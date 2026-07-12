package com.vastra.dao;

import com.vastra.model.User;
import com.vastra.util.ActivityLogUtil;
import com.vastra.util.DBUtil;
import com.vastra.util.PasswordUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class UserDAO {

    public static User findByUsername(String username) throws SQLException {
        String sql = "SELECT * FROM users WHERE username = ? AND is_active = 1";
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return extract(rs);
        }
        return null;
    }

    /** Verifies an ADMIN login. Returns the User on success, null on bad username/password/role. */
    public static User verifyAdminLogin(String username, String password) throws SQLException {
        String sql = "SELECT * FROM users WHERE username = ? AND role = 'ADMIN' AND is_active = 1";
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String hash = rs.getString("password_hash");
                String salt = rs.getString("password_salt");
                if (PasswordUtil.verify(password, hash, salt)) {
                    return extract(rs);
                }
            }
        }
        return null;
    }

    /** Verifies a CASHIER login with a 4-digit PIN. Returns the User on success, null otherwise. */
    public static User verifyCashierLogin(String username, String pin) throws SQLException {
        String sql = "SELECT * FROM users WHERE username = ? AND role = 'CASHIER' AND is_active = 1";
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String hash = rs.getString("pin_hash");
                String salt = rs.getString("pin_salt");
                if (PasswordUtil.verify(pin, hash, salt)) {
                    return extract(rs);
                }
            }
        }
        return null;
    }

    /** Admin action: creates a new cashier with a 4-digit PIN. */
    public static String createCashier(String username, String displayName, String pin) throws SQLException {
        if (pin == null || !pin.matches("\\d{4}")) {
            throw new SQLException("PIN must be exactly 4 digits");
        }
        String[] hashAndSalt = PasswordUtil.hash(pin);
        String id = UUID.randomUUID().toString();
        String sql = """
            INSERT INTO users(id, username, role, pin_hash, pin_salt, display_name, is_active, created_at)
            VALUES (?, ?, 'CASHIER', ?, ?, ?, 1, datetime('now'))
        """;
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, username);
            ps.setString(3, hashAndSalt[0]);
            ps.setString(4, hashAndSalt[1]);
            ps.setString(5, displayName != null && !displayName.isBlank() ? displayName : username);
            ps.executeUpdate();
        }
        ActivityLogUtil.log(null, "CREATE", "USER", id, "Cashier '" + username + "' added");
        return id;
    }

    public static void resetCashierPin(String userId, String newPin) throws SQLException {
        if (newPin == null || !newPin.matches("\\d{4}")) {
            throw new SQLException("PIN must be exactly 4 digits");
        }
        String[] hashAndSalt = PasswordUtil.hash(newPin);
        String sql = "UPDATE users SET pin_hash = ?, pin_salt = ? WHERE id = ?";
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, hashAndSalt[0]);
            ps.setString(2, hashAndSalt[1]);
            ps.setString(3, userId);
            ps.executeUpdate();
        }
    }
    public static User findById(String id) throws SQLException {
        String sql = "SELECT * FROM users WHERE id = ?";
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return extract(rs);
        }
        return null;
    }

    /** Admin action: rename a cashier / change their display name (not their PIN - use resetCashierPin for that). */
    public static void updateCashier(String userId, String username, String displayName) throws SQLException {
        String sql = "UPDATE users SET username = ?, display_name = ? WHERE id = ?";
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, displayName != null && !displayName.isBlank() ? displayName : username);
            ps.setString(3, userId);
            ps.executeUpdate();
        }
        ActivityLogUtil.log(null, "UPDATE", "USER", userId, "Cashier updated: " + username);
    }

    /** Lets the logged-in admin update their own username/display name. */
    public static void updateAdminProfile(String userId, String username, String displayName) throws SQLException {
        String sql = "UPDATE users SET username = ?, display_name = ? WHERE id = ?";
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, displayName != null && !displayName.isBlank() ? displayName : username);
            ps.setString(3, userId);
            ps.executeUpdate();
        }
        ActivityLogUtil.log(null, "UPDATE", "USER", userId, "Admin profile updated: " + username);
    }

    /** Lets the logged-in admin change their own password. */
    public static void changeAdminPassword(String userId, String newPassword) throws SQLException {
        if (newPassword == null || newPassword.length() < 4) {
            throw new SQLException("Password must be at least 4 characters");
        }
        String[] hashAndSalt = PasswordUtil.hash(newPassword);
        String sql = "UPDATE users SET password_hash = ?, password_salt = ? WHERE id = ?";
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, hashAndSalt[0]);
            ps.setString(2, hashAndSalt[1]);
            ps.setString(3, userId);
            ps.executeUpdate();
        }
    }

    public static List<User> getAllCashiers() throws SQLException {
        String sql = "SELECT * FROM users WHERE role = 'CASHIER' AND is_active = 1 ORDER BY username";
        List<User> list = new ArrayList<>();
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(extract(rs));
        }
        return list;
    }

    public static void deactivateUser(String userId) throws SQLException {
        String sql = "UPDATE users SET is_active = 0 WHERE id = ?";
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.executeUpdate();
        }
        ActivityLogUtil.log(null, "DELETE", "USER", userId, "User deactivated");
    }

    private static User extract(ResultSet rs) throws SQLException {
        User u = new User();
        u.setId(rs.getString("id"));
        u.setUsername(rs.getString("username"));
        u.setRole(rs.getString("role"));
        u.setDisplayName(rs.getString("display_name"));
        u.setActive(rs.getInt("is_active") == 1);
        u.setCreatedAt(rs.getString("created_at"));
        return u;
    }
}