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
    private int openingStock;
    private int reorderThreshold;
    private String unit;
    private String description;
    private String imagePath;
    private boolean isActive;
    private String createdAt;
    private String updatedAt;
    private String size;
    private String color;

    // Complete Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getVariant() { return variant; }
    public void setVariant(String variant) { this.variant = variant; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public int getMrpCents() { return mrpCents; }
    public void setMrpCents(int mrpCents) { this.mrpCents = mrpCents; }

    public int getSellPriceCents() { return sellPriceCents; }
    public void setSellPriceCents(int sellPriceCents) { this.sellPriceCents = sellPriceCents; }

    public int getPurchasePriceCents() { return purchasePriceCents; }
    public void setPurchasePriceCents(int purchasePriceCents) { this.purchasePriceCents = purchasePriceCents; }

    public int getGstPercent() { return gstPercent; }
    public void setGstPercent(int gstPercent) { this.gstPercent = gstPercent; }

    public String getHsnCode() { return hsnCode; }
    public void setHsnCode(String hsnCode) { this.hsnCode = hsnCode; }

    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }

    /** Quantity on hand that came in without a purchase bill/invoice - informational only, not part of {@link #stock}. */
    public int getOpeningStock() { return openingStock; }
    public void setOpeningStock(int openingStock) { this.openingStock = openingStock; }

    public int getReorderThreshold() { return reorderThreshold; }
    public void setReorderThreshold(int reorderThreshold) { this.reorderThreshold = reorderThreshold; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public String getSize() { return size; }
    public void setSize(String size) { this.size = size; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    // Convenience methods
    public double getMrp() {
        return mrpCents / 100.0;
    }

    public double getSellPrice() {
        return sellPriceCents / 100.0;
    }

    public double getPurchasePrice() {
        return purchasePriceCents / 100.0;
    }

    public String getDisplayName() {
        return variant != null && !variant.isEmpty()
                ? name + " - " + variant
                : name;
    }

    public String getFullDisplayName() {
        StringBuilder sb = new StringBuilder(name);
        if (variant != null && !variant.isEmpty()) {
            sb.append(" - ").append(variant);
        }
        if (brand != null && !brand.isEmpty()) {
            sb.append(" (").append(brand).append(")");
        }
        return sb.toString();
    }

    public double calculateTaxAmount(int quantity) {
        double base = (sellPriceCents * quantity) / 100.0;
        return (base * gstPercent) / (100.0 + gstPercent);
    }

    public double calculateLineTotal(int quantity) {
        return (sellPriceCents * quantity) / 100.0;
    }

    public double getProfitMargin() {
        if (purchasePriceCents == 0) return 0;
        return ((sellPriceCents - purchasePriceCents) * 100.0) / purchasePriceCents;
    }

    public double getProfitAmount() {
        return (sellPriceCents - purchasePriceCents) / 100.0;
    }

    public boolean isLowStock() {
        return stock <= reorderThreshold;
    }

    public boolean isOutOfStock() {
        return stock == 0;
    }
}