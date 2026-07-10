package com.vastra.model;

public class TaxBreakupRow {
    private double taxPercent;
    private double taxableAmount; // the "Amt" column - value excluding tax
    private double gstAmount;     // sgst + cgst combined
    private double sgstAmount;
    private double cgstAmount;

    public TaxBreakupRow(double taxPercent, double taxableAmount, double gstAmount, double sgstAmount, double cgstAmount) {
        this.taxPercent = taxPercent;
        this.taxableAmount = taxableAmount;
        this.gstAmount = gstAmount;
        this.sgstAmount = sgstAmount;
        this.cgstAmount = cgstAmount;
    }

    public double getTaxPercent() { return taxPercent; }
    public double getTaxableAmount() { return taxableAmount; }
    public double getGstAmount() { return gstAmount; }
    public double getSgstAmount() { return sgstAmount; }
    public double getCgstAmount() { return cgstAmount; }
}