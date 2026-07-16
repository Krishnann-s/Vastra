package com.vastra.dao;

import com.vastra.model.Product;
import com.vastra.util.ActivityLogUtil;
import com.vastra.util.DBUtil;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ProductDAO {

    /**
     * Insert a new product with auto-generated barcode if not provided
     */
    public static String insertProduct(String prod_name, String variant,
                                       int mrp, int sellPrice, int purchasePrice, int gst, int stock,
                                       int openingStock,
                                       String category, String brand, String sku,
                                       String hsnCode, int reorderThreshold, String description,
                                       String size, String color) throws Exception {
        String prod_id = UUID.randomUUID().toString();
        String barcode = sku != null && !sku.isEmpty() ? sku : generateBarcode();

        String sql = """
            INSERT INTO products(id, name, variant, category, brand, barcode, sku,
                               mrp_cents, sell_price_cents, purchase_price_cents, gst_percent, stock,
                               opening_stock, reorder_threshold, hsn_code, description, size, color,
                               is_active, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, datetime('now'))
        """;

        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, prod_id);
            ps.setString(2, prod_name);
            ps.setString(3, variant != null ? variant : "");
            ps.setString(4, category != null ? category : "");
            ps.setString(5, brand != null ? brand : "");
            ps.setString(6, barcode);
            ps.setString(7, sku != null ? sku : barcode);
            ps.setInt(8, mrp);
            ps.setInt(9, sellPrice);
            ps.setInt(10, purchasePrice);
            ps.setInt(11, gst);
            ps.setInt(12, stock);
            ps.setInt(13, openingStock);
            ps.setInt(14, reorderThreshold);
            ps.setString(15, hsnCode != null ? hsnCode : "");
            ps.setString(16, description != null ? description : "");
            ps.setString(17, (size == null || size.isBlank()) ? null : size.trim());
            ps.setString(18, (color == null || color.isBlank()) ? null : color.trim());
            ps.executeUpdate();
        }

        ActivityLogUtil.log(null, "CREATE", "PRODUCT", prod_id, prod_name + " added");
        return prod_id;
    }

    /**
     * Find product by ID
     */
    public static Product findById(String id) throws SQLException {
        String sql = "SELECT * FROM products WHERE id = ? AND is_active = 1";
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            if(rs.next()) {
                return extractProduct(rs);
            }
        }
        return null;
    }

    /**
     * Find product by barcode (primary lookup for scanner)
     */
    public static Product findByBarcode(String barcode) throws SQLException {
        if (barcode == null || barcode.isBlank()) return null;
        String sql = "SELECT * FROM products WHERE barcode = ? AND is_active = 1";
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, barcode);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return extractProduct(rs);
            }
        }
        return null;
    }

    /**
     * Find product by SKU
     */
    public static Product findBySku(String sku) throws SQLException {
        if (sku == null || sku.isBlank()) return null;
        String sql = "SELECT * FROM products WHERE sku = ? AND is_active = 1";
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, sku);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return extractProduct(rs);
            }
        }
        return null;
    }

    /** Filler words that describe search intent but never literally appear in stored fields. */
    private static final java.util.Set<String> SEARCH_STOPWORDS = java.util.Set.of(
            "size", "sz", "no", "no.", "number", "of", "in", "the", "a", "an", "for");

    /**
     * Free-text product search across everything a cashier might remember about
     * an item - name, category (e.g. "Half Hand Jersey Tshirt"), brand, variant,
     * size, color, SKU, and barcode - instead of requiring an exact SKU/barcode.
     * The query is split into words and every word must appear <i>somewhere</i>
     * in that combined text (in any order, any field), so "half hand jersey
     * tshirt size 32" matches a product whose category is "Half Hand Jersey
     * Tshirt" and whose size is "32", even though no single field contains the
     * whole phrase.
     */
    public static List<Product> searchByName(String query) throws SQLException {
        List<String> tokens = new ArrayList<>();
        if (query != null) {
            for (String word : query.trim().toLowerCase().split("\\s+")) {
                if (!word.isEmpty() && !SEARCH_STOPWORDS.contains(word)) {
                    tokens.add(word);
                }
            }
        }
        if (tokens.isEmpty()) {
            return getAllProducts();
        }

        String combinedText = "(COALESCE(name,'') || ' ' || COALESCE(category,'') || ' ' || " +
                "COALESCE(brand,'') || ' ' || COALESCE(variant,'') || ' ' || COALESCE(size,'') || ' ' || " +
                "COALESCE(color,'') || ' ' || COALESCE(sku,'') || ' ' || COALESCE(barcode,''))";

        StringBuilder sql = new StringBuilder("SELECT * FROM products WHERE is_active = 1");
        for (int i = 0; i < tokens.size(); i++) {
            sql.append(" AND ").append(combinedText).append(" LIKE ? ESCAPE '\\'");
        }
        sql.append(" ORDER BY name LIMIT 50");

        List<Product> products = new ArrayList<>();
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < tokens.size(); i++) {
                ps.setString(i + 1, "%" + escapeLike(tokens.get(i)) + "%");
            }
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                products.add(extractProduct(rs));
            }
        }
        return products;
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /**
     * Update product stock
     */
    public static void updateStock(String productId, int newStock) throws SQLException {
        String sql = "UPDATE products SET stock = ?, updated_at = datetime('now') WHERE id = ?";
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, newStock);
            ps.setString(2, productId);
            ps.executeUpdate();
        }
    }

    /**
     * Decrement stock as part of an existing transaction (e.g. from inside
     * SalesDAO.completeSale()). Must use the SAME connection as the caller -
     * a separate connection here would try to grab SQLite's single write lock
     * while the caller's own transaction is still holding it, which either
     * deadlocks or (worse) can commit a stock change independently of whether
     * the sale itself actually goes through.
     */
    public static void decrementStock(Connection conn, String productId, int quantity) throws SQLException {
        String sql = "UPDATE products SET stock = stock - ?, updated_at = datetime('now') " +
                "WHERE id = ? AND stock >= ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, quantity);
            ps.setString(2, productId);
            ps.setInt(3, quantity);
            int updated = ps.executeUpdate();
            if (updated == 0) {
                throw new SQLException("Insufficient stock for product: " + productId);
            }
        }
    }

    /** Used when a return puts stock back. */
    public static void incrementStock(String productId, int quantity) throws SQLException {
        String sql = "UPDATE products SET stock = stock + ?, updated_at = datetime('now') WHERE id = ?";
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, quantity);
            ps.setString(2, productId);
            ps.executeUpdate();
        }
    }

    /**
     * Get products with low stock (at or below reorder threshold)
     */
    public static List<Product> getLowStockProducts() throws SQLException {
        String sql = """
            SELECT * FROM products 
            WHERE stock <= reorder_threshold AND is_active = 1 
            ORDER BY stock ASC
        """;
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

    /**
     * Low stock products, each paired with a suggested reorder quantity based on
     * how fast it's actually been selling over the last 30 days:
     *   - If it's been selling: enough to cover the next 14 days at that pace
     *     (and always at least enough to clear the low-stock alert).
     *   - If it hasn't sold at all recently (new item, or slow mover): a safe
     *     default of twice the reorder threshold, since there's no sales data
     *     to estimate from.
     * This is a simple estimate to help you decide how much to order, not an
     * exact science - adjust based on what you know about the item.
     */
    public static List<com.vastra.model.LowStockSuggestion> getLowStockWithSuggestions() throws SQLException {
        String sql = """
            SELECT p.*, COALESCE(SUM(si.qty), 0) AS sold_last_30d
            FROM products p
            LEFT JOIN sale_items si ON si.product_id = p.id
            LEFT JOIN sales s ON s.id = si.sale_id AND s.ts >= datetime('now', '-30 days')
            WHERE p.stock <= p.reorder_threshold AND p.is_active = 1
            GROUP BY p.id
            ORDER BY p.stock ASC
        """;
        List<com.vastra.model.LowStockSuggestion> results = new ArrayList<>();
        try (Connection c = DBUtil.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                Product p = extractProduct(rs);
                int soldLast30Days = rs.getInt("sold_last_30d");

                int suggested;
                if (soldLast30Days > 0) {
                    double dailyAvg = soldLast30Days / 30.0;
                    int twoWeeksWorth = (int) Math.ceil(dailyAvg * 14);
                    int enoughToClearAlert = (p.getReorderThreshold() - p.getStock()) + 1;
                    suggested = Math.max(twoWeeksWorth, Math.max(enoughToClearAlert, 1));
                } else {
                    suggested = Math.max(p.getReorderThreshold() * 2 - p.getStock(), p.getReorderThreshold());
                }

                results.add(new com.vastra.model.LowStockSuggestion(p, soldLast30Days, suggested));
            }
        }
        return results;
    }

    /**
     * Get all active products
     */
    public static List<Product> getAllProducts() throws SQLException {
        String sql = "SELECT * FROM products WHERE is_active = 1 ORDER BY name";
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

    /**
     * Update product details
     */
    public static void updateProduct(Product product) throws SQLException {
        String sql = """
            UPDATE products SET
                name = ?, variant = ?, category = ?, brand = ?,
                barcode = ?, sku = ?, mrp_cents = ?, sell_price_cents = ?,
                purchase_price_cents = ?, gst_percent = ?, hsn_code = ?,
                stock = ?, opening_stock = ?, reorder_threshold = ?, unit = ?, description = ?,
                size = ?, color = ?,
                updated_at = datetime('now')
            WHERE id = ?
        """;

        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, product.getName());
            ps.setString(2, product.getVariant());
            ps.setString(3, product.getCategory());
            ps.setString(4, product.getBrand());
            ps.setString(5, product.getBarcode());
            ps.setString(6, product.getSku());
            ps.setInt(7, product.getMrpCents());
            ps.setInt(8, product.getSellPriceCents());
            ps.setInt(9, product.getPurchasePriceCents());
            ps.setInt(10, product.getGstPercent());
            ps.setString(11, product.getHsnCode());
            ps.setInt(12, product.getStock());
            ps.setInt(13, product.getOpeningStock());
            ps.setInt(14, product.getReorderThreshold());
            ps.setString(15, product.getUnit());
            ps.setString(16, product.getDescription());
            ps.setString(17, (product.getSize() == null || product.getSize().isBlank()) ? null : product.getSize().trim());
            ps.setString(18, (product.getColor() == null || product.getColor().isBlank()) ? null : product.getColor().trim());
            ps.setString(19, product.getId());
            ps.executeUpdate();
        }

        ActivityLogUtil.log(null, "UPDATE", "PRODUCT", product.getId(), product.getName() + " updated");
    }

    /**
     * Deactivate product (soft delete). The row is never physically removed,
     * so backups taken before or after this call both stay consistent - and
     * this action is recorded in activity_log so you can see what was
     * deleted and when.
     */
    public static void deactivateProduct(String productId) throws SQLException {
        Product existing = findById(productId);

        String sql = "UPDATE products SET is_active = 0, updated_at = datetime('now') WHERE id = ?";
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, productId);
            ps.executeUpdate();
        }

        String name = existing != null ? existing.getFullDisplayName() : productId;
        ActivityLogUtil.log(null, "DELETE", "PRODUCT", productId, name + " deleted (soft delete)");
    }

    /**
     * Check if barcode already exists
     */
    public static boolean barcodeExists(String barcode) throws SQLException {
        String sql = "SELECT COUNT(*) FROM products WHERE barcode = ?";
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, barcode);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    /**
     * Generate a short, human-readable product code (e.g. "P0001") instead of
     * a random 13-digit number - built from a global, persisted, never-reused
     * counter (see {@link #nextProductCodeSequence()}) so codes just keep
     * counting up (P0001, P0002, ...) no matter how many days/purchases apart
     * they're created, and a prefix configurable in Settings.
     */
    private static String generateBarcode() throws SQLException {
        String prefix = StoreSettingsDAO.get("product_code_prefix", "P");
        if (prefix == null || prefix.isBlank()) prefix = "P";
        prefix = prefix.trim().toUpperCase();

        String code;
        int attempts = 0;
        do {
            int seq = nextProductCodeSequence();
            code = prefix + String.format("%04d", seq);
            attempts++;
            if (attempts > 1000) {
                throw new SQLException("Could not generate unique product code");
            }
        } while (barcodeExists(code));
        return code;
    }

    /**
     * Atomically reserves and returns the next number in the global product
     * code sequence, persisted in store_settings so it survives restarts and
     * never goes backward or repeats - e.g. add 100 products today (0001-0100),
     * come back next week and the next one picked up is 0101, regardless of
     * which brand/category it is.
     */
    private static synchronized int nextProductCodeSequence() throws SQLException {
        String current = StoreSettingsDAO.get("next_product_code_seq", "1");
        int next;
        try {
            next = Integer.parseInt(current.trim());
        } catch (NumberFormatException e) {
            next = 1;
        }
        StoreSettingsDAO.set("next_product_code_seq", String.valueOf(next + 1));
        return next;
    }

    /**
     * Extract product from ResultSet
     */
    private static Product extractProduct(ResultSet rs) throws SQLException {
        Product p = new Product();
        p.setId(rs.getString("id"));
        p.setName(rs.getString("name"));
        p.setVariant(rs.getString("variant"));

        String category = rs.getString("category");
        p.setCategory(category != null ? category : "");

        String brand = rs.getString("brand");
        p.setBrand(brand != null ? brand : "");

        String barcode = rs.getString("barcode");
        p.setBarcode(barcode != null ? barcode : "");

        String sku = rs.getString("sku");
        p.setSku(sku != null ? sku : "");

        p.setMrpCents(rs.getInt("mrp_cents"));
        p.setSellPriceCents(rs.getInt("sell_price_cents"));
        p.setPurchasePriceCents(rs.getInt("purchase_price_cents"));
        p.setGstPercent(rs.getInt("gst_percent"));

        String hsnCode = rs.getString("hsn_code");
        p.setHsnCode(hsnCode != null ? hsnCode : "");

        p.setStock(rs.getInt("stock"));
        p.setOpeningStock(rs.getInt("opening_stock"));
        p.setReorderThreshold(rs.getInt("reorder_threshold"));

        String unit = rs.getString("unit");
        p.setUnit(unit != null ? unit : "PCS");

        String description = rs.getString("description");
        p.setDescription(description != null ? description : "");

        String imagePath = rs.getString("image_path");
        p.setImagePath(imagePath != null ? imagePath : "");

        p.setActive(rs.getInt("is_active") == 1);
        p.setCreatedAt(rs.getString("created_at"));

        String updatedAt = rs.getString("updated_at");
        p.setUpdatedAt(updatedAt != null ? updatedAt : "");

        p.setSize(rs.getString("size"));
        p.setColor(rs.getString("color"));

        return p;
    }
}