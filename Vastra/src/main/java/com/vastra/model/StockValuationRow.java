package com.vastra.model;

/** One product's contribution to the Stock Valuation report. */
public class StockValuationRow {
    private String productName;
    private String category;
    private int stock;
    private int costPriceCents;
    private int sellPriceCents;

    public StockValuationRow(String productName, String category, int stock, int costPriceCents, int sellPriceCents) {
        this.productName = productName;
        this.category = category;
        this.stock = stock;
        this.costPriceCents = costPriceCents;
        this.sellPriceCents = sellPriceCents;
    }

    public String getProductName() { return productName; }
    public String getCategory() { return category; }
    public int getStock() { return stock; }

    public double getCostPrice() { return costPriceCents / 100.0; }
    public double getSellPrice() { return sellPriceCents / 100.0; }

    /** Total value of what's on the shelf, at what you paid for it. */
    public double getCostValue() { return (costPriceCents * (long) stock) / 100.0; }

    /** Total value of what's on the shelf, at what you'd sell it for. */
    public double getSellValue() { return (sellPriceCents * (long) stock) / 100.0; }

    /** Profit locked up in current stock, if it all sells at the current price. */
    public double getPotentialProfit() { return getSellValue() - getCostValue(); }
}