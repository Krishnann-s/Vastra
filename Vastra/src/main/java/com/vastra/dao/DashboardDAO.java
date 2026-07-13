package com.vastra.dao;

import com.vastra.util.DBUtil;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

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

    /** Revenue for each of the last 7 days (including today), oldest first - for the dashboard trend chart. */
    public static List<com.vastra.model.DailySalesRow> getLast7DaysRevenue() throws SQLException {
        String sql = """
            SELECT DATE(ts) AS sale_date, COUNT(*) AS num_sales,
                   COALESCE(SUM(total_cents),0) AS total_revenue_cents,
                   COALESCE(SUM(tax_cents),0) AS total_tax_cents,
                   COALESCE(SUM(discount_cents),0) AS total_discount_cents
            FROM sales
            WHERE DATE(ts) >= DATE('now', '-6 days')
            GROUP BY DATE(ts)
        """;
        // Pre-fill all 7 days with zero so days with no sales still show a bar
        java.util.Map<String, com.vastra.model.DailySalesRow> byDate = new java.util.LinkedHashMap<>();
        for (int i = 6; i >= 0; i--) {
            String date = java.time.LocalDate.now().minusDays(i).toString();
            com.vastra.model.DailySalesRow row = new com.vastra.model.DailySalesRow();
            row.setDate(date);
            byDate.put(date, row);
        }
        try (Connection c = DBUtil.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                String date = rs.getString("sale_date");
                com.vastra.model.DailySalesRow row = byDate.get(date);
                if (row == null) continue; // outside the 7-day window somehow - ignore
                row.setNumSales(rs.getInt("num_sales"));
                row.setRevenueCents(rs.getInt("total_revenue_cents"));
                row.setTaxCents(rs.getInt("total_tax_cents"));
                row.setDiscountCents(rs.getInt("total_discount_cents"));
            }
        }
        return new java.util.ArrayList<>(byDate.values());
    }

    /** Best sellers by quantity sold over the last N days - for the dashboard "Top Products" widget. */
    public static List<com.vastra.model.TopProductRow> getTopSellingProducts(int limit, int days) throws SQLException {
        String sql = """
            SELECT si.product_name AS name, SUM(si.qty) AS qty_sold, SUM(si.line_total_cents) AS revenue_cents
            FROM sale_items si
            JOIN sales s ON s.id = si.sale_id
            WHERE s.ts >= datetime('now', ?)
            GROUP BY si.product_name
            ORDER BY qty_sold DESC
            LIMIT ?
        """;
        List<com.vastra.model.TopProductRow> results = new ArrayList<>();
        try (Connection c = DBUtil.getConnection();
             java.sql.PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, "-" + days + " days");
            ps.setInt(2, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                results.add(new com.vastra.model.TopProductRow(
                        rs.getString("name"), rs.getInt("qty_sold"), rs.getInt("revenue_cents")));
            }
        }
        return results;
    }

    private static int scalarInt(String sql) throws SQLException {
        try (Connection c = DBUtil.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }
}