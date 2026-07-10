package com.vastra.model;

public class PurchaseItem {
    private String id;
    private String purchaseId;
    private String productId;
    private String productName;
    private int qty;
    private int costPriceCents;
    private int lineTotalCents;

    // Not persisted - kept for convenience while building the entry screen
    private transient Product product;

    public PurchaseItem() {}

    public PurchaseItem(Product product, int qty, int costPriceCents) {
        this.product = product;
        this.productId = product.getId();
        this.productName = product.getFullDisplayName();
        this.qty = qty;
        this.costPriceCents = costPriceCents;
        recomputeLineTotal();
    }

    public void recomputeLineTotal() {
        this.lineTotalCents = qty * costPriceCents;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPurchaseId() { return purchaseId; }
    public void setPurchaseId(String purchaseId) { this.purchaseId = purchaseId; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public int getQty() { return qty; }
    public void setQty(int qty) { this.qty = Math.max(1, qty); recomputeLineTotal(); }

    public int getCostPriceCents() { return costPriceCents; }
    public void setCostPriceCents(int costPriceCents) { this.costPriceCents = Math.max(0, costPriceCents); recomputeLineTotal(); }
    public double getCostPrice() { return costPriceCents / 100.0; }

    public int getLineTotalCents() { return lineTotalCents; }
    public double getLineTotal() { return lineTotalCents / 100.0; }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
}