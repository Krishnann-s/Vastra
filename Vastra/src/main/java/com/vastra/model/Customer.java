package com.vastra.model;

public class Customer {
    private String id;
    private String name;
    private String phone;
    private String email;
    private String address;
    private String city;
    private String pincode;
    private String birthday;
    private String anniversary;
    private double points;
    private int totalPurchasesCents;
    private int visitCount;
    private String tier;
    private String notes;
    private boolean isActive;
    private String createdAt;
    private String lastVisit;

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getPincode() { return pincode; }
    public void setPincode(String pincode) { this.pincode = pincode; }

    public String getBirthday() { return birthday; }
    public void setBirthday(String birthday) { this.birthday = birthday; }

    public String getAnniversary() { return anniversary; }
    public void setAnniversary(String anniversary) { this.anniversary = anniversary; }

    /** Loyalty points balance. Kept fractional (e.g. 66.09) to match exact 1% calculations - not rounded. */
    public double getPoints() { return points; }
    public void setPoints(double points) { this.points = points; }

    public int getTotalPurchasesCents() { return totalPurchasesCents; }
    public void setTotalPurchasesCents(int totalPurchasesCents) { this.totalPurchasesCents = totalPurchasesCents; }

    public int getVisitCount() { return visitCount; }
    public void setVisitCount(int visitCount) { this.visitCount = visitCount; }

    public String getTier() { return tier; }
    public void setTier(String tier) { this.tier = tier; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getLastVisit() { return lastVisit; }
    public void setLastVisit(String lastVisit) { this.lastVisit = lastVisit; }

    // Convenience methods
    public double getAvailableDiscount() {
        return points; // 1 point = 1 rupee discount
    }

    public double getTotalPurchases() {
        return totalPurchasesCents / 100.0;
    }

    public String getFullAddress() {
        StringBuilder sb = new StringBuilder();
        if (address != null && !address.isEmpty()) {
            sb.append(address);
        }
        if (city != null && !city.isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(city);
        }
        if (pincode != null && !pincode.isEmpty()) {
            if (sb.length() > 0) sb.append(" - ");
            sb.append(pincode);
        }
        return sb.toString();
    }
}