package com.vastra.model;

/** One aggregated row (one calendar day) in the Daily Sales report. */
public class DailySalesRow {
    private String date;
    private int numSales;
    private int revenueCents;
    private int taxCents;
    private int discountCents;

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public int getNumSales() { return numSales; }
    public void setNumSales(int numSales) { this.numSales = numSales; }

    public int getRevenueCents() { return revenueCents; }
    public void setRevenueCents(int revenueCents) { this.revenueCents = revenueCents; }
    public double getRevenue() { return revenueCents / 100.0; }

    public int getTaxCents() { return taxCents; }
    public void setTaxCents(int taxCents) { this.taxCents = taxCents; }
    public double getTax() { return taxCents / 100.0; }

    public int getDiscountCents() { return discountCents; }
    public void setDiscountCents(int discountCents) { this.discountCents = discountCents; }
    public double getDiscount() { return discountCents / 100.0; }
}