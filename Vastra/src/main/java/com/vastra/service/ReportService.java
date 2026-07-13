package com.vastra.service;

import com.vastra.dao.ExpenseDAO;
import com.vastra.dao.ProductDAO;
import com.vastra.dao.SalesDAO;
import com.vastra.model.DailySalesRow;
import com.vastra.model.Expense;
import com.vastra.model.Product;
import com.vastra.model.StockValuationRow;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Backs the Reports screen. Pulls from the existing DAOs and shapes the
 * results into plain model lists the UI can bind straight to a TableView.
 */
public class ReportService {

    public static List<DailySalesRow> getDailySales(String startDate, String endDate) throws Exception {
        List<DailySalesRow> rows = new ArrayList<>();
        ResultSet rs = SalesDAO.getSalesInRange(startDate, endDate);
        while (rs.next()) {
            DailySalesRow row = new DailySalesRow();
            row.setDate(rs.getString("sale_date"));
            row.setNumSales(rs.getInt("num_sales"));
            row.setRevenueCents(rs.getInt("total_revenue_cents"));
            row.setTaxCents(rs.getInt("total_tax_cents"));
            row.setDiscountCents(rs.getInt("total_discount_cents"));
            rows.add(row);
        }
        return rows;
    }

    public static List<StockValuationRow> getStockValuation() throws Exception {
        List<StockValuationRow> rows = new ArrayList<>();
        List<Product> products = ProductDAO.getAllProducts();
        for (Product p : products) {
            rows.add(new StockValuationRow(
                    p.getFullDisplayName(), p.getCategory(), p.getStock(),
                    p.getPurchasePriceCents(), p.getSellPriceCents()
            ));
        }
        return rows;
    }

    public static List<Expense> getExpenses(String startDate, String endDate) throws Exception {
        return ExpenseDAO.getExpensesInRange(startDate, endDate);
    }

    public static int getTotalExpensesCents(String startDate, String endDate) throws Exception {
        return ExpenseDAO.getTotalExpensesCentsInRange(startDate, endDate);
    }
}