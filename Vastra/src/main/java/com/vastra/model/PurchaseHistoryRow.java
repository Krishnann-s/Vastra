package com.vastra.model;

/** One item-level row in a customer's purchase history (as opposed to the money-only ledger). */
public class PurchaseHistoryRow {
    private String date;
    private String billNumber;
    private String productName;
    private String variant;
    private int quantity;
    private int unitPriceCents;
    private int lineTotalCents;

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getBillNumber() { return billNumber; }
    public void setBillNumber(String billNumber) { this.billNumber = billNumber; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getVariant() { return variant; }
    public void setVariant(String variant) { this.variant = variant; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public int getUnitPriceCents() { return unitPriceCents; }
    public void setUnitPriceCents(int unitPriceCents) { this.unitPriceCents = unitPriceCents; }
    public double getUnitPrice() { return unitPriceCents / 100.0; }

    public int getLineTotalCents() { return lineTotalCents; }
    public void setLineTotalCents(int lineTotalCents) { this.lineTotalCents = lineTotalCents; }
    public double getLineTotal() { return lineTotalCents / 100.0; }
}