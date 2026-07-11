package com.vastra.model;

public class SaleItem {
    private String id;
    private String saleId;
    private String productId;
    private String productName;
    private String productVariant;
    private int qty;
    private int unitPriceCents;
    private int lineTotalCents;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSaleId() { return saleId; }
    public void setSaleId(String saleId) { this.saleId = saleId; }
    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getProductVariant() { return productVariant; }
    public void setProductVariant(String productVariant) { this.productVariant = productVariant; }
    public int getQty() { return qty; }
    public void setQty(int qty) { this.qty = qty; }
    public int getUnitPriceCents() { return unitPriceCents; }
    public void setUnitPriceCents(int unitPriceCents) { this.unitPriceCents = unitPriceCents; }
    public int getLineTotalCents() { return lineTotalCents; }
    public void setLineTotalCents(int lineTotalCents) { this.lineTotalCents = lineTotalCents; }
}