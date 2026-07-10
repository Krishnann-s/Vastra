package com.vastra.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Atomic, gap-free-per-transaction sequence counters backed by the
 * store_settings table (key = counter name, value = current integer as text).
 */
public class SequenceUtil {

    /** Standalone convenience version - opens its own connection/transaction. */
    public static synchronized long nextValue(String key, long startValue) throws SQLException {
        try (Connection c = DBUtil.getConnection()) {
            return nextValueInTransaction(c, key, startValue);
        }
    }

    /**
     * Use this variant when the increment needs to be part of a larger
     * transaction (e.g. bill numbers inside SalesDAO.completeSale, so a
     * rolled-back sale doesn't burn a number).
     */
    public static long nextValueInTransaction(Connection conn, String key, long startValue) throws SQLException {
        try (PreparedStatement ins = conn.prepareStatement(
                "INSERT OR IGNORE INTO store_settings(key, value, updated_at) VALUES (?, ?, datetime('now'))")) {
            ins.setString(1, key);
            ins.setString(2, String.valueOf(startValue - 1));
            ins.executeUpdate();
        }
        try (PreparedStatement upd = conn.prepareStatement(
                "UPDATE store_settings SET value = CAST(value AS INTEGER) + 1, updated_at = datetime('now') WHERE key = ? RETURNING value")) {
            upd.setString(1, key);
            ResultSet rs = upd.executeQuery();
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
        throw new SQLException("Could not generate sequence value for key: " + key);
    }
}