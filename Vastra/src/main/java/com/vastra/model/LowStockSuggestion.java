package com.vastra.model;

/**
 * A low-stock product plus a suggested reorder quantity, based on how fast
 * it's actually been selling. See ProductDAO.getLowStockWithSuggestions()
 * for how the suggestion is calculated.
 */
public class LowStockSuggestion {
    private final Product product;
    private final int soldLast30Days;
    private final int suggestedReorderQty;

    public LowStockSuggestion(Product product, int soldLast30Days, int suggestedReorderQty) {
        this.product = product;
        this.soldLast30Days = soldLast30Days;
        this.suggestedReorderQty = suggestedReorderQty;
    }

    public Product getProduct() { return product; }
    public int getSoldLast30Days() { return soldLast30Days; }
    public int getSuggestedReorderQty() { return suggestedReorderQty; }
}