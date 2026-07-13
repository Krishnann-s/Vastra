package com.vastra.util;

import com.vastra.dao.StoreSettingsDAO;

/**
 * Turns the manual "remember to back up" reminder into a real automatic backup.
 *
 * Since this is a desktop app that isn't running 24/7 like a server, "once a day"
 * is implemented as two checkpoints, both gated by the "auto_backup_enabled"
 * store setting (defaults on) and both skipped if a backup was already taken
 * today (see BackupUtil.wasBackedUpToday()):
 *
 *   1. On startup  - runs on a background thread shortly after login, so it
 *      doesn't block the UI and doesn't slow down opening the app.
 *   2. On shutdown - runs synchronously just before the window actually closes,
 *      as a safety net for a shop that's left running all day and closed once
 *      at the end of the day. VACUUM INTO on a shop-sized SQLite file is fast
 *      (well under a second in practice), so this doesn't meaningfully delay closing.
 *
 * Either one alone would miss cases (crash before close; app left open for days);
 * together they cover the realistic ways this app actually gets used.
 */
public class AutoBackupUtil {

    private static final String SETTING_KEY = "auto_backup_enabled";

    public static boolean isEnabled() {
        try {
            return "1".equals(StoreSettingsDAO.get(SETTING_KEY, "1"));
        } catch (Exception e) {
            e.printStackTrace();
            return true; // fail safe - if we can't read the setting, prefer to still back up
        }
    }

    private static boolean isDue() {
        return isEnabled() && !BackupUtil.wasBackedUpToday();
    }

    /** Call once, shortly after startup. Silent - failures are logged, not shown to the user. */
    public static void runOnStartupIfDue() {
        if (!isDue()) return;
        Thread t = new Thread(() -> {
            try {
                BackupUtil.createBackup();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "auto-backup-startup");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Call from the window's close handler, BEFORE letting the close proceed.
     * Runs synchronously (VACUUM INTO is fast on a shop-sized database), so the
     * backup is guaranteed to exist before the process exits.
     */
    public static void runOnShutdownIfDue() {
        if (!isDue()) return;
        try {
            BackupUtil.createBackup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}