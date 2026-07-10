package com.vastra.model;

public class PayableReminder {
    private String supplierId;
    private String supplierName;
    private String nextDueDate; // ISO date string, e.g. "2026-07-15"
    private int amountDueCents; // supplier's total current due
    private boolean overdue;
    private long daysDiff; // days overdue (positive) or days until due (negative), 0 = due today

    public PayableReminder(String supplierId, String supplierName, String nextDueDate,
                            int amountDueCents, boolean overdue, long daysDiff) {
        this.supplierId = supplierId;
        this.supplierName = supplierName;
        this.nextDueDate = nextDueDate;
        this.amountDueCents = amountDueCents;
        this.overdue = overdue;
        this.daysDiff = daysDiff;
    }

    public String getSupplierId() { return supplierId; }
    public String getSupplierName() { return supplierName; }
    public String getNextDueDate() { return nextDueDate; }
    public int getAmountDueCents() { return amountDueCents; }
    public double getAmountDue() { return amountDueCents / 100.0; }
    public boolean isOverdue() { return overdue; }
    public long getDaysDiff() { return daysDiff; }
}