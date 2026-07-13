package com.vastra.dao;

import com.vastra.model.Customer;
import com.vastra.util.ActivityLogUtil;
import com.vastra.util.DBUtil;
import com.vastra.model.CustomerLedgerEntry;

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
    /**
     * Adds loyalty points as part of an existing transaction (e.g. from inside
     * SalesDAO.completeSale()) - see the note on ProductDAO.decrementStock()
     * for why this must reuse the caller's connection rather than open its own.
     */
    public static void addPoints(Connection conn, String customerId, double points) throws SQLException {
        String sql = "UPDATE customers SET points = points + ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
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

    public static List<Customer> searchByName(String term) throws SQLException {
        String sql = "SELECT * FROM customers WHERE is_active = 1 AND (name LIKE ? OR phone LIKE ?) ORDER BY name LIMIT 30";
        List<Customer> list = new ArrayList<>();
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, "%" + term + "%");
            ps.setString(2, "%" + term + "%");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(extractCustomer(rs));
        }
        return list;
    }

    /** Current amount owed BY this customer: sum of CREDIT sales - payments received. */
    public static int getCurrentDueCents(String customerId) throws SQLException {
        int creditSalesTotal = 0;
        String saleSql = "SELECT COALESCE(SUM(total_cents),0) FROM sales WHERE customer_id = ? AND payment_mode = 'CREDIT'";
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(saleSql)) {
            ps.setString(1, customerId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) creditSalesTotal = rs.getInt(1);
        }

        int paymentTotal = 0;
        String paymentSql = "SELECT COALESCE(SUM(amount_cents),0) FROM customer_payments WHERE customer_id = ?";
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(paymentSql)) {
            ps.setString(1, customerId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) paymentTotal = rs.getInt(1);
        }

        return creditSalesTotal - paymentTotal;
    }

    /** Full statement: every credit sale (debit) and payment (credit) in chronological order, with a running balance. */
    /**
     * Every item this customer has ever bought (any payment mode - unlike getLedger()
     * which only covers CREDIT sales for the money owed). Most recent first.
     */
    public static List<com.vastra.model.PurchaseHistoryRow> getPurchaseHistory(String customerId) throws SQLException {
        String sql = """
            SELECT s.ts, s.invoice_number, si.product_name, si.product_variant,
                   si.qty, si.unit_price_cents, si.line_total_cents
            FROM sale_items si
            JOIN sales s ON s.id = si.sale_id
            WHERE s.customer_id = ?
            ORDER BY s.ts DESC
        """;
        List<com.vastra.model.PurchaseHistoryRow> rows = new ArrayList<>();
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, customerId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                com.vastra.model.PurchaseHistoryRow row = new com.vastra.model.PurchaseHistoryRow();
                row.setDate(rs.getString("ts"));
                row.setBillNumber(rs.getString("invoice_number"));
                row.setProductName(rs.getString("product_name"));
                row.setVariant(rs.getString("product_variant"));
                row.setQuantity(rs.getInt("qty"));
                row.setUnitPriceCents(rs.getInt("unit_price_cents"));
                row.setLineTotalCents(rs.getInt("line_total_cents"));
                rows.add(row);
            }
        }
        return rows;
    }

    public static List<CustomerLedgerEntry> getLedger(String customerId) throws SQLException {
        List<Object[]> rawRows = new ArrayList<>(); // [date, type, refNo, description, amountCents]

        String saleSql = "SELECT ts, invoice_number, total_cents FROM sales WHERE customer_id = ? AND payment_mode = 'CREDIT'";
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(saleSql)) {
            ps.setString(1, customerId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                rawRows.add(new Object[]{rs.getString("ts"), "SALE", rs.getString("invoice_number"), "Credit sale", rs.getInt("total_cents")});
            }
        }

        String paymentSql = "SELECT payment_date, payment_mode, amount_cents, notes FROM customer_payments WHERE customer_id = ?";
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(paymentSql)) {
            ps.setString(1, customerId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                rawRows.add(new Object[]{rs.getString("payment_date"), "PAYMENT", rs.getString("payment_mode"),
                        rs.getString("notes") != null ? rs.getString("notes") : "Payment received", rs.getInt("amount_cents")});
            }
        }

        rawRows.sort((a, b) -> String.valueOf(a[0]).compareTo(String.valueOf(b[0])));

        List<CustomerLedgerEntry> ledger = new ArrayList<>();
        int running = 0;
        for (Object[] row : rawRows) {
            String date = (String) row[0];
            boolean isSale = "SALE".equals(row[1]);
            int amountCents = (int) row[4];
            if (isSale) {
                running += amountCents;
                ledger.add(new CustomerLedgerEntry(date, CustomerLedgerEntry.Type.SALE, (String) row[2], (String) row[3], amountCents, 0, running));
            } else {
                running -= amountCents;
                ledger.add(new CustomerLedgerEntry(date, CustomerLedgerEntry.Type.PAYMENT, (String) row[2], (String) row[3], 0, amountCents, running));
            }
        }
        return ledger;
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

    /** Updates the editable profile fields (not points/tier, which only change via addPoints/redeemPoints). */
    public static void updateCustomerDetails(Customer c) throws SQLException {
        String sql = """
        UPDATE customers SET name = ?, phone = ?, email = ?, address = ?, city = ?, pincode = ?
        WHERE id = ?
    """;
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, c.getName());
            ps.setString(2, c.getPhone());
            ps.setString(3, c.getEmail());
            ps.setString(4, c.getAddress());
            ps.setString(5, c.getCity());
            ps.setString(6, c.getPincode());
            ps.setString(7, c.getId());
            ps.executeUpdate();
        }
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