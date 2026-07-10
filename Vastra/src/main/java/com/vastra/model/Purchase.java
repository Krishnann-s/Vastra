package com.vastra.model;

public class Purchase {
    private String id;
    private String supplierId;
    private String supplierName; // convenience field, joined in from suppliers table
    private String invoiceNumber;
    private String invoiceDate;
    private int totalAmountCents;
    private String dueDate;
    private String notes;
    private String createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSupplierId() { return supplierId; }
    public void setSupplierId(String supplierId) { this.supplierId = supplierId; }

    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }

    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }

    public String getInvoiceDate() { return invoiceDate; }
    public void setInvoiceDate(String invoiceDate) { this.invoiceDate = invoiceDate; }

    public int getTotalAmountCents() { return totalAmountCents; }
    public void setTotalAmountCents(int totalAmountCents) { this.totalAmountCents = totalAmountCents; }
    public double getTotalAmount() { return totalAmountCents / 100.0; }

    public String getDueDate() { return dueDate; }
    public void setDueDate(String dueDate) { this.dueDate = dueDate; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}