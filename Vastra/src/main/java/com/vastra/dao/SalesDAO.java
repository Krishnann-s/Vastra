package com.vastra.dao;

import com.vastra.model.CartItem;
import com.vastra.model.Customer;
import com.vastra.model.SaleReceiptData;
import com.vastra.util.DBUtil;
import com.vastra.util.SequenceUtil;
import com.vastra.util.TaxBreakupUtil;
import com.vastra.model.Sale;
import com.vastra.model.SaleItem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;

public class SalesDAO {

    /**
     * Completes a sale and returns everything needed to print the receipt.
     *
     * @param customerPointsOpening the customer's points balance BEFORE this
     *                              transaction (capture this in the UI before
     *                              any redemption happens, since redemption
     *                              currently runs as a separate call before
     *                              this method) - pass 0 for walk-in sales.
     * @param pointsRedeemedThisSale how many points were redeemed as part of
     *                               this sale's discount, purely for the
     *                               receipt's closing-balance math - pass 0
     *                               if none were redeemed.
     * @param cashierName            who's ringing up this sale, printed on the receipt.
     */
    public static SaleReceiptData completeSale(List<CartItem> items, String customerId,
                                                double customerPointsOpening, double pointsRedeemedThisSale,
                                                int discountCents, String paymentMode, String cashierName) throws SQLException {
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            conn.setAutoCommit(false);

            // Calculate totals
            int subtotalCents = 0;
            int taxCents = 0;
            for (CartItem item : items) {
                int lineTotal = item.getProduct().getSellPriceCents() * item.getQuantity();
                subtotalCents += lineTotal;
                taxCents += (int) Math.round(item.getTaxAmount() * 100);
            }
            int totalCents = subtotalCents - discountCents;

            // Sequential, human-friendly bill number (e.g. "1572") instead of a timestamp
            long billNumberLong = SequenceUtil.nextValueInTransaction(conn, "next_bill_number", 1001);
            String invoiceNumber = String.valueOf(billNumberLong);

            String saleId = UUID.randomUUID().toString();
            LocalDateTime now = LocalDateTime.now();
            String timestamp = now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

            // Insert sale record
            String saleSql = """
                INSERT INTO sales(id, invoice_number, customer_id, cashier_name, ts, subtotal_cents, tax_cents, 
                                  discount_cents, total_cents, payment_mode, status, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'COMPLETED', datetime('now'))
            """;

            try (PreparedStatement ps = conn.prepareStatement(saleSql)) {
                ps.setString(1, saleId);
                ps.setString(2, invoiceNumber);
                ps.setString(3, customerId);
                ps.setString(4, cashierName);
                ps.setString(5, timestamp);
                ps.setInt(6, subtotalCents);
                ps.setInt(7, taxCents);
                ps.setInt(8, discountCents);
                ps.setInt(9, totalCents);
                ps.setString(10, paymentMode);
                ps.executeUpdate();
            }

            // Insert sale items and update stock
            String itemSql = """
                INSERT INTO sale_items(id, sale_id, product_id, product_name, product_variant, 
                                       qty, unit_price_cents, tax_percent, line_total_cents)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

            try (PreparedStatement ps = conn.prepareStatement(itemSql)) {
                for (CartItem item : items) {
                    ps.setString(1, UUID.randomUUID().toString());
                    ps.setString(2, saleId);
                    ps.setString(3, item.getProduct().getId());
                    ps.setString(4, item.getProduct().getName());
                    ps.setString(5, item.getProduct().getVariant() != null ? item.getProduct().getVariant() : "");
                    ps.setInt(6, item.getQuantity());
                    ps.setInt(7, item.getProduct().getSellPriceCents());
                    ps.setInt(8, item.getProduct().getGstPercent());
                    ps.setInt(9, (int) Math.round(item.getLineTotal() * 100));
                    ps.executeUpdate();

                    // Update stock
                    ProductDAO.decrementStock(item.getProduct().getId(), item.getQuantity());
                }
            }

            // Award loyalty points - exact 1% of the total, unrounded (matches your sample: 66.09 on a 6609 bill)
            double pointsEarned = 0;
            if (customerId != null && !customerId.isEmpty()) {
                pointsEarned = totalCents / 10000.0;
                if (pointsEarned > 0) {
                    CustomerDAO.addPoints(customerId, pointsEarned);
                }
            }

            conn.commit();

            // ---- Assemble everything the receipt needs ----
            SaleReceiptData receipt = new SaleReceiptData();
            receipt.setBillNumber(invoiceNumber);
            receipt.setBillDate(now.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            receipt.setBillTime(now.format(DateTimeFormatter.ofPattern("hh:mm:ss a")));
            receipt.setCashierName(cashierName != null && !cashierName.isBlank() ? cashierName : "Admin");
            receipt.setCounterName(StoreSettingsDAO.get("counter_name", "Serve"));

            receipt.setItems(items);
            int qtyTotal = 0;
            for (CartItem item : items) qtyTotal += item.getQuantity();
            receipt.setQtyTotal(qtyTotal);
            receipt.setItemCount(items.size());

            receipt.setSubtotal(subtotalCents / 100.0);
            receipt.setTax(taxCents / 100.0);
            receipt.setDiscount(discountCents / 100.0);
            receipt.setTotal(totalCents / 100.0);

            receipt.setPaymentMode(paymentMode);
            receipt.setCredit(paymentMode != null && paymentMode.equalsIgnoreCase("CREDIT"));

            if (customerId != null && !customerId.isEmpty()) {
                Customer customer = CustomerDAO.findById(customerId);
                receipt.setHasCustomer(true);
                receipt.setCustomerCode(customerId.substring(0, Math.min(8, customerId.length())).toUpperCase());
                receipt.setCustomerName(customer != null ? customer.getName() : "");
                receipt.setCustomerPlace(customer != null ? customer.getCity() : "");
                receipt.setCustomerPhone(customer != null ? customer.getPhone() : "");
                receipt.setPointsOpening(customerPointsOpening);
                receipt.setPointsEarned(pointsEarned);
                receipt.setPointsRedeemed(pointsRedeemedThisSale);
                receipt.setPointsClosing(customerPointsOpening - pointsRedeemedThisSale + pointsEarned);
            } else {
                receipt.setHasCustomer(false);
            }

            receipt.setTaxBreakup(TaxBreakupUtil.computeBreakup(items));

            receipt.setStoreName(StoreSettingsDAO.get("store_name", "Vastra Store"));
            receipt.setStoreAddress(StoreSettingsDAO.get("store_address", ""));
            receipt.setStoreGstin(StoreSettingsDAO.get("store_gstin", ""));
            receipt.setStorePhone(StoreSettingsDAO.get("store_phone", ""));
            receipt.setReceiptFooter(StoreSettingsDAO.get("receipt_footer", "Thank you for shopping with us!"));
            receipt.setExchangePolicy(StoreSettingsDAO.get("exchange_policy", ""));

            return receipt;

        } catch (Exception e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
            throw new SQLException("Sale transaction failed: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    public static Sale findByInvoiceNumber(String invoiceNumber) throws SQLException {
        String sql = """
        SELECT s.*, c.name as customer_name FROM sales s
        LEFT JOIN customers c ON s.customer_id = c.id
        WHERE s.invoice_number = ?
    """;
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, invoiceNumber.trim());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Sale sale = new Sale();
                sale.setId(rs.getString("id"));
                sale.setInvoiceNumber(rs.getString("invoice_number"));
                sale.setCustomerId(rs.getString("customer_id"));
                sale.setCustomerName(rs.getString("customer_name"));
                sale.setTs(rs.getString("ts"));
                sale.setTotalCents(rs.getInt("total_cents"));
                sale.setPaymentMode(rs.getString("payment_mode"));
                return sale;
            }
        }
        return null;
    }

    public static List<SaleItem> getSaleItems(String saleId) throws SQLException {
        String sql = "SELECT * FROM sale_items WHERE sale_id = ?";
        List<SaleItem> list = new ArrayList<>();
        try (Connection c = DBUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, saleId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                SaleItem item = new SaleItem();
                item.setId(rs.getString("id"));
                item.setSaleId(rs.getString("sale_id"));
                item.setProductId(rs.getString("product_id"));
                item.setProductName(rs.getString("product_name"));
                item.setProductVariant(rs.getString("product_variant"));
                item.setQty(rs.getInt("qty"));
                item.setUnitPriceCents(rs.getInt("unit_price_cents"));
                item.setLineTotalCents(rs.getInt("line_total_cents"));
                list.add(item);
            }
        }
        return list;
    }

    public static ResultSet getDailySalesReport(String date) throws SQLException {
        String sql = """
            SELECT s.*, c.name as customer_name, c.phone as customer_phone
            FROM sales s
            LEFT JOIN customers c ON s.customer_id = c.id
            WHERE DATE(s.ts) = ?
            ORDER BY s.ts DESC
        """;

        Connection c = DBUtil.getConnection();
        PreparedStatement ps = c.prepareStatement(sql);
        ps.setString(1, date);
        return ps.executeQuery();
    }

    public static ResultSet getSalesInRange(String startDate, String endDate) throws SQLException {
        String sql = """
            SELECT DATE(ts) as sale_date, 
                   COUNT(*) as num_sales,
                   SUM(total_cents) as total_revenue_cents,
                   SUM(tax_cents) as total_tax_cents,
                   SUM(discount_cents) as total_discount_cents
            FROM sales
            WHERE DATE(ts) BETWEEN ? AND ?
            GROUP BY DATE(ts)
            ORDER BY sale_date DESC
        """;

        Connection c = DBUtil.getConnection();
        PreparedStatement ps = c.prepareStatement(sql);
        ps.setString(1, startDate);
        ps.setString(2, endDate);
        return ps.executeQuery();
    }

    /**
     * Individual (non-aggregated) sale records within a date range - used by
     * the yearly Excel archive export so every transaction is listed, not
     * just the daily totals that getSalesInRange() returns.
     */
    public static ResultSet getSalesDetailedInRange(String startDate, String endDate) throws SQLException {
        String sql = """
            SELECT s.*, c.name as customer_name, c.phone as customer_phone
            FROM sales s
            LEFT JOIN customers c ON s.customer_id = c.id
            WHERE DATE(s.ts) BETWEEN ? AND ?
            ORDER BY s.ts DESC
        """;

        Connection c = DBUtil.getConnection();
        PreparedStatement ps = c.prepareStatement(sql);
        ps.setString(1, startDate);
        ps.setString(2, endDate);
        return ps.executeQuery();
    }

    /**
     * Every individual line item sold within a date range - used by the
     * yearly Excel archive export (a "Sale Items" sheet, one row per product sold).
     */
    public static ResultSet getSaleItemsInRange(String startDate, String endDate) throws SQLException {
        String sql = """
            SELECT si.*, s.ts, s.invoice_number
            FROM sale_items si
            JOIN sales s ON si.sale_id = s.id
            WHERE DATE(s.ts) BETWEEN ? AND ?
            ORDER BY s.ts DESC
        """;

        Connection c = DBUtil.getConnection();
        PreparedStatement ps = c.prepareStatement(sql);
        ps.setString(1, startDate);
        ps.setString(2, endDate);
        return ps.executeQuery();
    }
}