package com.vastra.model;

/** One product's ranking in the Top Selling Products dashboard widget. */
public class TopProductRow {
    private final String productName;
    private final int qtySold;
    private final int revenueCents;

    public TopProductRow(String productName, int qtySold, int revenueCents) {
        this.productName = productName;
        this.qtySold = qtySold;
        this.revenueCents = revenueCents;
    }

    public String getProductName() { return productName; }
    public int getQtySold() { return qtySold; }
    public double getRevenue() { return revenueCents / 100.0; }
}