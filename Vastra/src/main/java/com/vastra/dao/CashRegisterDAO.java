package com.vastra.dao;

import com.vastra.model.CashRegisterSession;
import com.vastra.util.ActivityLogUtil;
import com.vastra.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Backs cash register open/close reconciliation. "Expected cash" is worked out from
 * the day's actual transactions rather than trusted blindly:
 *
 *   expected = opening float
 *            + cash sales today
 *            + cash payments received from customers today
 *            - cash refunds paid out today
 *            - cash expenses paid today
 *            - cash payments made to suppliers today
 *
 * Whatever's physically counted at close time is compared against that to get the variance.
 */
public class CashRegisterDAO {

    public static CashRegisterSession getTodaySession() throws SQLException {
        String today = LocalDate.now().toString();
        String sql = "SELECT * FROM cash_register_sessions WHERE business_date = ? ORDER BY opened_at DESC LIMIT 1";
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, today);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? extract(rs) : null;
        }
    }

    public static String openRegister(int openingCents, String notes, String openedBy) throws SQLException {
        String id = UUID.randomUUID().toString();
        String sql = """
            INSERT INTO cash_register_sessions(id, business_date, opening_cents, opening_notes, opened_by, opened_at, status)
            VALUES (?, ?, ?, ?, ?, datetime('now'), 'OPEN')
        """;
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, LocalDate.now().toString());
            ps.setInt(3, openingCents);
            ps.setString(4, notes);
            ps.setString(5, openedBy);
            ps.executeUpdate();
        }
        ActivityLogUtil.log(null, "CREATE", "CASH_REGISTER", id,
                "Register opened with ₹" + String.format("%.2f", openingCents / 100.0));
        return id;
    }

    /** What the drawer *should* have in it right now, based on today's transactions so far. */
    public static int computeExpectedCashCents(String businessDate, int openingCents) throws SQLException {
        int cashSales = scalarInt("""
            SELECT COALESCE(SUM(sp.amount_cents), 0)
            FROM sale_payments sp
            JOIN sales s ON s.id = sp.sale_id
            WHERE sp.mode = 'CASH' AND DATE(s.ts) = ?
        """, businessDate);

        int cashCustomerPayments = scalarInt("""
            SELECT COALESCE(SUM(amount_cents), 0) FROM customer_payments
            WHERE payment_mode = 'CASH' AND payment_date = ?
        """, businessDate);

        int cashRefunds = scalarInt("""
            SELECT COALESCE(SUM(refund_amount_cents), 0) FROM returns
            WHERE status = 'COMPLETED' AND return_date = ?
        """, businessDate);

        int cashExpenses = scalarInt("""
            SELECT COALESCE(SUM(amount_cents), 0) FROM expenses
            WHERE payment_mode = 'CASH' AND expense_date = ?
        """, businessDate);

        int cashSupplierPayments = scalarInt("""
            SELECT COALESCE(SUM(amount_cents), 0) FROM supplier_payments
            WHERE payment_mode = 'CASH' AND payment_date = ?
        """, businessDate);

        return openingCents + cashSales + cashCustomerPayments - cashRefunds - cashExpenses - cashSupplierPayments;
    }

    public static void closeRegister(String sessionId, int closingCents, String notes, String closedBy) throws SQLException {
        CashRegisterSession session = getById(sessionId);
        if (session == null) throw new SQLException("Register session not found");

        int expected = computeExpectedCashCents(session.getBusinessDate(), session.getOpeningCents());
        int variance = closingCents - expected;

        String sql = """
            UPDATE cash_register_sessions
            SET closing_cents = ?, closing_notes = ?, closed_by = ?, closed_at = datetime('now'),
                expected_cents = ?, variance_cents = ?, status = 'CLOSED'
            WHERE id = ?
        """;
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, closingCents);
            ps.setString(2, notes);
            ps.setString(3, closedBy);
            ps.setInt(4, expected);
            ps.setInt(5, variance);
            ps.setString(6, sessionId);
            ps.executeUpdate();
        }
        ActivityLogUtil.log(null, "UPDATE", "CASH_REGISTER", sessionId,
                String.format("Register closed - expected \u20B9%.2f, counted \u20B9%.2f, variance \u20B9%.2f",
                        expected / 100.0, closingCents / 100.0, variance / 100.0));
    }

    public static List<CashRegisterSession> getRecentSessions(int limit) throws SQLException {
        String sql = "SELECT * FROM cash_register_sessions ORDER BY opened_at DESC LIMIT ?";
        List<CashRegisterSession> results = new ArrayList<>();
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) results.add(extract(rs));
        }
        return results;
    }

    private static CashRegisterSession getById(String id) throws SQLException {
        String sql = "SELECT * FROM cash_register_sessions WHERE id = ?";
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? extract(rs) : null;
        }
    }

    private static int scalarInt(String sql, String param) throws SQLException {
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, param);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static CashRegisterSession extract(ResultSet rs) throws SQLException {
        CashRegisterSession s = new CashRegisterSession();
        s.setId(rs.getString("id"));
        s.setBusinessDate(rs.getString("business_date"));
        s.setOpeningCents(rs.getInt("opening_cents"));
        s.setOpeningNotes(rs.getString("opening_notes"));
        s.setOpenedBy(rs.getString("opened_by"));
        s.setOpenedAt(rs.getString("opened_at"));

        int closing = rs.getInt("closing_cents");
        s.setClosingCents(rs.wasNull() ? null : closing);
        s.setClosingNotes(rs.getString("closing_notes"));
        s.setClosedBy(rs.getString("closed_by"));
        s.setClosedAt(rs.getString("closed_at"));

        int expected = rs.getInt("expected_cents");
        s.setExpectedCents(rs.wasNull() ? null : expected);
        int variance = rs.getInt("variance_cents");
        s.setVarianceCents(rs.wasNull() ? null : variance);

        s.setStatus(rs.getString("status"));
        return s;
    }
}