package com.vastra.util;

import com.vastra.model.CartItem;
import com.vastra.model.SplitPayment;

import java.util.List;

/**
 * The actual money math behind completing a sale, pulled out of SalesDAO so
 * it can be unit tested without touching a database. SalesDAO calls into
 * this; nothing here talks to SQL.
 */
public class SaleCalculator {

    /** Everything SalesDAO needs to know about a cart's totals before it writes anything to the DB. */
    public static class Totals {
        public final int subtotalCents;      // gross, before any discount
        public final int taxCents;           // tax embedded in the (tax-inclusive) prices
        public final int itemDiscountCents;  // sum of each cart line's own discount
        public final int billDiscountCents;  // the additional bill-level discount typed in at checkout
        public final int totalDiscountCents; // item + bill discounts combined
        public final int totalCents;         // what the customer actually owes

        public Totals(int subtotalCents, int taxCents, int itemDiscountCents, int billDiscountCents) {
            this.subtotalCents = subtotalCents;
            this.taxCents = taxCents;
            this.itemDiscountCents = itemDiscountCents;
            this.billDiscountCents = billDiscountCents;
            this.totalDiscountCents = itemDiscountCents + billDiscountCents;
            this.totalCents = subtotalCents - this.totalDiscountCents;
        }
    }

    /**
     * Sums a cart into subtotal/tax/discount/total. Mirrors exactly what
     * SalesDAO.completeSale() used to compute inline.
     */
    public static Totals computeTotals(List<CartItem> items, int billDiscountCents) {
        int subtotalCents = 0;
        int taxCents = 0;
        int itemDiscountCents = 0;
        for (CartItem item : items) {
            int lineTotal = item.getProduct().getSellPriceCents() * item.getQuantity();
            subtotalCents += lineTotal;
            taxCents += (int) Math.round(item.getTaxAmount() * 100);
            itemDiscountCents += item.getDiscountCents();
        }
        return new Totals(subtotalCents, taxCents, itemDiscountCents, billDiscountCents);
    }

    /**
     * True if the payment lines are a valid way to settle a sale of totalCents:
     * either a single CREDIT line (pay later, no amount check needed), or a
     * set of lines whose amounts add up exactly to the total.
     */
    public static boolean isSplitValid(List<SplitPayment> payments, int totalCents) {
        if (payments == null || payments.isEmpty()) return false;
        if (payments.size() == 1 && "CREDIT".equalsIgnoreCase(payments.get(0).getMode())) return true;
        int sum = 0;
        for (SplitPayment p : payments) sum += p.getAmountCents();
        return sum == totalCents;
    }

    /** What goes in sales.payment_mode: the plain mode for a single payment, "SPLIT" for more than one. */
    public static String overallPaymentMode(List<SplitPayment> payments) {
        if (payments.size() == 1) return payments.get(0).getMode();
        return "SPLIT";
    }

    /** Human-readable breakdown for the receipt, e.g. "CASH ₹300.00 + UPI ₹200.00". */
    public static String paymentDisplay(List<SplitPayment> payments) {
        if (payments.size() == 1) return payments.get(0).getMode();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < payments.size(); i++) {
            if (i > 0) sb.append(" + ");
            SplitPayment p = payments.get(i);
            sb.append(p.getMode()).append(" \u20B9").append(String.format("%.2f", p.getAmount()));
        }
        return sb.toString();
    }
}