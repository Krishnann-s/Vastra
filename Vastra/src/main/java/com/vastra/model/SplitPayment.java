package com.vastra.model;

/** One payment line on a sale - e.g. "CASH ₹300". A sale can have 1-3 of these when split. */
public class SplitPayment {
    private final String mode;
    private final int amountCents;

    public SplitPayment(String mode, int amountCents) {
        this.mode = mode;
        this.amountCents = amountCents;
    }

    public String getMode() { return mode; }
    public int getAmountCents() { return amountCents; }
    public double getAmount() { return amountCents / 100.0; }
}
