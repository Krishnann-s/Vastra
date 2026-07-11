package com.vastra.dao;

import com.vastra.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

public class StoreSettingsDAO {

    public static String get(String key, String defaultValue) throws SQLException {
        String sql = "SELECT value FROM store_settings WHERE key = ?";
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, key);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String v = rs.getString(1);
                return (v != null && !v.isEmpty()) ? v : defaultValue;
            }
        }
        return defaultValue;
    }

    public static void set(String key, String value) throws SQLException {
        String sql = """
            INSERT INTO store_settings(key, value, updated_at) VALUES (?, ?, datetime('now'))
            ON CONFLICT(key) DO UPDATE SET value = excluded.value, updated_at = datetime('now')
        """;
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        }
    }

    public static Map<String, String> getAll() throws SQLException {
        Map<String, String> map = new LinkedHashMap<>();
        String sql = "SELECT key, value FROM store_settings ORDER BY key";
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) map.put(rs.getString(1), rs.getString(2));
        }
        return map;
    }
}