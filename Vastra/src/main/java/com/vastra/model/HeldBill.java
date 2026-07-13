package com.vastra.model;

import java.util.List;

/** A cart that was set aside mid-sale (Hold Sale) so it can be resumed later. */
public class HeldBill {
    private String id;
    private String label;
    private String customerId;
    private String customerName;
    private List<CartItem> items;
    private int discountCents;
    private String heldBy;
    private String createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public List<CartItem> getItems() { return items; }
    public void setItems(List<CartItem> items) { this.items = items; }

    public int getDiscountCents() { return discountCents; }
    public void setDiscountCents(int discountCents) { this.discountCents = discountCents; }
    public double getDiscount() { return discountCents / 100.0; }

    public String getHeldBy() { return heldBy; }
    public void setHeldBy(String heldBy) { this.heldBy = heldBy; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public int getItemCount() { return items != null ? items.size() : 0; }

    /** Estimated total at today's prices (item discounts + the bill-level discount that was set when held). */
    public double getEstimatedTotal() {
        double subtotal = 0;
        if (items != null) {
            for (CartItem item : items) subtotal += item.getLineTotal();
        }
        return subtotal - getDiscount();
    }
}