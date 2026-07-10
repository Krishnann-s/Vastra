package com.vastra.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * Full-database backup and restore.
 *
 * Uses SQLite's "VACUUM INTO" command, which produces a single, fully
 * consistent snapshot file even while the app is running in WAL mode - much
 * safer than copying the raw .db/-wal/-shm files by hand.
 *
 * Since every "delete" in this app is a soft-delete (is_active = 0, nothing
 * is ever physically removed - see ProductDAO/CustomerDAO/SupplierDAO), a
 * backup taken at any moment already reflects your deletions correctly. The
 * activity_log table (see ActivityLogUtil) is what lets you see WHEN
 * something was deleted, in case you need to explain a gap between backups.
 */
public class BackupUtil {

    private static final String BACKUP_DIR = "backups";
    private static final String BACKUP_PREFIX = "vastra_backup_";
    private static final String BACKUP_SUFFIX = ".db";
    private static final int MAX_BACKUPS_TO_KEEP = 60; // ~2 months if run daily

    /**
     * Creates a new timestamped backup file, updates the "last backup" record,
     * and prunes old backups beyond MAX_BACKUPS_TO_KEEP. Safe to call while
     * the app is in normal use.
     */
    public static File createBackup() throws Exception {
        File dir = new File(BACKUP_DIR);
        if (!dir.exists()) dir.mkdirs();

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File backupFile = new File(dir, BACKUP_PREFIX + timestamp + BACKUP_SUFFIX);

        // Escape single quotes for the SQL literal path
        String escapedPath = backupFile.getAbsolutePath().replace("'", "''");

        try (Connection c = DBUtil.getConnection();
             Statement s = c.createStatement()) {
            s.execute("VACUUM INTO '" + escapedPath + "'");
        }

        recordLastBackupTimestamp();
        pruneOldBackups();

        return backupFile;
    }

    private static void recordLastBackupTimestamp() throws SQLException {
        String sql = """
            INSERT INTO store_settings(key, value, updated_at)
            VALUES ('last_backup_at', datetime('now'), datetime('now'))
            ON CONFLICT(key) DO UPDATE SET value = datetime('now'), updated_at = datetime('now')
        """;
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.executeUpdate();
        }
    }

    /** Returns the timestamp (as stored, in SQLite's datetime('now') format) of the last backup, or null if none yet. */
    public static String getLastBackupTimestamp() throws SQLException {
        String sql = "SELECT value FROM store_settings WHERE key = 'last_backup_at'";
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String value = rs.getString(1);
                return (value == null || value.isBlank()) ? null : value;
            }
        }
        return null;
    }

    /** All backup files, newest first. */
    public static List<File> listBackups() {
        File dir = new File(BACKUP_DIR);
        List<File> result = new ArrayList<>();
        File[] files = dir.listFiles((d, name) -> name.startsWith(BACKUP_PREFIX) && name.endsWith(BACKUP_SUFFIX));
        if (files != null) {
            result.addAll(List.of(files));
            result.sort(Comparator.comparing(File::getName).reversed());
        }
        return result;
    }

    private static void pruneOldBackups() {
        List<File> backups = listBackups(); // newest first
        for (int i = MAX_BACKUPS_TO_KEEP; i < backups.size(); i++) {
            backups.get(i).delete();
        }
    }

    /**
     * Restores the database from a chosen backup file. For safety, this
     * first takes a fresh backup of the CURRENT database (so you can always
     * undo a restore), then replaces db/vastra.db with the chosen backup and
     * clears any leftover WAL/journal files so the restored copy loads clean.
     *
     * IMPORTANT: Restart the application after calling this so every screen
     * reloads from the restored data.
     */
    public static void restoreBackup(File backupFile) throws Exception {
        if (!backupFile.exists()) {
            throw new IOException("Backup file not found: " + backupFile.getAbsolutePath());
        }

        // Safety net: snapshot the current (about-to-be-overwritten) database first
        createBackup();

        Path target = Paths.get(DBUtil.DB_FILE_PATH);
        Files.createDirectories(target.getParent() != null ? target.getParent() : Paths.get("."));

        Files.copy(backupFile.toPath(), target, StandardCopyOption.REPLACE_EXISTING);

        // Remove any leftover WAL/shared-memory files from the previous database
        // so the restored file is read cleanly on next connection.
        deleteIfExists(DBUtil.DB_FILE_PATH + "-wal");
        deleteIfExists(DBUtil.DB_FILE_PATH + "-shm");
    }

    private static void deleteIfExists(String path) {
        File f = new File(path);
        if (f.exists()) f.delete();
    }

    /** True if a backup has already been taken today (useful for an end-of-day reminder). */
    public static boolean wasBackedUpToday() {
        try {
            String last = getLastBackupTimestamp();
            if (last == null || last.isBlank()) return false;
            String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
            return last.startsWith(today);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}