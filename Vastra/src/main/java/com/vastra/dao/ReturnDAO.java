package com.vastra.dao;

import com.vastra.model.ReturnRecord;
import com.vastra.util.ActivityLogUtil;
import com.vastra.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ReturnDAO {

    public static int getReturnedQtyForSaleItem(String saleItemId) throws SQLException {
        String sql = "SELECT COALESCE(SUM(qty),0) FROM return_items WHERE sale_item_id = ?";
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, saleItemId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /**
     * Records a return: one `returns` header row, one `return_items` row per
     * line, and (optionally) restocks the returned quantity - all in a
     * single transaction so stock can never drift out of sync with the
     * recorded return if something fails partway through.
     */
    public static int processReturn(String saleId, String customerId, String reason,
                                     List<ReturnLine> lines, boolean restock,
                                     String processedBy, String notes) throws SQLException {
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            conn.setAutoCommit(false);

            int totalRefundCents = 0;
            for (ReturnLine l : lines) totalRefundCents += l.getRefundAmountCents();

            String returnId = UUID.randomUUID().toString();
            String returnSql = """
                INSERT INTO returns(id, sale_id, customer_id, return_date, reason, refund_amount_cents, status, processed_by, notes)
                VALUES (?, ?, ?, date('now'), ?, ?, 'COMPLETED', ?, ?)
            """;
            try (PreparedStatement ps = conn.prepareStatement(returnSql)) {
                ps.setString(1, returnId);
                ps.setString(2, saleId);
                ps.setString(3, customerId);
                ps.setString(4, reason);
                ps.setInt(5, totalRefundCents);
                ps.setString(6, processedBy != null ? processedBy : "Admin");
                ps.setString(7, notes);
                ps.executeUpdate();
            }

            String itemSql = """
                INSERT INTO return_items(id, return_id, sale_item_id, product_id, qty, refund_amount_cents)
                VALUES (?, ?, ?, ?, ?, ?)
            """;
            try (PreparedStatement ps = conn.prepareStatement(itemSql)) {
                for (ReturnLine l : lines) {
                    ps.setString(1, UUID.randomUUID().toString());
                    ps.setString(2, returnId);
                    ps.setString(3, l.getSaleItemId());
                    ps.setString(4, l.getProductId());
                    ps.setInt(5, l.getQty());
                    ps.setInt(6, l.getRefundAmountCents());
                    ps.executeUpdate();
                }
            }

            if (restock) {
                for (ReturnLine l : lines) {
                    String stockSql = "UPDATE products SET stock = stock + ?, updated_at = datetime('now') WHERE id = ?";
                    try (PreparedStatement ps = conn.prepareStatement(stockSql)) {
                        ps.setInt(1, l.getQty());
                        ps.setString(2, l.getProductId());
                        ps.executeUpdate();
                    }
                }
            }

            conn.commit();

            ActivityLogUtil.log(processedBy, "RETURN", "SALE", saleId,
                    String.format("Return processed: ₹%.2f refunded across %d line(s)%s",
                            totalRefundCents / 100.0, lines.size(), restock ? " (restocked)" : " (not restocked)"));

            return totalRefundCents;

        } catch (Exception e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            }
            throw new SQLException("Return processing failed: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    public static List<ReturnRecord> getRecentReturns(int limit) throws SQLException {
        String sql = """
            SELECT r.*, s.invoice_number, c.name as customer_name
            FROM returns r
            JOIN sales s ON r.sale_id = s.id
            LEFT JOIN customers c ON r.customer_id = c.id
            ORDER BY r.return_date DESC
            LIMIT ?
        """;
        List<ReturnRecord> list = new ArrayList<>();
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                ReturnRecord r = new ReturnRecord();
                r.setId(rs.getString("id"));
                r.setSaleId(rs.getString("sale_id"));
                r.setInvoiceNumber(rs.getString("invoice_number"));
                r.setCustomerId(rs.getString("customer_id"));
                r.setCustomerName(rs.getString("customer_name"));
                r.setReturnDate(rs.getString("return_date"));
                r.setReason(rs.getString("reason"));
                r.setRefundAmountCents(rs.getInt("refund_amount_cents"));
                r.setStatus(rs.getString("status"));
                r.setProcessedBy(rs.getString("processed_by"));
                r.setNotes(rs.getString("notes"));
                list.add(r);
            }
        }
        return list;
    }

    /** Simple DTO to pass "what's being returned" from the UI into processReturn(). */
    public static class ReturnLine {
        private final String saleItemId;
        private final String productId;
        private final int qty;
        private final int refundAmountCents;

        public ReturnLine(String saleItemId, String productId, int qty, int refundAmountCents) {
            this.saleItemId = saleItemId;
            this.productId = productId;
            this.qty = qty;
            this.refundAmountCents = refundAmountCents;
        }

        public String getSaleItemId() { return saleItemId; }
        public String getProductId() { return productId; }
        public int getQty() { return qty; }
        public int getRefundAmountCents() { return refundAmountCents; }
    }
}