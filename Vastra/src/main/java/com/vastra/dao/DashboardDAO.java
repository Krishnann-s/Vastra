package com.vastra.dao;

import com.vastra.util.DBUtil;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;

public class DashboardDAO {

    public static int getTodaysSalesCents() throws SQLException {
        return scalarInt("SELECT COALESCE(SUM(total_cents),0) FROM sales WHERE DATE(ts) = DATE('now')");
    }

    public static int getTodaysSalesCount() throws SQLException {
        return scalarInt("SELECT COUNT(*) FROM sales WHERE DATE(ts) = DATE('now')");
    }

    public static int getLowStockCount() throws SQLException {
        return scalarInt("SELECT COUNT(*) FROM products WHERE stock <= reorder_threshold AND is_active = 1");
    }

    public static int getOutOfStockCount() throws SQLException {
        return scalarInt("SELECT COUNT(*) FROM products WHERE stock <= 0 AND is_active = 1");
    }

    /** Total currently owed to all active suppliers: opening + purchases - payments. */
    public static int getTotalSupplierDueCents() throws SQLException {
        String sql = """
            SELECT COALESCE((
                SELECT SUM(opening_balance_cents) FROM suppliers WHERE is_active = 1
            ), 0)
            + COALESCE((
                SELECT SUM(p.total_amount_cents) FROM purchases p
                JOIN suppliers s ON p.supplier_id = s.id WHERE s.is_active = 1
            ), 0)
            - COALESCE((
                SELECT SUM(sp.amount_cents) FROM supplier_payments sp
                JOIN suppliers s ON sp.supplier_id = s.id WHERE s.is_active = 1
            ), 0)
        """;
        return scalarInt(sql);
    }

    /** Total currently owed BY customers: all CREDIT sales minus payments received against them. */
    public static int getTotalCustomerDueCents() throws SQLException {
        String sql = """
            SELECT COALESCE((SELECT SUM(total_cents) FROM sales WHERE payment_mode = 'CREDIT'), 0)
                 - COALESCE((SELECT SUM(amount_cents) FROM customer_payments), 0)
        """;
        return scalarInt(sql);
    }

    private static int scalarInt(String sql) throws SQLException {
        try (Connection c = DBUtil.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }
}