package com.vastra.dao;

import com.vastra.model.Purchase;
import com.vastra.model.PurchaseItem;
import com.vastra.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PurchaseDAO {

    /**
     * Records a full purchase: header row, all line items, increments stock
     * for each product, and (if amountPaidNowCents > 0) also records an
     * immediate supplier payment tied to this purchase.
     *
     * Since you told me purchases are mostly on credit, amountPaidNowCents
     * will usually be 0 and the whole total becomes due immediately.
     */
    public static String recordPurchase(String supplierId, String invoiceNumber, String invoiceDate,
                                         List<PurchaseItem> items, String dueDate, String notes,
                                         int amountPaidNowCents, String paymentModeIfPaidNow) throws SQLException {
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            conn.setAutoCommit(false);

            int totalCents = 0;
            for (PurchaseItem item : items) {
                totalCents += item.getLineTotalCents();
            }

            String purchaseId = UUID.randomUUID().toString();
            String purchaseSql = """
                INSERT INTO purchases(id, supplier_id, invoice_number, invoice_date, total_amount_cents, due_date, notes, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, datetime('now'))
            """;
            try (PreparedStatement ps = conn.prepareStatement(purchaseSql)) {
                ps.setString(1, purchaseId);
                ps.setString(2, supplierId);
                ps.setString(3, invoiceNumber);
                ps.setString(4, invoiceDate);
                ps.setInt(5, totalCents);
                ps.setString(6, dueDate);
                ps.setString(7, notes);
                ps.executeUpdate();
            }

            String itemSql = """
                INSERT INTO purchase_items(id, purchase_id, product_id, product_name, qty, cost_price_cents, line_total_cents)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
            try (PreparedStatement ps = conn.prepareStatement(itemSql)) {
                for (PurchaseItem item : items) {
                    ps.setString(1, UUID.randomUUID().toString());
                    ps.setString(2, purchaseId);
                    ps.setString(3, item.getProductId());
                    ps.setString(4, item.getProductName());
                    ps.setInt(5, item.getQty());
                    ps.setInt(6, item.getCostPriceCents());
                    ps.setInt(7, item.getLineTotalCents());
                    ps.executeUpdate();

                    // increment stock for this product
                    String stockSql = "UPDATE products SET stock = stock + ?, purchase_price_cents = ?, updated_at = datetime('now') WHERE id = ?";
                    try (PreparedStatement stockPs = conn.prepareStatement(stockSql)) {
                        stockPs.setInt(1, item.getQty());
                        stockPs.setInt(2, item.getCostPriceCents());
                        stockPs.setString(3, item.getProductId());
                        stockPs.executeUpdate();
                    }
                }
            }

            if (amountPaidNowCents > 0) {
                String paymentSql = """
                    INSERT INTO supplier_payments(id, supplier_id, purchase_id, amount_cents, payment_date, payment_mode, notes, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, datetime('now'))
                """;
                try (PreparedStatement ps = conn.prepareStatement(paymentSql)) {
                    ps.setString(1, UUID.randomUUID().toString());
                    ps.setString(2, supplierId);
                    ps.setString(3, purchaseId);
                    ps.setInt(4, amountPaidNowCents);
                    ps.setString(5, invoiceDate);
                    ps.setString(6, paymentModeIfPaidNow != null ? paymentModeIfPaidNow : "CASH");
                    ps.setString(7, "Paid at time of purchase");
                    ps.executeUpdate();
                }
            }

            conn.commit();
            return purchaseId;

        } catch (Exception e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            }
            throw new SQLException("Purchase entry failed: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    /**
     * Purchases within a date range as a raw ResultSet (joined with supplier name)
     * - used by ExcelReportUtil for the yearly archive export.
     */
    public static ResultSet getPurchasesInRangeResultSet(String startDate, String endDate) throws SQLException {
        String sql = """
            SELECT p.*, s.name as supplier_name
            FROM purchases p
            JOIN suppliers s ON p.supplier_id = s.id
            WHERE DATE(p.invoice_date) BETWEEN ? AND ?
            ORDER BY p.invoice_date DESC
        """;
        Connection c = DBUtil.getConnection();
        PreparedStatement ps = c.prepareStatement(sql);
        ps.setString(1, startDate);
        ps.setString(2, endDate);
        return ps.executeQuery();
    }

    public static List<Purchase> getPurchasesForSupplier(String supplierId) throws SQLException {
        String sql = "SELECT * FROM purchases WHERE supplier_id = ? ORDER BY invoice_date DESC";
        List<Purchase> list = new ArrayList<>();
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, supplierId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Purchase p = new Purchase();
                p.setId(rs.getString("id"));
                p.setSupplierId(rs.getString("supplier_id"));
                p.setInvoiceNumber(rs.getString("invoice_number"));
                p.setInvoiceDate(rs.getString("invoice_date"));
                p.setTotalAmountCents(rs.getInt("total_amount_cents"));
                p.setDueDate(rs.getString("due_date"));
                p.setNotes(rs.getString("notes"));
                p.setCreatedAt(rs.getString("created_at"));
                list.add(p);
            }
        }
        return list;
    }

    /**
     * Purchases whose due_date has passed. NOTE: this checks the invoice due
     * date against today, but the balance shown is the supplier's overall
     * running due (not per-invoice allocation) - good enough for "who do I
     * need to pay soon" without building full FIFO payment allocation.
     */
    public static List<Purchase> getOverduePurchases() throws SQLException {
        String sql = """
            SELECT * FROM purchases
            WHERE due_date IS NOT NULL AND due_date != '' AND date(due_date) < date('now')
            ORDER BY due_date ASC
        """;
        List<Purchase> list = new ArrayList<>();
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Purchase p = new Purchase();
                p.setId(rs.getString("id"));
                p.setSupplierId(rs.getString("supplier_id"));
                p.setInvoiceNumber(rs.getString("invoice_number"));
                p.setInvoiceDate(rs.getString("invoice_date"));
                p.setTotalAmountCents(rs.getInt("total_amount_cents"));
                p.setDueDate(rs.getString("due_date"));
                p.setNotes(rs.getString("notes"));
                p.setCreatedAt(rs.getString("created_at"));
                list.add(p);
            }
        }
        return list;
    }
}