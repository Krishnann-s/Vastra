package com.vastra.model;

public class Supplier {
    private String id;
    private String name;
    private String phone;
    private String address;
    private String gstNo;
    private int openingBalanceCents; // what you owed them when you started tracking in this software
    private String notes;
    private boolean active;
    private String createdAt;

    // Not persisted - populated by SupplierDAO.getCurrentDue() when listing suppliers
    private int dueCents;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getGstNo() { return gstNo; }
    public void setGstNo(String gstNo) { this.gstNo = gstNo; }

    public int getOpeningBalanceCents() { return openingBalanceCents; }
    public void setOpeningBalanceCents(int openingBalanceCents) { this.openingBalanceCents = openingBalanceCents; }
    public double getOpeningBalance() { return openingBalanceCents / 100.0; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public int getDueCents() { return dueCents; }
    public void setDueCents(int dueCents) { this.dueCents = dueCents; }
    public double getDue() { return dueCents / 100.0; }
}