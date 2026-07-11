package com.vastra.util;

import com.vastra.model.CartItem;
import com.vastra.model.SaleReceiptData;
import com.vastra.dao.StoreSettingsDAO;

import java.awt.Desktop;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Opens a wa.me link with a pre-filled bill summary. Nothing is sent
 * automatically - it opens WhatsApp Desktop (or WhatsApp Web in the browser
 * if the desktop app isn't installed) with the message typed in; the
 * cashier or customer still taps Send themselves.
 */
public class WhatsAppUtil {

    public static void shareBill(String rawPhone, SaleReceiptData receipt) throws Exception {
        String phone = normalizePhone(rawPhone);
        String message = buildMessage(receipt); // now throws SQLException, covered by "throws Exception" above
        String url = "https://wa.me/" + phone + "?text=" + URLEncoder.encode(message, StandardCharsets.UTF_8);

        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(new URI(url));
        } else {
            throw new UnsupportedOperationException("Opening a browser isn't supported on this system.");
        }
    }

    /** Strips spaces/dashes; assumes +91 (India) if a bare 10-digit number is given. */
    private static String normalizePhone(String rawPhone) {
        String digits = rawPhone.replaceAll("[^0-9+]", "");
        if (digits.startsWith("+")) return digits.substring(1);
        if (digits.length() == 10) return "91" + digits;
        return digits;
    }

    private static String buildMessage(SaleReceiptData receipt) throws java.sql.SQLException{
        StringBuilder sb = new StringBuilder();
        String storeName = StoreSettingsDAO.get("store_name", "Vastra Store");

        sb.append("*").append(storeName).append("*\n");
        sb.append("Bill No: ").append(receipt.getBillNumber()).append("\n");
        sb.append("Date: ").append(receipt.getBillDate()).append(" ").append(receipt.getBillTime()).append("\n\n");

        for (CartItem item : receipt.getItems()) {
            sb.append(String.format("%s x%d - Rs.%.2f%n",
                    item.getProduct().getName(), item.getQuantity(), item.getLineTotal()));
        }

        sb.append("\nSubtotal: Rs.").append(String.format("%.2f", receipt.getSubtotal())).append("\n");
        sb.append("Tax: Rs.").append(String.format("%.2f", receipt.getTax())).append("\n");
        if (receipt.getDiscount() > 0) {
            sb.append("Discount: Rs.").append(String.format("%.2f", receipt.getDiscount())).append("\n");
        }
        sb.append("*Total: Rs.").append(String.format("%.2f", receipt.getTotal())).append("*\n");
        sb.append("Payment: ").append(receipt.getPaymentMode()).append("\n\n");
        sb.append("Thank you for shopping with us! 🙏");

        return sb.toString();
    }
}