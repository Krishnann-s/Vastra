package com.vastra.dao;

import com.vastra.model.Customer;
import com.vastra.util.ActivityLogUtil;
import com.vastra.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
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

        ActivityLogUtil.log(null, "CREATE", "CUSTOMER", id, name + " added");
        return findByPhone(phone);
    }

    /**
     * Adds loyalty points. Accepts a fractional amount (e.g. 66.09) since
     * points are calculated as an exact 1% of the sale total, unrounded.
     */
    public static void addPoints(String customerId, double points) throws SQLException {
        String sql = "UPDATE customers SET points = points + ? WHERE id = ?";
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDouble(1, points);
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

    /** All active customers - used by search screens and the yearly Excel archive. */
    public static List<Customer> getAllCustomers() throws SQLException {
        String sql = "SELECT * FROM customers WHERE is_active = 1 ORDER BY name";
        List<Customer> list = new ArrayList<>();
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(extractCustomer(rs));
            }
        }
        return list;
    }

    /**
     * Soft-deletes a customer (is_active = 0). The row and their full sales
     * history stay in the database - nothing is physically removed - and the
     * action is recorded in activity_log.
     */
    public static void deactivateCustomer(String customerId) throws SQLException {
        Customer existing = findById(customerId);

        String sql = "UPDATE customers SET is_active = 0 WHERE id = ?";
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, customerId);
            ps.executeUpdate();
        }

        String name = existing != null ? existing.getName() : customerId;
        ActivityLogUtil.log(null, "DELETE", "CUSTOMER", customerId, name + " deleted (soft delete)");
    }

    private static Customer extractCustomer(ResultSet rs) throws SQLException {
        Customer c = new Customer();
        c.setId(rs.getString("id"));
        c.setName(rs.getString("name"));
        c.setPhone(rs.getString("phone"));

        String email = rs.getString("email");
        c.setEmail(email != null ? email : "");

        String address = rs.getString("address");
        c.setAddress(address != null ? address : "");

        String city = rs.getString("city");
        c.setCity(city != null ? city : "");

        String pincode = rs.getString("pincode");
        c.setPincode(pincode != null ? pincode : "");

        String birthday = rs.getString("birthday");
        c.setBirthday(birthday != null ? birthday : "");

        String anniversary = rs.getString("anniversary");
        c.setAnniversary(anniversary != null ? anniversary : "");

        c.setPoints(rs.getDouble("points"));
        c.setTotalPurchasesCents(rs.getInt("total_purchases_cents"));
        c.setVisitCount(rs.getInt("visit_count"));

        String tier = rs.getString("tier");
        c.setTier(tier != null ? tier : "BRONZE");

        String notes = rs.getString("notes");
        c.setNotes(notes != null ? notes : "");

        c.setActive(rs.getInt("is_active") == 1);
        c.setCreatedAt(rs.getString("created_at"));

        String lastVisit = rs.getString("last_visit");
        c.setLastVisit(lastVisit != null ? lastVisit : "");

        return c;
    }
}