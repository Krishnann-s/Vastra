package com.vastra.dao;

import com.vastra.model.CartItem;
import com.vastra.model.HeldBill;
import com.vastra.model.Product;
import com.vastra.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Backs the "Hold Sale" / "Resume Held Bill" feature. A held bill stores just
 * the product IDs, quantities and per-item discounts (as a small hand-rolled
 * JSON array - product IDs are always UUIDs so no escaping is needed). When
 * resumed, each product is looked up fresh, so a resumed cart always reflects
 * today's prices and stock, not whatever they were when the sale was held.
 * If a product was deleted in the meantime, that line is silently skipped.
 */
public class HeldBillDAO {

    private static final Pattern ITEM_PATTERN =
            Pattern.compile("\\{\"productId\":\"([^\"]*)\",\"quantity\":(\\d+),\"discountCents\":(\\d+)\\}");

    public static String holdBill(String label, String customerId, String customerName,
                                   List<CartItem> items, int discountCents, String heldBy) throws SQLException {
        String id = UUID.randomUUID().toString();
        String sql = """
            INSERT INTO held_bills(id, label, customer_id, customer_name, cart_json, discount_cents, held_by, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, datetime('now'))
        """;
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, (label == null || label.isBlank()) ? "Held sale" : label.trim());
            ps.setString(3, customerId);
            ps.setString(4, customerName);
            ps.setString(5, toJson(items));
            ps.setInt(6, discountCents);
            ps.setString(7, heldBy);
            ps.executeUpdate();
        }
        return id;
    }

    /** All held bills, most recently held first, each fully resolved against current product data. */
    public static List<HeldBill> getAllHeldBills() throws SQLException {
        String sql = "SELECT * FROM held_bills ORDER BY created_at DESC";
        List<HeldBill> results = new ArrayList<>();
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.add(extract(rs));
            }
        }
        return results;
    }

    public static int countHeldBills() throws SQLException {
        String sql = "SELECT COUNT(*) FROM held_bills";
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    public static void deleteHeldBill(String id) throws SQLException {
        String sql = "DELETE FROM held_bills WHERE id = ?";
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        }
    }

    private static HeldBill extract(ResultSet rs) throws SQLException {
        HeldBill bill = new HeldBill();
        bill.setId(rs.getString("id"));
        bill.setLabel(rs.getString("label"));
        bill.setCustomerId(rs.getString("customer_id"));
        bill.setCustomerName(rs.getString("customer_name"));
        bill.setDiscountCents(rs.getInt("discount_cents"));
        bill.setHeldBy(rs.getString("held_by"));
        bill.setCreatedAt(rs.getString("created_at"));
        bill.setItems(fromJson(rs.getString("cart_json")));
        return bill;
    }

    private static String toJson(List<CartItem> items) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            CartItem item = items.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"productId\":\"").append(item.getProduct().getId())
              .append("\",\"quantity\":").append(item.getQuantity())
              .append(",\"discountCents\":").append(item.getDiscountCents())
              .append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private static List<CartItem> fromJson(String json) throws SQLException {
        List<CartItem> result = new ArrayList<>();
        if (json == null || json.isBlank()) return result;

        Matcher m = ITEM_PATTERN.matcher(json);
        while (m.find()) {
            String productId = m.group(1);
            int qty = Integer.parseInt(m.group(2));
            int discountCents = Integer.parseInt(m.group(3));

            Product product = ProductDAO.findById(productId);
            if (product == null) continue; // product was deleted since this bill was held - skip it

            CartItem item = new CartItem(product, qty);
            item.setDiscountCents(discountCents);
            result.add(item);
        }
        return result;
    }
}
