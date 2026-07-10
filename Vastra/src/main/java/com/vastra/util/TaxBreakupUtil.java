package com.vastra.util;

import com.vastra.model.CartItem;
import com.vastra.model.TaxBreakupRow;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class TaxBreakupUtil {

    /**
     * Groups items by GST%, sums their (tax-inclusive) subtotal per rate,
     * then extracts the tax component the same way Product.calculateTaxAmount()
     * already does: tax = inclusiveTotal * rate / (100 + rate).
     */
    public static List<TaxBreakupRow> computeBreakup(List<CartItem> items) {
        Map<Integer, Double> inclusiveTotalByRate = new TreeMap<>();
        for (CartItem item : items) {
            int rate = item.getProduct().getGstPercent();
            inclusiveTotalByRate.merge(rate, item.getSubtotal(), Double::sum);
        }

        List<TaxBreakupRow> rows = new ArrayList<>();
        for (Map.Entry<Integer, Double> entry : inclusiveTotalByRate.entrySet()) {
            int rate = entry.getKey();
            double inclusiveTotal = entry.getValue();
            double taxAmount = rate == 0 ? 0.0 : (inclusiveTotal * rate) / (100.0 + rate);
            double taxableValue = inclusiveTotal - taxAmount;
            double half = taxAmount / 2.0;
            rows.add(new TaxBreakupRow(rate, taxableValue, taxAmount, half, half));
        }
        return rows;
    }
}