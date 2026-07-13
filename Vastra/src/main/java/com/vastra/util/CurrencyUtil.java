package com.vastra.util;

import com.vastra.dao.StoreSettingsDAO;

/**
 * Reads the currency symbol from Settings (defaults to ₹) so it's consistent
 * everywhere it's displayed, instead of every screen hardcoding "₹" itself.
 * Change it once in Settings > Billing > Currency Symbol and it applies
 * everywhere this is used.
 */
public class CurrencyUtil {

    public static String symbol() {
        try {
            String s = StoreSettingsDAO.get("currency_symbol", "\u20B9");
            return (s == null || s.isBlank()) ? "\u20B9" : s;
        } catch (Exception e) {
            e.printStackTrace();
            return "\u20B9";
        }
    }
}