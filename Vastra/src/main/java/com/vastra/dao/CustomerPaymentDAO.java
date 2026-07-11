package com.vastra.dao;

import com.vastra.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

public class CustomerPaymentDAO {

    public static String recordPayment(String customerId, String saleId, int amountCents,
                                        String paymentDate, String paymentMode, String notes) throws SQLException {
        String id = UUID.randomUUID().toString();
        String sql = """
            INSERT INTO customer_payments(id, customer_id, sale_id, amount_cents, payment_date, payment_mode, notes, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, datetime('now'))
        """;
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, customerId);
            ps.setString(3, saleId); // may be null - general payment against running balance
            ps.setInt(4, amountCents);
            ps.setString(5, paymentDate);
            ps.setString(6, paymentMode);
            ps.setString(7, notes);
            ps.executeUpdate();
        }
        return id;
    }
}