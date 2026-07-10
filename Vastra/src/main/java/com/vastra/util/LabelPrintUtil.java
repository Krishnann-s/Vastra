package com.vastra.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.vastra.model.LabelPrintItem;
import com.vastra.model.Product;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.print.*;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates and prints garment labels sized to match your existing label
 * roll: 35mm x 22mm, printed 3 across per row.
 *
 * IMPORTANT ONE-TIME SETUP:
 * JavaFX cannot invent a custom paper size on the fly - it can only pick from
 * paper sizes your printer driver already knows about. Before this will print
 * at the correct size, go into Windows > Printer Properties > Advanced (or
 * "Forms") for your label printer and create a custom paper size:
 *      Width  = 105mm  (3 x 35mm)
 *      Height = 22mm
 * Name it something like "VastraLabel-105x22". Once that exists, this class
 * will auto-detect and use it. If no matching paper is found, it falls back
 * to A4 so nothing crashes, but the label positions will be wrong until you
 * add the custom paper size.
 */
public class LabelPrintUtil {

    public static final double LABEL_WIDTH_MM = 35;
    public static final double LABEL_HEIGHT_MM = 22;
    public static final int LABELS_PER_ROW = 3;

    // Most cheap thermal/label printers report 203 DPI. Change if yours differs.
    private static final double DPI = 203;

    private static final String STORE_NAME = "CHENNAI FAASHIONS";

    // -------------------- unit conversion --------------------

    private static double mmToPx(double mm) {
        return (mm / 25.4) * DPI;
    }

    private static double mmToPoints(double mm) {
        return (mm / 25.4) * 72.0; // JavaFX Paper dimensions are in points (1/72 inch)
    }

    // -------------------- QR generation --------------------

    private static Image generateQrImage(String data, int sizePx) {
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.MARGIN, 0);
            BitMatrix matrix = new MultiFormatWriter()
                    .encode(data, BarcodeFormat.QR_CODE, sizePx, sizePx, hints);
            BufferedImage bufferedImage = MatrixToImageWriter.toBufferedImage(matrix);
            return SwingFXUtils.toFXImage(bufferedImage, null);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // -------------------- label layout --------------------

    /**
     * Builds a single 35mm x 22mm label node matching your printed sample:
     * store name, brand + cloth name, size, item code, price, QR code.
     *
     * @param careNoteText optional rotated text along the left edge (e.g.
     *                     wash-care instructions). Pass null/empty to skip it.
     */
    public static VBox buildSingleLabel(Product product, String careNoteText) {
        double wPx = mmToPx(LABEL_WIDTH_MM);
        double hPx = mmToPx(LABEL_HEIGHT_MM);

        VBox root = new VBox();
        root.setMinSize(wPx, hPx);
        root.setPrefSize(wPx, hPx);
        root.setMaxSize(wPx, hPx);
        root.setStyle("-fx-border-color: black; -fx-border-width: 0.5;");

        Text brand = new Text(STORE_NAME);
        brand.setFont(Font.font("Arial", FontWeight.BOLD, 6.5));

        String desc = (product.getBrand() != null && !product.getBrand().isEmpty()
                ? product.getBrand() + " " : "") + product.getName();
        Text descText = new Text(desc.toUpperCase());
        descText.setFont(Font.font("Arial", FontWeight.BOLD, 7));
        descText.setWrappingWidth(wPx * 0.6);

        Text sizeText = new Text("SIZE : " + (product.getVariant() != null ? product.getVariant() : "-"));
        sizeText.setFont(Font.font("Arial", 7));

        String itemCode = (product.getSku() != null && !product.getSku().isEmpty())
                ? product.getSku() : product.getBarcode();
        Text codeText = new Text(itemCode);
        codeText.setFont(Font.font("Arial", 7));

        Text priceText = new Text("PRICE:" + String.format("%.2f", product.getSellPrice()));
        priceText.setFont(Font.font("Arial", FontWeight.BOLD, 7));

        VBox textBlock = new VBox(1, brand, descText, sizeText, codeText, priceText);
        textBlock.setPadding(new Insets(2, 2, 2, 2));
        textBlock.setAlignment(Pos.TOP_LEFT);

        int qrSizePx = (int) (hPx * 0.85);
        Image qrImage = generateQrImage(itemCode != null ? itemCode : product.getId(), qrSizePx);
        ImageView qrView = new ImageView(qrImage);
        qrView.setFitWidth(qrSizePx);
        qrView.setFitHeight(qrSizePx);

        HBox body = new HBox(2, textBlock, qrView);
        body.setAlignment(Pos.CENTER_LEFT);

        if (careNoteText != null && !careNoteText.isBlank()) {
            Text care = new Text(careNoteText.toUpperCase());
            care.setFont(Font.font("Arial", 5));
            care.setRotate(-90);
            VBox careHolder = new VBox(care);
            careHolder.setAlignment(Pos.CENTER);
            careHolder.setPrefWidth(mmToPx(3));
            HBox withCare = new HBox(careHolder, body);
            withCare.setAlignment(Pos.CENTER_LEFT);
            root.getChildren().add(withCare);
        } else {
            root.getChildren().add(body);
        }

        return root;
    }

    private static Region blankLabelSlot() {
        Region r = new Region();
        r.setMinSize(mmToPx(LABEL_WIDTH_MM), mmToPx(LABEL_HEIGHT_MM));
        r.setPrefSize(mmToPx(LABEL_WIDTH_MM), mmToPx(LABEL_HEIGHT_MM));
        r.setMaxSize(mmToPx(LABEL_WIDTH_MM), mmToPx(LABEL_HEIGHT_MM));
        return r;
    }

    private static HBox buildRow(List<Product> rowProducts, String careNoteText) {
        HBox row = new HBox(0);
        row.setAlignment(Pos.CENTER_LEFT);
        for (Product p : rowProducts) {
            row.getChildren().add(buildSingleLabel(p, careNoteText));
        }
        while (row.getChildren().size() < LABELS_PER_ROW) {
            row.getChildren().add(blankLabelSlot());
        }
        return row;
    }

    /** Expands the queue (product + quantity) into one flat list, one entry per physical label. */
    private static List<Product> flatten(List<LabelPrintItem> items) {
        List<Product> flat = new ArrayList<>();
        for (LabelPrintItem item : items) {
            for (int i = 0; i < item.getQuantity(); i++) {
                flat.add(item.getProduct());
            }
        }
        return flat;
    }

    private static List<List<Product>> chunkIntoRows(List<Product> flat) {
        List<List<Product>> rows = new ArrayList<>();
        for (int i = 0; i < flat.size(); i += LABELS_PER_ROW) {
            rows.add(flat.subList(i, Math.min(i + LABELS_PER_ROW, flat.size())));
        }
        return rows;
    }

    /** Builds a full on-screen preview (all rows stacked) - use this in a ScrollPane before printing. */
    public static VBox buildPreviewSheet(List<LabelPrintItem> items, String careNoteText) {
        VBox sheet = new VBox(2);
        for (List<Product> row : chunkIntoRows(flatten(items))) {
            sheet.getChildren().add(buildRow(row, careNoteText));
        }
        return sheet;
    }

    // -------------------- test without a physical printer --------------------

    /**
     * Renders the given label sheet to a PNG file at the printer's exact DPI
     * (203) so you can inspect or print-test the layout without the TSC
     * printer connected. Open the PNG at 100% zoom to check text/QR fit, or
     * print it on any regular printer at "actual size" (not "fit to page")
     * and hold it against a real label to check scale.
     */
    public static boolean exportPreviewAsPng(List<LabelPrintItem> items, String careNoteText, File outputFile) {
        try {
            VBox sheet = buildPreviewSheet(items, careNoteText);
            new Scene(sheet); // attaching to a scene forces a layout pass before snapshot
            sheet.applyCss();
            sheet.layout();

            SnapshotParameters params = new SnapshotParameters();
            params.setFill(Color.WHITE);
            WritableImage snapshot = sheet.snapshot(params, null);

            BufferedImage bufferedImage = SwingFXUtils.fromFXImage(snapshot, null);
            return ImageIO.write(bufferedImage, "png", outputFile);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // -------------------- paper size detection --------------------

    private static Paper findBestMatchingPaper(Printer printer, double wantedWidthMm, double wantedHeightMm) {
        double wantedWidthPt = mmToPoints(wantedWidthMm);
        double wantedHeightPt = mmToPoints(wantedHeightMm);
        double tolerancePt = mmToPoints(3); // 3mm tolerance

        Paper best = null;
        double bestDiff = Double.MAX_VALUE;

        for (Paper p : printer.getPrinterAttributes().getSupportedPapers()) {
            double diff = Math.abs(p.getWidth() - wantedWidthPt) + Math.abs(p.getHeight() - wantedHeightPt);
            if (diff < bestDiff) {
                bestDiff = diff;
                best = p;
            }
        }

        if (best != null && bestDiff <= tolerancePt * 2) {
            return best;
        }
        return null; // no acceptable match
    }

    // -------------------- printing --------------------

    /**
     * Prints the queued labels, one row (3 labels) per physical page/feed.
     * Returns true if every row printed successfully.
     *
     * @param careNoteText optional wash-care text rotated on the left edge of each label, or null.
     */
    public static boolean printLabels(List<LabelPrintItem> items, String careNoteText) {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            System.err.println("No printer available");
            return false;
        }

        Printer printer = job.getPrinter();
        Paper matchedPaper = findBestMatchingPaper(printer,
                LABEL_WIDTH_MM * LABELS_PER_ROW, LABEL_HEIGHT_MM);

        Paper paperToUse = matchedPaper != null ? matchedPaper : Paper.A4;
        if (matchedPaper == null) {
            System.err.println("WARNING: No custom label paper found on printer '" + printer.getName() +
                    "'. Falling back to A4 - labels will NOT be positioned correctly. " +
                    "Add a custom paper size of 105mm x 22mm in your printer driver settings.");
        }

        PageLayout layout = printer.createPageLayout(paperToUse, PageOrientation.PORTRAIT, Printer.MarginType.HARDWARE_MINIMUM);
        job.getJobSettings().setPageLayout(layout);

        List<List<Product>> rows = chunkIntoRows(flatten(items));
        boolean allOk = true;
        for (List<Product> row : rows) {
            HBox rowNode = buildRow(row, careNoteText);
            boolean printed = job.printPage(rowNode);
            allOk = allOk && printed;
        }

        if (allOk) {
            job.endJob();
        } else {
            job.cancelJob();
        }
        return allOk;
    }
}