package com.vastra.util;

import com.vastra.model.CartItem;
import com.vastra.model.SaleReceiptData;
import com.vastra.model.TaxBreakupRow;
import javafx.print.*;
import javafx.scene.Node;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.geometry.Insets;
import javafx.geometry.Pos;

import java.util.List;

/**
 * Prints the sales receipt on a thermal printer (58mm or 80mm, set in
 * Settings > Billing > Receipt Paper Width). The layout mirrors a standard
 * Indian retail POS bill: store header, bill/cashier info, itemised table,
 * totals, payment breakdown, GST tax breakup, customer & loyalty points,
 * and the shop's own footer/exchange policy text - all pulled from
 * SaleReceiptData, which is itself built from Settings at sale time. Change
 * something in Settings and the next bill printed reflects it automatically.
 */
public class ThermalPrinterUtil {

    private static final Font FONT_NORMAL_58 = Font.font("Monospaced", 8);
    private static final Font FONT_NORMAL_80 = Font.font("Monospaced", 10);
    private static final Font FONT_BOLD_58 = Font.font("Monospaced", FontWeight.BOLD, 9);
    private static final Font FONT_BOLD_80 = Font.font("Monospaced", FontWeight.BOLD, 11);
    private static final Font FONT_TITLE_58 = Font.font("Monospaced", FontWeight.BOLD, 12);
    private static final Font FONT_TITLE_80 = Font.font("Monospaced", FontWeight.BOLD, 16);

    public static boolean printReceipt(SaleReceiptData receipt) {
        VBox node = createReceipt(receipt);
        return printNode(node);
    }

    private static VBox createReceipt(SaleReceiptData receipt) {
        boolean narrow = "58mm".equals(getPaperWidthSetting());
        int receiptWidthPx = narrow ? 220 : 300;
        int lineChars = narrow ? 32 : 42;

        Font normal = narrow ? FONT_NORMAL_58 : FONT_NORMAL_80;
        Font bold = narrow ? FONT_BOLD_58 : FONT_BOLD_80;
        Font title = narrow ? FONT_TITLE_58 : FONT_TITLE_80;

        String currency = CurrencyUtil.symbol();
        String divider = "-".repeat(lineChars);
        String doubleDivider = "=".repeat(lineChars);

        VBox root = new VBox(3);
        root.setPadding(new Insets(10));
        root.setMaxWidth(receiptWidthPx);
        root.setAlignment(Pos.TOP_CENTER);

        // ---- Store header ----
        Text storeName = new Text(nvl(receipt.getStoreName(), "Vastra Store"));
        storeName.setFont(title);
        root.getChildren().add(storeName);

        if (notBlank(receipt.getStoreAddress())) {
            Text address = centeredText(receipt.getStoreAddress(), normal);
            root.getChildren().add(address);
        }

        StringBuilder contactLine = new StringBuilder();
        if (notBlank(receipt.getStoreGstin())) contactLine.append("GSTIN: ").append(receipt.getStoreGstin());
        if (notBlank(receipt.getStorePhone())) {
            if (contactLine.length() > 0) contactLine.append("   ");
            contactLine.append("Ph: ").append(receipt.getStorePhone());
        }
        if (contactLine.length() > 0) {
            Text contact = centeredText(contactLine.toString(), normal);
            root.getChildren().add(contact);
        }

        root.getChildren().add(new Text(doubleDivider) {{ setFont(normal); }});

        // ---- Bill type badge ----
        Text badge = new Text(receipt.isCredit() ? "CREDIT BILL" : "CASH BILL");
        badge.setFont(bold);
        root.getChildren().add(badge);
        root.getChildren().add(new Text(divider) {{ setFont(normal); }});

        // ---- Bill / cashier / counter info ----
        Text billInfo = new Text(
                "Bill No: " + nvl(receipt.getBillNumber(), "-") + "\n" +
                "Date: " + nvl(receipt.getBillDate(), "-") + "   Time: " + nvl(receipt.getBillTime(), "-") + "\n" +
                "Cashier: " + nvl(receipt.getCashierName(), "-") + "   Counter: " + nvl(receipt.getCounterName(), "-")
        );
        billInfo.setFont(normal);
        root.getChildren().add(billInfo);
        root.getChildren().add(new Text(divider) {{ setFont(normal); }});

        // ---- Items ----
        Text itemsHeader = new Text(String.format("%-16s %6s %4s %8s", "Description", "Rate", "Qty", "Amt"));
        itemsHeader.setFont(bold);
        root.getChildren().add(itemsHeader);
        root.getChildren().add(new Text(divider) {{ setFont(normal); }});

        List<CartItem> items = receipt.getItems();
        if (items != null) {
            for (CartItem item : items) {
                String name = item.getProduct().getName();
                if (item.getProduct().getVariant() != null && !item.getProduct().getVariant().isBlank()) {
                    name += " " + item.getProduct().getVariant();
                }
                Text nameLine = new Text(name.length() > lineChars ? name.substring(0, lineChars) : name);
                nameLine.setFont(bold);
                root.getChildren().add(nameLine);

                String code = notBlank(item.getProduct().getBarcode()) ? item.getProduct().getBarcode()
                        : notBlank(item.getProduct().getSku()) ? item.getProduct().getSku() : "";
                Text itemLine = new Text(String.format("%-16s %6.2f %4d %8.2f",
                        code, item.getProduct().getSellPrice(), item.getQuantity(), item.getLineTotal()));
                itemLine.setFont(normal);
                root.getChildren().add(itemLine);
            }
        }

        root.getChildren().add(new Text(divider) {{ setFont(normal); }});

        // ---- E&OE / total summary ----
        Text eoeLine = new Text(String.format("%-24s %8.2f", "E & O.E., Incl. Tax  Total:", receipt.getTotal()));
        eoeLine.setFont(normal);
        root.getChildren().add(eoeLine);

        Text qtyItemsLine = new Text(String.format("Qty: %-6d  Items: %d", receipt.getQtyTotal(), receipt.getItemCount()));
        qtyItemsLine.setFont(normal);
        root.getChildren().add(qtyItemsLine);

        Text totalLine = new Text(String.format("%-24s %8.2f", "TOTAL:", receipt.getTotal()));
        totalLine.setFont(title);
        root.getChildren().add(totalLine);

        root.getChildren().add(new Text(divider) {{ setFont(normal); }});

        // ---- Payment details ----
        Text paymentHeader = new Text("Payment Details");
        paymentHeader.setFont(bold);
        root.getChildren().add(paymentHeader);
        // paymentMode already carries a human-readable breakdown for split
        // payments (e.g. "CASH ₹300.00 + UPI ₹200.00") or the single mode/CREDIT
        Text paymentLine = new Text(nvl(receipt.getPaymentMode(), "-") +
                (receipt.isCredit() ? "" : ("  :  " + currency + String.format("%.2f", receipt.getTotal()))));
        paymentLine.setFont(normal);
        root.getChildren().add(paymentLine);

        if (receipt.isCredit()) {
            Text creditNote = new Text("Note: CREDIT - Payment Pending");
            creditNote.setFont(bold);
            root.getChildren().add(creditNote);
        }

        root.getChildren().add(new Text(divider) {{ setFont(normal); }});

        // ---- Tax breakup ----
        List<TaxBreakupRow> breakup = receipt.getTaxBreakup();
        if (breakup != null && !breakup.isEmpty()) {
            Text taxHeader = new Text(String.format("%-6s %8s %7s %7s %7s", "Tax%", "Amt", "GST", "SGST", "CGST"));
            taxHeader.setFont(bold);
            root.getChildren().add(taxHeader);
            for (TaxBreakupRow row : breakup) {
                Text taxLine = new Text(String.format("%-6.2f %8.2f %7.2f %7.2f %7.2f",
                        row.getTaxPercent(), row.getTaxableAmount(), row.getGstAmount(), row.getSgstAmount(), row.getCgstAmount()));
                taxLine.setFont(normal);
                root.getChildren().add(taxLine);
            }
            root.getChildren().add(new Text(divider) {{ setFont(normal); }});
        }

        // ---- Customer & loyalty points ----
        if (receipt.isHasCustomer()) {
            StringBuilder custBlock = new StringBuilder();
            custBlock.append("Cust ID: ").append(nvl(receipt.getCustomerCode(), "-")).append("\n");
            custBlock.append(nvl(receipt.getCustomerName(), "")).append("\n");
            if (notBlank(receipt.getCustomerPlace())) custBlock.append(receipt.getCustomerPlace()).append("\n");
            if (notBlank(receipt.getCustomerPhone())) custBlock.append(receipt.getCustomerPhone());
            Text custText = new Text(custBlock.toString().stripTrailing());
            custText.setFont(normal);
            root.getChildren().add(custText);

            Text pointsHeader = new Text(String.format("%-12s %8s %8s", "Pts Opening", "Earned", "Closing"));
            pointsHeader.setFont(bold);
            root.getChildren().add(pointsHeader);
            Text pointsLine = new Text(String.format("%-12.2f %8.2f %8.2f",
                    receipt.getPointsOpening(), receipt.getPointsEarned(), receipt.getPointsClosing()));
            pointsLine.setFont(normal);
            root.getChildren().add(pointsLine);

            root.getChildren().add(new Text(divider) {{ setFont(normal); }});
        }

        // ---- Footer ----
        Text thankYou = centeredText("Thank You - Visit Again", bold);
        root.getChildren().add(thankYou);

        if (notBlank(receipt.getReceiptFooter())) {
            Text footerMsg = centeredText(receipt.getReceiptFooter(), normal);
            root.getChildren().add(footerMsg);
        }

        if (notBlank(receipt.getExchangePolicy())) {
            Text policy = centeredText(receipt.getExchangePolicy(), normal);
            root.getChildren().add(policy);
        }

        return root;
    }

    private static Text centeredText(String content, Font font) {
        Text t = new Text(content);
        t.setFont(font);
        t.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        return t;
    }

    private static String getPaperWidthSetting() {
        try {
            return com.vastra.dao.StoreSettingsDAO.get("receipt_paper_width", "80mm");
        } catch (Exception e) {
            return "80mm";
        }
    }

    /** The printer chosen in Settings > Billing > Receipt Printer, if one is set and still connected. */
    private static Printer resolveConfiguredPrinter() {
        try {
            String configuredName = com.vastra.dao.StoreSettingsDAO.get("receipt_printer_name", "");
            if (configuredName == null || configuredName.isBlank()) return null;
            for (Printer p : Printer.getAllPrinters()) {
                if (p.getName().equals(configuredName)) return p;
            }
            System.err.println("Configured receipt printer '" + configuredName +
                    "' isn't currently available (unplugged/off?) - falling back to a printer picker.");
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static String nvl(String s, String fallback) {
        return (s == null || s.isBlank()) ? fallback : s;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /**
     * Print a JavaFX node
     */
    private static boolean printNode(Node node) {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            System.err.println("No printer available");
            return false;
        }

        Printer printer = resolveConfiguredPrinter();
        if (printer == null) {
            // Nothing usable configured (or the saved printer isn't currently connected) -
            // let the cashier pick one so the sale can still complete, rather than silently
            // failing or going to whatever Windows' default happens to be.
            boolean proceed = job.showPrintDialog(null);
            if (!proceed) return false;
            printer = job.getPrinter();
        } else {
            job.setPrinter(printer);
        }

        PageLayout pageLayout = printer.createPageLayout(
                Paper.A5,
                PageOrientation.PORTRAIT,
                Printer.MarginType.HARDWARE_MINIMUM
        );

        job.getJobSettings().setPageLayout(pageLayout);

        boolean success = job.printPage(node);
        if (success) {
            job.endJob();
        }
        return success;
    }

    /**
     * Print barcode labels
     */
    public static boolean printBarcodeLabel(String productName, String barcode, double price) {
        VBox label = new VBox(5);
        label.setPadding(new Insets(5));
        label.setAlignment(Pos.CENTER);

        Text name = new Text(productName);
        name.setFont(Font.font("Arial", FontWeight.BOLD, 12));

        Text barcodeText = new Text(barcode);
        barcodeText.setFont(Font.font("Monospaced", 10));

        Text priceText = new Text(CurrencyUtil.symbol() + " " + String.format("%.2f", price));
        priceText.setFont(Font.font("Arial", FontWeight.BOLD, 14));

        label.getChildren().addAll(name, barcodeText, priceText);

        return printNode(label);
    }
}