package com.vastra.model;

/**
 * A queued item for label printing: a product plus how many copies of its
 * label should be printed (e.g. print 5 labels because 5 units came in stock).
 */
public class LabelPrintItem {
    private Product product;
    private int quantity;

    public LabelPrintItem(Product product, int quantity) {
        this.product = product;
        this.quantity = quantity;
    }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) {
        this.quantity = Math.max(1, quantity);
    }
}