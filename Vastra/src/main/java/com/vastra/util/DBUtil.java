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

             s.execute("""
                     CREATE TABLE IF NOT EXISTS products (
                     id TEXT PRIMARY KEY,
                     name TEXT NOT NULL,
                     variant TEXT,
                     mrp_cents INTEGER,
                     sell_price_cents INTEGER,
                     gst_percent INTEGER,
                     stock INTEGER DEFAULT 0,
                     reorder_threshold INTEGER DEFAULT 5,
                     created_at TEXT);""");
            s.execute("""
                CREATE TABLE IF NOT EXISTS customers(
                  id TEXT PRIMARY KEY,
                  name TEXT,
                  phone TEXT UNIQUE,
                  email TEXT,
                  points INTEGER DEFAULT 0,
                  created_at TEXT
                );
            """);

            s.execute("""
                CREATE TABLE IF NOT EXISTS sales(
                  id TEXT PRIMARY KEY,
                  customer_id TEXT,
                  ts TEXT,
                  subtotal_cents INTEGER,
                  tax_cents INTEGER,
                  discount_cents INTEGER,
                  total_cents INTEGER,
                  payment_mode TEXT,
                  status TEXT
                );
            """);

            s.execute("""
                CREATE TABLE IF NOT EXISTS sale_items(
                  id TEXT PRIMARY KEY,
                  sale_id TEXT,
                  product_id TEXT,
                  qty INTEGER,
                  sell_price_cents INTEGER,
                  tax_percent INTEGER,
                  line_total_cents INTEGER
                );
            """);

            s.execute("CREATE INDEX IF NOT EXISTS idx_products_name ON products(name);");
            s.execute("CREATE INDEX IF NOT EXISTS idx_sales_ts ON sales(ts);");
            s.execute("CREATE INDEX IF NOT EXISTS idx_sale_items_product ON sale_items(product_id);");
        }
    }
}
