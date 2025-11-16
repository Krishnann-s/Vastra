package com.vastra.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class DBUtil {

    private static final String DB_URL = "jdbc:sqlite:db/vastra.db";

    public static Connection getConnection() throws java.sql.SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    public static void init() throws Exception {
        try (Connection c = getConnection();
             Statement s = c.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("PRAGMA synchronous=NORMAL");

            // Products table with enhanced fields
            s.execute("""
                     CREATE TABLE IF NOT EXISTS products (
                     id TEXT PRIMARY KEY,
                     name TEXT NOT NULL,
                     variant TEXT,
                     category TEXT,
                     brand TEXT,
                     barcode TEXT UNIQUE,
                     sku TEXT,
                     mrp_cents INTEGER NOT NULL,
                     sell_price_cents INTEGER NOT NULL,
                     purchase_price_cents INTEGER DEFAULT 0,
                     gst_percent INTEGER DEFAULT 0,
                     hsn_code TEXT,
                     stock INTEGER DEFAULT 0,
                     reorder_threshold INTEGER DEFAULT 5,
                     unit TEXT DEFAULT 'PCS',
                     description TEXT,
                     image_path TEXT,
                     is_active INTEGER DEFAULT 1,
                     created_at TEXT,
                     updated_at TEXT);""");

            // Customers table with enhanced fields
            s.execute("""
                CREATE TABLE IF NOT EXISTS customers(
                  id TEXT PRIMARY KEY,
                  name TEXT NOT NULL,
                  phone TEXT UNIQUE NOT NULL,
                  email TEXT,
                  address TEXT,
                  city TEXT,
                  pincode TEXT,
                  birthday TEXT,
                  anniversary TEXT,
                  points INTEGER DEFAULT 0,
                  total_purchases_cents INTEGER DEFAULT 0,
                  visit_count INTEGER DEFAULT 0,
                  tier TEXT DEFAULT 'BRONZE',
                  notes TEXT,
                  is_active INTEGER DEFAULT 1,
                  created_at TEXT,
                  last_visit TEXT
                );
            """);

            // Sales table with enhanced fields
            s.execute("""
                CREATE TABLE IF NOT EXISTS sales(
                  id TEXT PRIMARY KEY,
                  invoice_number TEXT UNIQUE,
                  customer_id TEXT,
                  cashier_name TEXT,
                  ts TEXT NOT NULL,
                  subtotal_cents INTEGER NOT NULL,
                  tax_cents INTEGER NOT NULL,
                  discount_cents INTEGER DEFAULT 0,
                  points_redeemed INTEGER DEFAULT 0,
                  total_cents INTEGER NOT NULL,
                  payment_mode TEXT NOT NULL,
                  amount_received_cents INTEGER,
                  change_returned_cents INTEGER,
                  status TEXT DEFAULT 'COMPLETED',
                  notes TEXT,
                  created_at TEXT,
                  FOREIGN KEY(customer_id) REFERENCES customers(id)
                );
            """);

            // Sale items table
            s.execute("""
                CREATE TABLE IF NOT EXISTS sale_items(
                  id TEXT PRIMARY KEY,
                  sale_id TEXT NOT NULL,
                  product_id TEXT NOT NULL,
                  product_name TEXT NOT NULL,
                  product_variant TEXT,
                  qty INTEGER NOT NULL,
                  unit_price_cents INTEGER NOT NULL,
                  discount_cents INTEGER DEFAULT 0,
                  tax_percent INTEGER DEFAULT 0,
                  line_total_cents INTEGER NOT NULL,
                  FOREIGN KEY(sale_id) REFERENCES sales(id),
                  FOREIGN KEY(product_id) REFERENCES products(id)
                );
            """);

            // Store settings table
            s.execute("""
                CREATE TABLE IF NOT EXISTS store_settings(
                  key TEXT PRIMARY KEY,
                  value TEXT,
                  updated_at TEXT
                );
            """);

            // Activity log table
            s.execute("""
                CREATE TABLE IF NOT EXISTS activity_log(
                  id TEXT PRIMARY KEY,
                  user_name TEXT,
                  action TEXT,
                  entity_type TEXT,
                  entity_id TEXT,
                  details TEXT,
                  timestamp TEXT
                );
            """);

            // Returns/Exchange table
            s.execute("""
                CREATE TABLE IF NOT EXISTS returns(
                  id TEXT PRIMARY KEY,
                  sale_id TEXT NOT NULL,
                  customer_id TEXT,
                  return_date TEXT,
                  reason TEXT,
                  refund_amount_cents INTEGER,
                  status TEXT DEFAULT 'PENDING',
                  processed_by TEXT,
                  notes TEXT,
                  FOREIGN KEY(sale_id) REFERENCES sales(id)
                );
            """);

            // Return items table
            s.execute("""
                CREATE TABLE IF NOT EXISTS return_items(
                  id TEXT PRIMARY KEY,
                  return_id TEXT NOT NULL,
                  sale_item_id TEXT NOT NULL,
                  product_id TEXT NOT NULL,
                  qty INTEGER NOT NULL,
                  refund_amount_cents INTEGER,
                  FOREIGN KEY(return_id) REFERENCES returns(id)
                );
            """);

            // Create indexes
            s.execute("CREATE INDEX IF NOT EXISTS idx_products_name ON products(name);");
            s.execute("CREATE INDEX IF NOT EXISTS idx_products_barcode ON products(barcode);");
            s.execute("CREATE INDEX IF NOT EXISTS idx_products_category ON products(category);");
            s.execute("CREATE INDEX IF NOT EXISTS idx_customers_phone ON customers(phone);");
            s.execute("CREATE INDEX IF NOT EXISTS idx_sales_ts ON sales(ts);");
            s.execute("CREATE INDEX IF NOT EXISTS idx_sales_customer ON sales(customer_id);");
            s.execute("CREATE INDEX IF NOT EXISTS idx_sales_invoice ON sales(invoice_number);");
            s.execute("CREATE INDEX IF NOT EXISTS idx_sale_items_sale ON sale_items(sale_id);");
            s.execute("CREATE INDEX IF NOT EXISTS idx_sale_items_product ON sale_items(product_id);");

            // Insert default store settings
            s.execute("""
                INSERT OR IGNORE INTO store_settings(key, value, updated_at) VALUES
                ('store_name', 'Vastra Store', datetime('now')),
                ('store_address', '', datetime('now')),
                ('store_phone', '', datetime('now')),
                ('store_email', '', datetime('now')),
                ('store_gstin', '', datetime('now')),
                ('tax_enabled', '1', datetime('now')),
                ('loyalty_enabled', '1', datetime('now')),
                ('points_per_100_rupees', '1', datetime('now')),
                ('min_points_redemption', '100', datetime('now')),
                ('receipt_footer', 'Thank you for shopping with us!', datetime('now')),
                ('currency_symbol', '₹', datetime('now')),
                ('low_stock_alert_enabled', '1', datetime('now'));
            """);
        }
    }
}