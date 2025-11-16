package com.vastra.model;

public class Product {
    private String id;
    private String name;
    private String variant;
    private String category;
    private String brand;
    private String barcode;
    private String sku;
    private int mrpCents;
    private int sellPriceCents;
    private int purchasePriceCents;
    private int gstPercent;
    private String hsnCode;
    private int stock;
    private int reorderThreshold;
    private String unit;
    private String description;
    private String imagePath;
    private boolean isActive;
    private String createdAt;
    private String updatedAt;

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getVariant() { return variant; }
    public void setVariant(String variant) { this.variant = variant; }

    public int getMrpCents() { return mrpCents; }
    public void setMrpCents(int mrpCents) { this.mrpCents = mrpCents; }

    public int getSellPriceCents() { return sellPriceCents; }
    public void setSellPriceCents(int sellPriceCents) { this.sellPriceCents = sellPriceCents; }

    public int getGstPercent() { return gstPercent; }
    public void setGstPercent(int gstPercent) { this.gstPercent = gstPercent; }

    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }

    public int getReorderThreshold() { return reorderThreshold; }
    public void setReorderThreshold(int reorderThreshold) { this.reorderThreshold = reorderThreshold; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    // Convenience methods
    public double getMrp() {
        return mrpCents / 100.0;
    }

    public double getSellPrice() {
        return sellPriceCents / 100.0;
    }

    public String getDisplayName() {
        return variant != null && !variant.isEmpty()
                ? name + " - " + variant
                : name;
    }

    public double calculateTaxAmount(int quantity) {
        double base = (sellPriceCents * quantity) / 100.0;
        return (base * gstPercent) / (100.0 + gstPercent);
    }

    public double calculateLineTotal(int quantity) {
        return (sellPriceCents * quantity) / 100.0;
    }
}