package com.vastra.model;

public class CustomerLedgerEntry {
    public enum Type { SALE, PAYMENT }

    private String date;
    private Type type;
    private String refNo;
    private String description;
    private int debitCents;   // increases what the customer owes (a credit sale)
    private int creditCents;  // decreases what the customer owes (a payment received)
    private int runningBalanceCents;

    public CustomerLedgerEntry(String date, Type type, String refNo, String description,
                                int debitCents, int creditCents, int runningBalanceCents) {
        this.date = date;
        this.type = type;
        this.refNo = refNo;
        this.description = description;
        this.debitCents = debitCents;
        this.creditCents = creditCents;
        this.runningBalanceCents = runningBalanceCents;
    }

    public String getDate() { return date; }
    public Type getType() { return type; }
    public String getRefNo() { return refNo; }
    public String getDescription() { return description; }
    public int getDebitCents() { return debitCents; }
    public double getDebit() { return debitCents / 100.0; }
    public int getCreditCents() { return creditCents; }
    public double getCredit() { return creditCents / 100.0; }
    public int getRunningBalanceCents() { return runningBalanceCents; }
    public double getRunningBalance() { return runningBalanceCents / 100.0; }
}