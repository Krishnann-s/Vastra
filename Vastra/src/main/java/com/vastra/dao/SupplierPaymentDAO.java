package com.vastra.dao;

import com.vastra.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class SupplierPaymentDAO {

    /**
     * Payments within a date range as a raw ResultSet (joined with supplier name)
     * - used by ExcelReportUtil for the yearly archive export.
     */
    public static ResultSet getPaymentsInRange(String startDate, String endDate) throws SQLException {
        String sql = """
            SELECT sp.*, s.name as supplier_name
            FROM supplier_payments sp
            JOIN suppliers s ON sp.supplier_id = s.id
            WHERE DATE(sp.payment_date) BETWEEN ? AND ?
            ORDER BY sp.payment_date DESC
        """;
        Connection c = DBUtil.getConnection();
        PreparedStatement ps = c.prepareStatement(sql);
        ps.setString(1, startDate);
        ps.setString(2, endDate);
        return ps.executeQuery();
    }

    public static String recordPayment(String supplierId, String purchaseId, int amountCents,
                                        String paymentDate, String paymentMode, String notes) throws SQLException {
        String id = UUID.randomUUID().toString();
        String sql = """
            INSERT INTO supplier_payments(id, supplier_id, purchase_id, amount_cents, payment_date, payment_mode, notes, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, datetime('now'))
        """;
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, supplierId);
            ps.setString(3, purchaseId); // may be null - general payment
            ps.setInt(4, amountCents);
            ps.setString(5, paymentDate);
            ps.setString(6, paymentMode);
            ps.setString(7, notes);
            ps.executeUpdate();
        }
        return id;
    }
}