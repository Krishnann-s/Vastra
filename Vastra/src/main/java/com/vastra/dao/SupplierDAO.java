package com.vastra.dao;

import com.vastra.model.LedgerEntry;
import com.vastra.model.PayableReminder;
import com.vastra.model.Supplier;
import com.vastra.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SupplierDAO {

    public static String createSupplier(String name, String phone, String address,
                                         String gstNo, int openingBalanceCents, String notes) throws SQLException {
        String id = UUID.randomUUID().toString();
        String sql = """
            INSERT INTO suppliers(id, name, phone, address, gst_no, opening_balance_cents, notes, is_active, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, 1, datetime('now'))
        """;
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, name);
            ps.setString(3, phone);
            ps.setString(4, address);
            ps.setString(5, gstNo);
            ps.setInt(6, openingBalanceCents);
            ps.setString(7, notes);
            ps.executeUpdate();
        }
        return id;
    }

    public static void updateSupplier(Supplier s) throws SQLException {
        String sql = """
            UPDATE suppliers SET name=?, phone=?, address=?, gst_no=?, notes=?
            WHERE id=?
        """;
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, s.getName());
            ps.setString(2, s.getPhone());
            ps.setString(3, s.getAddress());
            ps.setString(4, s.getGstNo());
            ps.setString(5, s.getNotes());
            ps.setString(6, s.getId());
            ps.executeUpdate();
        }
    }

    public static void deactivateSupplier(String id) throws SQLException {
        Supplier existing = findById(id);

        String sql = "UPDATE suppliers SET is_active = 0 WHERE id = ?";
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        }

        String name = existing != null ? existing.getName() : id;
        com.vastra.util.ActivityLogUtil.log(null, "DELETE", "SUPPLIER", id, name + " deleted (soft delete)");
    }

    public static Supplier findById(String id) throws SQLException {
        String sql = "SELECT * FROM suppliers WHERE id = ?";
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return extract(rs);
        }
        return null;
    }

    public static List<Supplier> getAllActive() throws SQLException {
        String sql = "SELECT * FROM suppliers WHERE is_active = 1 ORDER BY name";
        List<Supplier> list = new ArrayList<>();
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(extract(rs));
        }
        return list;
    }

    public static List<Supplier> searchByName(String term) throws SQLException {
        String sql = "SELECT * FROM suppliers WHERE is_active = 1 AND name LIKE ? ORDER BY name LIMIT 30";
        List<Supplier> list = new ArrayList<>();
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, "%" + term + "%");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(extract(rs));
        }
        return list;
    }

    /**
     * Current amount owed to this supplier:
     * opening balance + all purchases - all payments made.
     */
    public static int getCurrentDueCents(String supplierId) throws SQLException {
        int opening = 0;
        String openingSql = "SELECT opening_balance_cents FROM suppliers WHERE id = ?";
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(openingSql)) {
            ps.setString(1, supplierId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) opening = rs.getInt(1);
        }

        int purchaseTotal = 0;
        String purchaseSql = "SELECT COALESCE(SUM(total_amount_cents),0) FROM purchases WHERE supplier_id = ?";
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(purchaseSql)) {
            ps.setString(1, supplierId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) purchaseTotal = rs.getInt(1);
        }

        int paymentTotal = 0;
        String paymentSql = "SELECT COALESCE(SUM(amount_cents),0) FROM supplier_payments WHERE supplier_id = ?";
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(paymentSql)) {
            ps.setString(1, supplierId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) paymentTotal = rs.getInt(1);
        }

        return opening + purchaseTotal - paymentTotal;
    }

    /**
     * Full statement: opening balance, then every purchase (debit) and every
     * payment (credit) in chronological order, with a running balance.
     */
    public static List<LedgerEntry> getLedger(String supplierId) throws SQLException {
        Supplier supplier = findById(supplierId);
        if (supplier == null) return new ArrayList<>();

        // Pull purchases and payments into a single sortable list of "raw rows"
        List<Object[]> rawRows = new ArrayList<>(); // [date, type, refNo, description, amountCents]

        String purchaseSql = "SELECT invoice_date, invoice_number, total_amount_cents, created_at FROM purchases WHERE supplier_id = ?";
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(purchaseSql)) {
            ps.setString(1, supplierId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                rawRows.add(new Object[]{
                        rs.getString("invoice_date"), "PURCHASE",
                        rs.getString("invoice_number"), "Purchase",
                        rs.getInt("total_amount_cents"), rs.getString("created_at")
                });
            }
        }

        String paymentSql = "SELECT payment_date, payment_mode, amount_cents, notes, created_at FROM supplier_payments WHERE supplier_id = ?";
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(paymentSql)) {
            ps.setString(1, supplierId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                rawRows.add(new Object[]{
                        rs.getString("payment_date"), "PAYMENT",
                        rs.getString("payment_mode"),
                        rs.getString("notes") != null ? rs.getString("notes") : "Payment",
                        rs.getInt("amount_cents"), rs.getString("created_at")
                });
            }
        }

        // Sort chronologically by date, then created_at as a tiebreaker
        rawRows.sort((a, b) -> {
            int dateCompare = String.valueOf(a[0]).compareTo(String.valueOf(b[0]));
            if (dateCompare != 0) return dateCompare;
            return String.valueOf(a[5]).compareTo(String.valueOf(b[5]));
        });

        List<LedgerEntry> ledger = new ArrayList<>();
        int running = supplier.getOpeningBalanceCents();
        ledger.add(new LedgerEntry(supplier.getCreatedAt(), LedgerEntry.Type.OPENING,
                "-", "Opening balance", 0, 0, running));

        for (Object[] row : rawRows) {
            String date = (String) row[0];
            String type = (String) row[1];
            String refNo = (String) row[2];
            String description = (String) row[3];
            int amountCents = (int) row[4];

            if ("PURCHASE".equals(type)) {
                running += amountCents;
                ledger.add(new LedgerEntry(date, LedgerEntry.Type.PURCHASE, refNo, description, amountCents, 0, running));
            } else {
                running -= amountCents;
                ledger.add(new LedgerEntry(date, LedgerEntry.Type.PAYMENT, refNo, description, 0, amountCents, running));
            }
        }

        return ledger;
    }

    /**
     * Finds the single most urgent supplier payment to show as a reminder
     * banner: among suppliers who currently have a balance due, picks the
     * one with the earliest outstanding invoice due date (most overdue
     * first, then soonest upcoming). Returns null if nobody is owed anything
     * or no purchases have due dates set.
     */
    public static PayableReminder getNextPaymentReminder() throws SQLException {
        List<Supplier> allSuppliers = getAllActive();
        Supplier mostUrgentSupplier = null;
        String mostUrgentDueDate = null;
        int mostUrgentAmountCents = 0;

        for (Supplier s : allSuppliers) {
            int dueCents = getCurrentDueCents(s.getId());
            if (dueCents <= 0) continue; // fully paid up, nothing to remind about

            String earliestDueDate = null;
            String sql = "SELECT MIN(due_date) FROM purchases WHERE supplier_id = ? AND due_date IS NOT NULL AND due_date != ''";
            try (Connection c = DBUtil.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, s.getId());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) earliestDueDate = rs.getString(1);
            }
            if (earliestDueDate == null) continue; // no due date recorded, skip from reminder

            if (mostUrgentDueDate == null || earliestDueDate.compareTo(mostUrgentDueDate) < 0) {
                mostUrgentDueDate = earliestDueDate;
                mostUrgentSupplier = s;
                mostUrgentAmountCents = dueCents;
            }
        }

        if (mostUrgentSupplier == null) return null;

        LocalDate dueDate = LocalDate.parse(mostUrgentDueDate);
        LocalDate today = LocalDate.now();
        long daysDiff = ChronoUnit.DAYS.between(dueDate, today); // positive = overdue by N days
        boolean overdue = daysDiff > 0;

        return new PayableReminder(mostUrgentSupplier.getId(), mostUrgentSupplier.getName(),
                mostUrgentDueDate, mostUrgentAmountCents, overdue, daysDiff);
    }

    private static Supplier extract(ResultSet rs) throws SQLException {
        Supplier s = new Supplier();
        s.setId(rs.getString("id"));
        s.setName(rs.getString("name"));
        s.setPhone(rs.getString("phone"));
        s.setAddress(rs.getString("address"));
        s.setGstNo(rs.getString("gst_no"));
        s.setOpeningBalanceCents(rs.getInt("opening_balance_cents"));
        s.setNotes(rs.getString("notes"));
        s.setActive(rs.getInt("is_active") == 1);
        s.setCreatedAt(rs.getString("created_at"));
        return s;
    }
}