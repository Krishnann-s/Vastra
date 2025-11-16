package com.vastra.dao;

import com.vastra.model.CartItem;
import com.vastra.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

public class SalesDAO {
    public static String completeSale(List<CartItem> items, String customerId,
                                      int discountCents, String paymentMode) throws SQLException {
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            conn.setAutoCommit(false);

            // Calculate totals
            int subtotal = 0;
            int tax = 0;
            for (CartItem item : items) {
                int lineTotal = item.getProduct().getSellPriceCents() * item.getQuantity();
                subtotal += lineTotal;
                tax += (int) (item.getTaxAmount() * 100);
            }

            int total = subtotal - discountCents;

            // Insert sale record
            String saleId = UUID.randomUUID().toString();
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

            String saleSql = """
                INSERT INTO sales(id, customer_id, ts, subtotal_cents, tax_cents, 
                                  discount_cents, total_cents, payment_mode, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'COMPLETED')
            """;

            try (PreparedStatement ps = conn.prepareStatement(saleSql)) {
                ps.setString(1, saleId);
                ps.setString(2, customerId);
                ps.setString(3, timestamp);
                ps.setInt(4, subtotal);
                ps.setInt(5, tax);
                ps.setInt(6, discountCents);
                ps.setInt(7, total);
                ps.setString(8, paymentMode);
                ps.executeUpdate();
            }

            // Insert sale items and update stock
            String itemSql = """
                INSERT INTO sale_items(id, sale_id, product_id, qty, sell_price_cents, 
                                       tax_percent, line_total_cents)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

            try (PreparedStatement ps = conn.prepareStatement(itemSql)) {
                for (CartItem item : items) {
                    ps.setString(1, UUID.randomUUID().toString());
                    ps.setString(2, saleId);
                    ps.setString(3, item.getProduct().getId());
                    ps.setInt(4, item.getQuantity());
                    ps.setInt(5, item.getProduct().getSellPriceCents());
                    ps.setInt(6, item.getProduct().getGstPercent());
                    ps.setInt(7, (int) (item.getLineTotal() * 100));
                    ps.executeUpdate();

                    // Update stock
                    ProductDAO.decrementStock(item.getProduct().getId(), item.getQuantity());
                }
            }

            // Award loyalty points (10 points per 1000 rupees)
            if (customerId != null && !customerId.isEmpty()) {
                int pointsEarned = (total / 100) / 100; // total in rupees / 100
                if (pointsEarned > 0) {
                    CustomerDAO.addPoints(customerId, pointsEarned);
                }
            }

            conn.commit();
            return saleId;

        } catch (Exception e) {
            if (conn != null) {
                conn.rollback();
            }
            throw new SQLException("Sale transaction failed", e);
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    public static ResultSet getDailySalesReport(String date) throws SQLException {
        String sql = """
            SELECT s.*, c.name as customer_name, c.phone as customer_phone
            FROM sales s
            LEFT JOIN customers c ON s.customer_id = c.id
            WHERE DATE(s.ts) = ?
            ORDER BY s.ts DESC
        """;

        Connection c = DBUtil.getConnection();
        PreparedStatement ps = c.prepareStatement(sql);
        ps.setString(1, date);
        return ps.executeQuery();
    }

    public static ResultSet getSalesInRange(String startDate, String endDate) throws SQLException {
        String sql = """
            SELECT DATE(ts) as sale_date, 
                   COUNT(*) as num_sales,
                   SUM(total_cents) as total_revenue_cents,
                   SUM(tax_cents) as total_tax_cents,
                   SUM(discount_cents) as total_discount_cents
            FROM sales
            WHERE DATE(ts) BETWEEN ? AND ?
            GROUP BY DATE(ts)
            ORDER BY sale_date DESC
        """;

        Connection c = DBUtil.getConnection();
        PreparedStatement ps = c.prepareStatement(sql);
        ps.setString(1, startDate);
        ps.setString(2, endDate);
        return ps.executeQuery();
    }
}
