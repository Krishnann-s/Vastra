package com.vastra.util;

import com.vastra.model.ActivityLogEntry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Every deletion (and other notable action) in the app should call log()
 * here. Since deletions in this app are always soft-deletes (is_active = 0,
 * the row itself is never physically removed), a full database backup taken
 * at any point will always reflect deletions correctly, and this log lets
 * you see exactly what was deleted and when - even between backups.
 */
public class ActivityLogUtil {

    public static void log(String userName, String action, String entityType, String entityId, String details) {
        String sql = """
            INSERT INTO activity_log(id, user_name, action, entity_type, entity_id, details, timestamp)
            VALUES (?, ?, ?, ?, ?, ?, datetime('now'))
        """;
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, userName != null ? userName : "Admin");
            ps.setString(3, action);
            ps.setString(4, entityType);
            ps.setString(5, entityId);
            ps.setString(6, details);
            ps.executeUpdate();
        } catch (Exception e) {
            // Never let a logging failure break the actual delete/action that triggered it
            e.printStackTrace();
        }
    }

    public static List<ActivityLogEntry> getRecentActivity(int limit) throws SQLException {
        String sql = "SELECT * FROM activity_log ORDER BY timestamp DESC LIMIT ?";
        List<ActivityLogEntry> list = new ArrayList<>();
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(extract(rs));
            }
        }
        return list;
    }

    public static List<ActivityLogEntry> getRecentDeletions(int limit) throws SQLException {
        String sql = "SELECT * FROM activity_log WHERE action = 'DELETE' ORDER BY timestamp DESC LIMIT ?";
        List<ActivityLogEntry> list = new ArrayList<>();
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(extract(rs));
            }
        }
        return list;
    }

    private static ActivityLogEntry extract(ResultSet rs) throws SQLException {
        ActivityLogEntry e = new ActivityLogEntry();
        e.setId(rs.getString("id"));
        e.setUserName(rs.getString("user_name"));
        e.setAction(rs.getString("action"));
        e.setEntityType(rs.getString("entity_type"));
        e.setEntityId(rs.getString("entity_id"));
        e.setDetails(rs.getString("details"));
        e.setTimestamp(rs.getString("timestamp"));
        return e;
    }
}