package com.vastra.dao;

import com.vastra.model.Expense;
import com.vastra.util.ActivityLogUtil;
import com.vastra.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ExpenseDAO {

    /** Common expense categories for a small retail shop - shown as suggestions, not enforced. */
    public static final String[] CATEGORIES = {
            "Rent", "Salaries", "Electricity", "Water", "Internet/Phone",
            "Transport", "Packing Material", "Maintenance", "Marketing", "Other"
    };

    public static String addExpense(String category, String description, int amountCents,
                                     String expenseDate, String paymentMode, String notes) throws SQLException {
        String id = UUID.randomUUID().toString();
        String sql = """
            INSERT INTO expenses(id, category, description, amount_cents, expense_date, payment_mode, notes, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, datetime('now'))
        """;
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, category);
            ps.setString(3, description);
            ps.setInt(4, amountCents);
            ps.setString(5, expenseDate);
            ps.setString(6, paymentMode);
            ps.setString(7, notes);
            ps.executeUpdate();
        }
        ActivityLogUtil.log(null, "CREATE", "EXPENSE", id,
                (category != null ? category : "Expense") + " - " + (description != null ? description : "") + " recorded");
        return id;
    }

    public static List<Expense> getExpensesInRange(String startDate, String endDate) throws SQLException {
        String sql = """
            SELECT * FROM expenses
            WHERE expense_date BETWEEN ? AND ?
            ORDER BY expense_date DESC, created_at DESC
        """;
        List<Expense> list = new ArrayList<>();
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, startDate);
            ps.setString(2, endDate);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(extract(rs));
        }
        return list;
    }

    public static int getTotalExpensesCentsInRange(String startDate, String endDate) throws SQLException {
        String sql = "SELECT COALESCE(SUM(amount_cents),0) FROM expenses WHERE expense_date BETWEEN ? AND ?";
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, startDate);
            ps.setString(2, endDate);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    public static void deleteExpense(String id) throws SQLException {
        String sql = "DELETE FROM expenses WHERE id = ?";
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        }
        ActivityLogUtil.log(null, "DELETE", "EXPENSE", id, "Expense entry deleted");
    }

    private static Expense extract(ResultSet rs) throws SQLException {
        Expense e = new Expense();
        e.setId(rs.getString("id"));
        e.setCategory(rs.getString("category"));
        e.setDescription(rs.getString("description"));
        e.setAmountCents(rs.getInt("amount_cents"));
        e.setExpenseDate(rs.getString("expense_date"));
        e.setPaymentMode(rs.getString("payment_mode"));
        e.setNotes(rs.getString("notes"));
        e.setCreatedAt(rs.getString("created_at"));
        return e;
    }
}