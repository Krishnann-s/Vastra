package com.vastra.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.PreparedStatement;

public class DBUtil {

    // Public so BackupUtil (and anything else that needs the raw file) can reference it.
    // Overridable via -Dvastra.db.path=... so tests can point at an isolated database
    // instead of the real shop data - see pom.xml's surefire configuration, which sets
    // this for every test run. In normal (non-test) use this system property is never
    // set, so production behavior is exactly as before.
    public static final String DB_FILE_PATH = System.getProperty("vastra.db.path", "db/vastra.db");
    private static final String DB_URL = "jdbc:sqlite:" + DB_FILE_PATH;

    static {
        // Explicitly load the driver instead of relying solely on JDBC 4's automatic
        // ServiceLoader registration - some runtime environments (certain test runners,
        // and potentially a jpackage'd executable) use a classloader setup where the
        // automatic registration doesn't reliably see the driver otherwise.
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError("SQLite JDBC driver not found on the classpath: " + e.getMessage());
        }
    }

    public static Connection getConnection() throws java.sql.SQLException {
        java.io.File dbFile = new java.io.File(DB_FILE_PATH);
        java.io.File parent = dbFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        Connection conn = DriverManager.getConnection(DB_URL);
        // Without this, two connections that briefly overlap (e.g. a sale being
        // completed at the same moment the auto-backup's VACUUM INTO runs) get an
        // immediate "database is locked" error instead of one of them just waiting
        // a moment for the other to finish - a genuine issue this project's own
        // test suite caught. 5s is generous for a single-shop SQLite file.
        try (Statement s = conn.createStatement()) {
            s.execute("PRAGMA busy_timeout = 5000");
        }
        return conn;
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

            // Activity log table (used for the deletion / audit trail)
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

            // Suppliers table
            s.execute("""
                CREATE TABLE IF NOT EXISTS suppliers(
                  id TEXT PRIMARY KEY,
                  name TEXT NOT NULL,
                  phone TEXT,
                  address TEXT,
                  gst_no TEXT,
                  opening_balance_cents INTEGER DEFAULT 0,
                  notes TEXT,
                  is_active INTEGER DEFAULT 1,
                  created_at TEXT
                );
            """);

            // Purchases (one row per supplier invoice)
            s.execute("""
                CREATE TABLE IF NOT EXISTS purchases(
                  id TEXT PRIMARY KEY,
                  supplier_id TEXT NOT NULL,
                  invoice_number TEXT,
                  invoice_date TEXT,
                  total_amount_cents INTEGER NOT NULL,
                  due_date TEXT,
                  notes TEXT,
                  created_at TEXT,
                  FOREIGN KEY(supplier_id) REFERENCES suppliers(id)
                );
            """);

            // Purchase line items
            s.execute("""
                CREATE TABLE IF NOT EXISTS purchase_items(
                  id TEXT PRIMARY KEY,
                  purchase_id TEXT NOT NULL,
                  product_id TEXT,
                  product_name TEXT,
                  qty INTEGER NOT NULL,
                  cost_price_cents INTEGER NOT NULL,
                  line_total_cents INTEGER NOT NULL,
                  FOREIGN KEY(purchase_id) REFERENCES purchases(id)
                );
            """);

            // Payments made to suppliers (credits against what you owe them)
            s.execute("""
                CREATE TABLE IF NOT EXISTS supplier_payments(
                  id TEXT PRIMARY KEY,
                  supplier_id TEXT NOT NULL,
                  purchase_id TEXT,
                  amount_cents INTEGER NOT NULL,
                  payment_date TEXT,
                  payment_mode TEXT,
                  notes TEXT,
                  created_at TEXT,
                  FOREIGN KEY(supplier_id) REFERENCES suppliers(id)
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
            s.execute("CREATE INDEX IF NOT EXISTS idx_suppliers_name ON suppliers(name);");
            s.execute("CREATE INDEX IF NOT EXISTS idx_purchases_supplier ON purchases(supplier_id);");
            s.execute("CREATE INDEX IF NOT EXISTS idx_purchases_duedate ON purchases(due_date);");
            s.execute("CREATE INDEX IF NOT EXISTS idx_purchase_items_purchase ON purchase_items(purchase_id);");
            s.execute("CREATE INDEX IF NOT EXISTS idx_supplier_payments_supplier ON supplier_payments(supplier_id);");

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
                ('low_stock_alert_enabled', '1', datetime('now')),
                ('last_backup_at', '', datetime('now')),
                ('counter_name', 'Serve', datetime('now')),
                ('exchange_policy', 'Exchange only one time
Without bill cannot be exchanged
Exchange time 2 to 4pm
Exchange within 2 days from date of purchase
Don''t bargain', datetime('now'));
            """);

            // Payments received from customers against CREDIT sales
            s.execute("""
                CREATE TABLE IF NOT EXISTS customer_payments(
                  id TEXT PRIMARY KEY,
                  customer_id TEXT NOT NULL,
                  sale_id TEXT,
                  amount_cents INTEGER NOT NULL,
                  payment_date TEXT,
                  payment_mode TEXT,
                  notes TEXT,
                  created_at TEXT,
                  FOREIGN KEY(customer_id) REFERENCES customers(id)
                );
            """);
            s.execute("CREATE INDEX IF NOT EXISTS idx_return_items_sale_item ON return_items(sale_item_id);");
            s.execute("CREATE INDEX IF NOT EXISTS idx_customer_payments_customer ON customer_payments(customer_id);");

            // Shop running expenses (rent, salaries, electricity, etc.) - feeds the Expense Log report
            s.execute("""
                CREATE TABLE IF NOT EXISTS expenses(
                  id TEXT PRIMARY KEY,
                  category TEXT,
                  description TEXT,
                  amount_cents INTEGER NOT NULL,
                  expense_date TEXT,
                  payment_mode TEXT,
                  notes TEXT,
                  created_at TEXT
                );
            """);
            s.execute("CREATE INDEX IF NOT EXISTS idx_expenses_date ON expenses(expense_date);");

            // Held bills - cart snapshot saved mid-sale so the counter can serve another
            // customer, then resume later (see the Hold Sale button on the billing screen)
            s.execute("""
                CREATE TABLE IF NOT EXISTS held_bills(
                  id TEXT PRIMARY KEY,
                  label TEXT,
                  customer_id TEXT,
                  customer_name TEXT,
                  cart_json TEXT NOT NULL,
                  discount_cents INTEGER DEFAULT 0,
                  held_by TEXT,
                  created_at TEXT
                );
            """);

            // One row per payment method used on a sale - lets a single bill be split
            // across Cash/Card/UPI. For a normal single-mode sale this just has one row.
            s.execute("""
                CREATE TABLE IF NOT EXISTS sale_payments(
                  id TEXT PRIMARY KEY,
                  sale_id TEXT NOT NULL,
                  mode TEXT NOT NULL,
                  amount_cents INTEGER NOT NULL,
                  FOREIGN KEY(sale_id) REFERENCES sales(id)
                );
            """);
            s.execute("CREATE INDEX IF NOT EXISTS idx_sale_payments_sale ON sale_payments(sale_id);");

            // One row per business day the register is opened/closed - tracks the
            // opening float, what cash *should* be there at close based on the day's
            // transactions, what was actually counted, and the variance between them.
            s.execute("""
                CREATE TABLE IF NOT EXISTS cash_register_sessions(
                  id TEXT PRIMARY KEY,
                  business_date TEXT NOT NULL,
                  opening_cents INTEGER NOT NULL,
                  opening_notes TEXT,
                  opened_by TEXT,
                  opened_at TEXT,
                  closing_cents INTEGER,
                  closing_notes TEXT,
                  closed_by TEXT,
                  closed_at TEXT,
                  expected_cents INTEGER,
                  variance_cents INTEGER,
                  status TEXT DEFAULT 'OPEN'
                );
            """);
            s.execute("CREATE INDEX IF NOT EXISTS idx_cash_register_date ON cash_register_sessions(business_date);");

            // Users table (login accounts - admins with passwords, cashiers with PINs)
            s.execute("""
                CREATE TABLE IF NOT EXISTS users(
                  id TEXT PRIMARY KEY,
                  username TEXT UNIQUE NOT NULL,
                  role TEXT NOT NULL,
                  password_hash TEXT,
                  password_salt TEXT,
                  pin_hash TEXT,
                  pin_salt TEXT,
                  display_name TEXT,
                  is_active INTEGER DEFAULT 1,
                  created_at TEXT
                );
            """);

            // Safe migration: products.size / products.color may not exist yet on an
            // already-deployed database (the table predates these columns), so add
            // them only if missing rather than assuming a fresh CREATE TABLE ran.
            ensureColumnExists(c, "products", "size", "TEXT");
            ensureColumnExists(c, "products", "color", "TEXT");

            seedDefaultAdminIfNeeded(c);
        }
    }

    /** Adds a column to an existing table only if it isn't already there - SQLite has no "ADD COLUMN IF NOT EXISTS". */
    private static void ensureColumnExists(Connection c, String table, String column, String definition) throws java.sql.SQLException {
        boolean exists = false;
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    exists = true;
                    break;
                }
            }
        }
        if (!exists) {
            try (Statement s = c.createStatement()) {
                s.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
            }
        }
    }

    private static void seedDefaultAdminIfNeeded(Connection c) throws Exception {
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM users WHERE role = 'ADMIN'")) {
            if (rs.next() && rs.getInt(1) == 0) {
                String[] hashAndSalt = PasswordUtil.hash("admin123");
                String sql = """
                    INSERT INTO users(id, username, role, password_hash, password_salt, display_name, is_active, created_at)
                    VALUES (?, 'admin', 'ADMIN', ?, ?, 'Admin', 1, datetime('now'))
                """;
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setString(1, java.util.UUID.randomUUID().toString());
                    ps.setString(2, hashAndSalt[0]);
                    ps.setString(3, hashAndSalt[1]);
                    ps.executeUpdate();
                }
            }
        }
    }
}