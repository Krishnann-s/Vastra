package com.vastra.dao;

import com.vastra.model.Product;
import com.vastra.util.DBUtil;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ProductDAO {

    public static void insertProduct(String prod_id, String prod_name, String variant,
                                     int mrp, int sellPrice, int gst, int stock) throws Exception {

        String sql = """
            INSERT INTO products(id, name, variant, mrp_cents, sell_price_cents, gst_percent, stock, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, datetime('now'))
        """;

        try (Connection c = DBUtil.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, prod_id);
            ps.setString(2, prod_name);
            ps.setString(3, variant);
            ps.setInt(4, mrp);
            ps.setInt(5, sellPrice);
            ps.setInt(6, gst);
            ps.setInt(7, stock);
            ps.executeUpdate();
        }
    }

    public static Product findById(String id) throws SQLException {
        String sql = "SELECT * FROM products WHERE id = ?";
        try (
            Connection c = DBUtil.getConnection();
            PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, id);
                ResultSet rs = ps.executeQuery();
                if(rs.next()) {
                    return extractProduct(rs);
                }
        }
        return null;
    }

    public static List<Product> searchByName(String name) throws SQLException {
        String sql = "SELECT * FROM products WHERE name LIKE ? ORDER BY name";
        List<Product> products = new ArrayList<>();
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, "%" + name + "%");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                products.add(extractProduct(rs));
            }
        }
        return products;
    }

    public static void updateStock(String productId, int newStock) throws SQLException {
        String sql = "UPDATE products SET stock = ? WHERE id = ?";
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, newStock);
            ps.setString(2, productId);
            ps.executeUpdate();
        }
    }

    public static void decrementStock(String productId, int quantity) throws SQLException {
        String sql = "UPDATE products SET stock = stock - ? WHERE id = ? AND stock >= ?";
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, quantity);
            ps.setString(2, productId);
            ps.setInt(3, quantity);
            int updated = ps.executeUpdate();
            if (updated == 0) {
                throw new SQLException("Insufficient stock for product: " + productId);
            }
        }
    }

    public static List<Product> getLowStockProducts() throws SQLException {
        String sql = "SELECT * FROM products WHERE stock <= reorder_threshold ORDER BY stock";
        List<Product> products = new ArrayList<>();
        try (Connection c = DBUtil.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                products.add(extractProduct(rs));
            }
        }
        return products;
    }

    public static List<Product> getAllProducts() throws SQLException {
        String sql = "SELECT * FROM products ORDER BY name";
        List<Product> products = new ArrayList<>();
        try (Connection c = DBUtil.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                products.add(extractProduct(rs));
            }
        }
        return products;
    }

    private static Product extractProduct(ResultSet rs) throws SQLException {
        Product p = new Product();
        p.setId(rs.getString("id"));
        p.setName(rs.getString("name"));
        p.setVariant(rs.getString("variant"));
        p.setMrpCents(rs.getInt("mrp_cents"));
        p.setSellPriceCents(rs.getInt("sell_price_cents"));
        p.setGstPercent(rs.getInt("gst_percent"));
        p.setStock(rs.getInt("stock"));
        p.setReorderThreshold(rs.getInt("reorder_threshold"));
        p.setCreatedAt(rs.getString("created_at"));
        return p;
    }
}
