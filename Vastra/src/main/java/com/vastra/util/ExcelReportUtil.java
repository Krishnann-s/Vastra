package com.vastra.util;

import com.vastra.dao.CustomerDAO;
import com.vastra.dao.ProductDAO;
import com.vastra.dao.PurchaseDAO;
import com.vastra.dao.SalesDAO;
import com.vastra.dao.SupplierDAO;
import com.vastra.dao.SupplierPaymentDAO;
import com.vastra.model.Customer;
import com.vastra.model.Product;
import com.vastra.model.Supplier;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;

public class ExcelReportUtil {

    public static void generateDailySalesReport(String date, String filepath) throws Exception {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Daily Sales - " + date);

        // Create header style
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        // Header row
        Row headerRow = sheet.createRow(0);
        String[] headers = {"Sale ID", "Time", "Customer", "Phone", "Subtotal", "Tax", "Discount", "Total", "Payment"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Data rows
        ResultSet rs = SalesDAO.getDailySalesReport(date);
        int rowNum = 1;
        double totalRevenue = 0;

        while (rs.next()) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(rs.getString("id"));
            row.createCell(1).setCellValue(rs.getString("ts"));
            row.createCell(2).setCellValue(rs.getString("customer_name"));
            row.createCell(3).setCellValue(rs.getString("customer_phone"));
            row.createCell(4).setCellValue(rs.getInt("subtotal_cents") / 100.0);
            row.createCell(5).setCellValue(rs.getInt("tax_cents") / 100.0);
            row.createCell(6).setCellValue(rs.getInt("discount_cents") / 100.0);
            row.createCell(7).setCellValue(rs.getInt("total_cents") / 100.0);
            row.createCell(8).setCellValue(rs.getString("payment_mode"));

            totalRevenue += rs.getInt("total_cents") / 100.0;
        }

        // Summary row
        Row summaryRow = sheet.createRow(rowNum + 1);
        Cell summaryLabel = summaryRow.createCell(6);
        summaryLabel.setCellValue("TOTAL REVENUE:");
        summaryLabel.setCellStyle(headerStyle);

        Cell summaryValue = summaryRow.createCell(7);
        summaryValue.setCellValue(totalRevenue);
        CellStyle currencyStyle = workbook.createCellStyle();
        currencyStyle.setFont(headerFont);
        summaryValue.setCellStyle(currencyStyle);

        // Auto-size columns
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }

        // Write to file
        try (FileOutputStream fos = new FileOutputStream(filepath)) {
            workbook.write(fos);
        }
        workbook.close();
    }

    public static void generateInventoryReport(String filepath) throws Exception {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Inventory Report");

        // Header style
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        // Header
        Row headerRow = sheet.createRow(0);
        String[] headers = {"Product ID", "Name", "Variant", "MRP", "Sell Price", "GST%", "Stock", "Status"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Data
        List<Product> products = ProductDAO.getAllProducts();
        int rowNum = 1;

        CellStyle lowStockStyle = workbook.createCellStyle();
        lowStockStyle.setFillForegroundColor(IndexedColors.ROSE.getIndex());
        lowStockStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        for (Product p : products) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(p.getId());
            row.createCell(1).setCellValue(p.getName());
            row.createCell(2).setCellValue(p.getVariant());
            row.createCell(3).setCellValue(p.getMrpCents());
            row.createCell(4).setCellValue(p.getSellPrice());
            row.createCell(5).setCellValue(p.getGstPercent());

            Cell stockCell = row.createCell(6);
            stockCell.setCellValue(p.getStock());

            Cell statusCell = row.createCell(7);
            if (p.getStock() <= p.getReorderThreshold()) {
                statusCell.setCellValue("LOW STOCK");
                stockCell.setCellStyle(lowStockStyle);
                statusCell.setCellStyle(lowStockStyle);
            } else if (p.getStock() == 0) {
                statusCell.setCellValue("OUT OF STOCK");
                stockCell.setCellStyle(lowStockStyle);
                statusCell.setCellStyle(lowStockStyle);
            } else {
                statusCell.setCellValue("OK");
            }
        }

        // Auto-size
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }

        try (FileOutputStream fos = new FileOutputStream(filepath)) {
            workbook.write(fos);
        }
        workbook.close();
    }

    public static void generateStockValuationReport(String filepath) throws Exception {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Stock Valuation");

        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        String[] headers = {"Product", "Category", "Stock", "Cost Price", "Sell Price", "Cost Value", "Sell Value", "Potential Profit"};
        writeHeaderRow(sheet, headerStyle, headers);

        List<Product> products = ProductDAO.getAllProducts();
        int rowNum = 1;
        double totalCostValue = 0, totalSellValue = 0;

        for (Product p : products) {
            Row row = sheet.createRow(rowNum++);
            double costValue = (p.getPurchasePriceCents() * (long) p.getStock()) / 100.0;
            double sellValue = (p.getSellPriceCents() * (long) p.getStock()) / 100.0;
            row.createCell(0).setCellValue(p.getFullDisplayName());
            row.createCell(1).setCellValue(p.getCategory());
            row.createCell(2).setCellValue(p.getStock());
            row.createCell(3).setCellValue(p.getPurchasePrice());
            row.createCell(4).setCellValue(p.getSellPrice());
            row.createCell(5).setCellValue(costValue);
            row.createCell(6).setCellValue(sellValue);
            row.createCell(7).setCellValue(sellValue - costValue);
            totalCostValue += costValue;
            totalSellValue += sellValue;
        }

        Row summaryRow = sheet.createRow(rowNum + 1);
        Cell label = summaryRow.createCell(4);
        label.setCellValue("TOTALS:");
        label.setCellStyle(headerStyle);
        summaryRow.createCell(5).setCellValue(totalCostValue);
        summaryRow.createCell(6).setCellValue(totalSellValue);
        summaryRow.createCell(7).setCellValue(totalSellValue - totalCostValue);

        autoSize(sheet, headers.length);

        try (FileOutputStream fos = new FileOutputStream(filepath)) {
            workbook.write(fos);
        }
        workbook.close();
    }

    public static void generateExpenseReport(String startDate, String endDate, String filepath) throws Exception {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Expenses");

        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        String[] headers = {"Date", "Category", "Description", "Amount", "Payment Mode", "Notes"};
        writeHeaderRow(sheet, headerStyle, headers);

        List<com.vastra.model.Expense> expenses = com.vastra.dao.ExpenseDAO.getExpensesInRange(startDate, endDate);
        int rowNum = 1;
        double total = 0;
        for (com.vastra.model.Expense e : expenses) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(e.getExpenseDate());
            row.createCell(1).setCellValue(e.getCategory());
            row.createCell(2).setCellValue(e.getDescription());
            row.createCell(3).setCellValue(e.getAmount());
            row.createCell(4).setCellValue(e.getPaymentMode());
            row.createCell(5).setCellValue(e.getNotes());
            total += e.getAmount();
        }

        Row summaryRow = sheet.createRow(rowNum + 1);
        Cell label = summaryRow.createCell(2);
        label.setCellValue("TOTAL EXPENSES:");
        label.setCellStyle(headerStyle);
        summaryRow.createCell(3).setCellValue(total);

        autoSize(sheet, headers.length);

        try (FileOutputStream fos = new FileOutputStream(filepath)) {
            workbook.write(fos);
        }
        workbook.close();
    }

    public static void generateMonthlyReport(String startDate, String endDate, String filepath) throws Exception {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Monthly Report");

        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);

        Row headerRow = sheet.createRow(0);
        String[] headers = {"Date", "Number of Sales", "Total Revenue", "Total Tax", "Total Discount"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        ResultSet rs = SalesDAO.getSalesInRange(startDate, endDate);
        int rowNum = 1;
        double grandTotal = 0;

        while (rs.next()) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(rs.getString("sale_date"));
            row.createCell(1).setCellValue(rs.getInt("num_sales"));
            row.createCell(2).setCellValue(rs.getInt("total_revenue_cents") / 100.0);
            row.createCell(3).setCellValue(rs.getInt("total_tax_cents") / 100.0);
            row.createCell(4).setCellValue(rs.getInt("total_discount_cents") / 100.0);

            grandTotal += rs.getInt("total_revenue_cents") / 100.0;
        }

        Row summaryRow = sheet.createRow(rowNum + 1);
        summaryRow.createCell(1).setCellValue("GRAND TOTAL:");
        summaryRow.createCell(2).setCellValue(grandTotal);

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }

        try (FileOutputStream fos = new FileOutputStream(filepath)) {
            workbook.write(fos);
        }
        workbook.close();
    }

    /**
     * Full archive export for backup/record-keeping purposes, filterable by
     * year alone (whole year) or year + month (single month). Produces a
     * multi-sheet workbook: Sales, Sale Items, Purchases, Supplier Payments
     * (all filtered to the period), plus Products, Customers and Suppliers
     * as full current snapshots (those don't have a natural "period" - they
     * reflect current state, deletions included since deleted rows are
     * simply excluded by the is_active filter already used in their DAOs).
     *
     * @param month pass null for the whole year, or 1-12 for a single month.
     */
    public static void generateYearlyArchive(int year, Integer month, String filepath) throws Exception {
        LocalDate start;
        LocalDate end;
        String periodLabel;
        if (month != null) {
            start = LocalDate.of(year, month, 1);
            end = start.withDayOfMonth(start.lengthOfMonth());
            periodLabel = year + "-" + String.format("%02d", month);
        } else {
            start = LocalDate.of(year, 1, 1);
            end = LocalDate.of(year, 12, 31);
            periodLabel = String.valueOf(year);
        }
        String startStr = start.toString();
        String endStr = end.toString();

        Workbook workbook = new XSSFWorkbook();

        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        writeSalesSheet(workbook, headerStyle, startStr, endStr, periodLabel);
        writeSaleItemsSheet(workbook, headerStyle, startStr, endStr, periodLabel);
        writePurchasesSheet(workbook, headerStyle, startStr, endStr, periodLabel);
        writeSupplierPaymentsSheet(workbook, headerStyle, startStr, endStr, periodLabel);
        writeProductsSnapshotSheet(workbook, headerStyle);
        writeCustomersSnapshotSheet(workbook, headerStyle);
        writeSuppliersSnapshotSheet(workbook, headerStyle);

        try (FileOutputStream fos = new FileOutputStream(filepath)) {
            workbook.write(fos);
        }
        workbook.close();
    }

    private static void writeHeaderRow(Sheet sheet, CellStyle style, String... headers) {
        Row row = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(style);
        }
    }

    private static void autoSize(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) sheet.autoSizeColumn(i);
    }

    private static void writeSalesSheet(Workbook wb, CellStyle headerStyle, String start, String end, String periodLabel) throws Exception {
        Sheet sheet = wb.createSheet("Sales " + periodLabel);
        String[] headers = {"Invoice", "Date/Time", "Customer", "Phone", "Subtotal", "Tax", "Discount", "Total", "Payment Mode"};
        writeHeaderRow(sheet, headerStyle, headers);

        ResultSet rs = SalesDAO.getSalesDetailedInRange(start, end);
        int rowNum = 1;
        while (rs.next()) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(rs.getString("invoice_number"));
            row.createCell(1).setCellValue(rs.getString("ts"));
            row.createCell(2).setCellValue(rs.getString("customer_name"));
            row.createCell(3).setCellValue(rs.getString("customer_phone"));
            row.createCell(4).setCellValue(rs.getInt("subtotal_cents") / 100.0);
            row.createCell(5).setCellValue(rs.getInt("tax_cents") / 100.0);
            row.createCell(6).setCellValue(rs.getInt("discount_cents") / 100.0);
            row.createCell(7).setCellValue(rs.getInt("total_cents") / 100.0);
            row.createCell(8).setCellValue(rs.getString("payment_mode"));
        }
        autoSize(sheet, headers.length);
    }

    private static void writeSaleItemsSheet(Workbook wb, CellStyle headerStyle, String start, String end, String periodLabel) throws Exception {
        Sheet sheet = wb.createSheet("Sale Items " + periodLabel);
        String[] headers = {"Invoice", "Date/Time", "Product", "Variant", "Qty", "Unit Price", "Line Total"};
        writeHeaderRow(sheet, headerStyle, headers);

        ResultSet rs = SalesDAO.getSaleItemsInRange(start, end);
        int rowNum = 1;
        while (rs.next()) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(rs.getString("invoice_number"));
            row.createCell(1).setCellValue(rs.getString("ts"));
            row.createCell(2).setCellValue(rs.getString("product_name"));
            row.createCell(3).setCellValue(rs.getString("product_variant"));
            row.createCell(4).setCellValue(rs.getInt("qty"));
            row.createCell(5).setCellValue(rs.getInt("unit_price_cents") / 100.0);
            row.createCell(6).setCellValue(rs.getInt("line_total_cents") / 100.0);
        }
        autoSize(sheet, headers.length);
    }

    private static void writePurchasesSheet(Workbook wb, CellStyle headerStyle, String start, String end, String periodLabel) throws Exception {
        Sheet sheet = wb.createSheet("Purchases " + periodLabel);
        String[] headers = {"Invoice #", "Supplier", "Invoice Date", "Due Date", "Total Amount", "Notes"};
        writeHeaderRow(sheet, headerStyle, headers);

        ResultSet rs = PurchaseDAO.getPurchasesInRangeResultSet(start, end);
        int rowNum = 1;
        while (rs.next()) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(rs.getString("invoice_number"));
            row.createCell(1).setCellValue(rs.getString("supplier_name"));
            row.createCell(2).setCellValue(rs.getString("invoice_date"));
            row.createCell(3).setCellValue(rs.getString("due_date"));
            row.createCell(4).setCellValue(rs.getInt("total_amount_cents") / 100.0);
            row.createCell(5).setCellValue(rs.getString("notes"));
        }
        autoSize(sheet, headers.length);
    }

    private static void writeSupplierPaymentsSheet(Workbook wb, CellStyle headerStyle, String start, String end, String periodLabel) throws Exception {
        Sheet sheet = wb.createSheet("Supplier Payments " + periodLabel);
        String[] headers = {"Supplier", "Payment Date", "Amount", "Mode", "Notes"};
        writeHeaderRow(sheet, headerStyle, headers);

        ResultSet rs = SupplierPaymentDAO.getPaymentsInRange(start, end);
        int rowNum = 1;
        while (rs.next()) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(rs.getString("supplier_name"));
            row.createCell(1).setCellValue(rs.getString("payment_date"));
            row.createCell(2).setCellValue(rs.getInt("amount_cents") / 100.0);
            row.createCell(3).setCellValue(rs.getString("payment_mode"));
            row.createCell(4).setCellValue(rs.getString("notes"));
        }
        autoSize(sheet, headers.length);
    }

    private static void writeProductsSnapshotSheet(Workbook wb, CellStyle headerStyle) throws Exception {
        Sheet sheet = wb.createSheet("Products (current)");
        String[] headers = {"Product", "Brand", "Category", "Barcode/SKU", "MRP", "Sell Price", "GST%", "Stock", "Status"};
        writeHeaderRow(sheet, headerStyle, headers);

        List<Product> products = ProductDAO.getAllProducts();
        int rowNum = 1;
        for (Product p : products) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(p.getFullDisplayName());
            row.createCell(1).setCellValue(p.getBrand());
            row.createCell(2).setCellValue(p.getCategory());
            row.createCell(3).setCellValue(p.getSku() != null && !p.getSku().isEmpty() ? p.getSku() : p.getBarcode());
            row.createCell(4).setCellValue(p.getMrp());
            row.createCell(5).setCellValue(p.getSellPrice());
            row.createCell(6).setCellValue(p.getGstPercent());
            row.createCell(7).setCellValue(p.getStock());
            row.createCell(8).setCellValue(p.isOutOfStock() ? "OUT OF STOCK" : (p.isLowStock() ? "LOW STOCK" : "OK"));
        }
        autoSize(sheet, headers.length);
    }

    private static void writeCustomersSnapshotSheet(Workbook wb, CellStyle headerStyle) throws Exception {
        Sheet sheet = wb.createSheet("Customers (current)");
        String[] headers = {"Name", "Phone", "Tier", "Points", "Total Purchases", "Visit Count", "Last Visit"};
        writeHeaderRow(sheet, headerStyle, headers);

        List<Customer> customers = CustomerDAO.getAllCustomers();
        int rowNum = 1;
        for (Customer c : customers) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(c.getName());
            row.createCell(1).setCellValue(c.getPhone());
            row.createCell(2).setCellValue(c.getTier());
            row.createCell(3).setCellValue(c.getPoints());
            row.createCell(4).setCellValue(c.getTotalPurchases());
            row.createCell(5).setCellValue(c.getVisitCount());
            row.createCell(6).setCellValue(c.getLastVisit());
        }
        autoSize(sheet, headers.length);
    }

    private static void writeSuppliersSnapshotSheet(Workbook wb, CellStyle headerStyle) throws Exception {
        Sheet sheet = wb.createSheet("Suppliers (current)");
        String[] headers = {"Supplier", "Phone", "GST No", "Currently Owed"};
        writeHeaderRow(sheet, headerStyle, headers);

        List<Supplier> suppliers = SupplierDAO.getAllActive();
        int rowNum = 1;
        for (Supplier s : suppliers) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(s.getName());
            row.createCell(1).setCellValue(s.getPhone());
            row.createCell(2).setCellValue(s.getGstNo());
            row.createCell(3).setCellValue(SupplierDAO.getCurrentDueCents(s.getId()) / 100.0);
        }
        autoSize(sheet, headers.length);
    }
}