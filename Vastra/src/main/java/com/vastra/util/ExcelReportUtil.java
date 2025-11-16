package com.vastra.util;

import com.vastra.dao.ProductDAO;
import com.vastra.dao.SalesDAO;
import com.vastra.model.Product;
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
}