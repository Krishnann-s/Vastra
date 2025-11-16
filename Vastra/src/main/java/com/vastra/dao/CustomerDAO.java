package com.vastra.dao;

import com.vastra.model.Customer;
import com.vastra.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class CustomerDAO {
    public static Customer findByPhone(String phone) throws SQLException {
        String sql = "SELECT * FROM customers WHERE phone = ?";
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, phone);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return extractCustomer(rs);
            }
        }
        return null;
    }

    public static Customer createCustomer(String name, String phone, String email) throws SQLException {
        String sql = """
            INSERT INTO customers(id, name, phone, email, points, created_at)
            VALUES (?, ?, ?, ?, 0, datetime('now'))
        """;

        String id = UUID.randomUUID().toString();
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, name);
            ps.setString(3, phone);
            ps.setString(4, email);
            ps.executeUpdate();
        }

        return findByPhone(phone);
    }

    public static void addPoints(String customerId, int points) throws SQLException {
        String sql = "UPDATE customers SET points = points + ? WHERE id = ?";
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, points);
            ps.setString(2, customerId);
            ps.executeUpdate();
        }
    }

    public static void redeemPoints(String customerId, int points) throws SQLException {
        String sql = "UPDATE customers SET points = points - ? WHERE id = ? AND points >= ?";
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, points);
            ps.setString(2, customerId);
            ps.setInt(3, points);
            int updated = ps.executeUpdate();
            if (updated == 0) {
                throw new SQLException("Insufficient points for customer: " + customerId);
            }
        }
    }

    public static Customer findById(String id) throws SQLException {
        String sql = "SELECT * FROM customers WHERE id = ?";
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return extractCustomer(rs);
            }
        }
        return null;
    }

    private static Customer extractCustomer(ResultSet rs) throws SQLException {
        Customer c = new Customer();
        c.setId(rs.getString("id"));
        c.setName(rs.getString("name"));
        c.setPhone(rs.getString("phone"));
        c.setEmail(rs.getString("email"));
        c.setPoints(rs.getInt("points"));
        c.setCreatedAt(rs.getString("created_at"));
        return c;
    }
}
