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
import javafx.print.Printer;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.ChoiceDialog;
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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Generates 35mm x 22mm garment labels (3 across per row, matching a
 * "105 x 21.5mm 3-up" die-cut label roll) and prints them on a TSC label
 * printer.
 * <p>
 * Printing goes straight to the Windows print spooler as raw TSPL commands
 * (see {@link RawPrinterHelper}), NOT through javafx.print.PrinterJob/GDI.
 * Earlier attempts routed the rendered label through the standard Windows
 * printer driver pipeline (custom Paper, matched Paper, pinned driver
 * default + HARDWARE_MINIMUM margins) and all of them were still at the
 * mercy of the driver re-rendering/re-paginating each "page" - which is
 * exactly what caused the misalignment, wrong orientation, and wasted
 * blank labels between rows. Exporting to PNG and printing that file
 * worked because a plain image viewer prints one flat image at 100% scale
 * with no per-page re-layout. Raw TSPL reproduces that same "one flat
 * image, exact scale" behaviour on the real printer: each row is
 * snapshotted at the printer's native 203 DPI (identical pixels to the PNG
 * export) and sent as a TSPL BITMAP inside a label sized with SIZE/GAP, so
 * the printer's own gap sensor - not a GDI page layout - decides where one
 * physical label ends and the next begins.
 */
public class LabelPrintUtil {

    public static final double LABEL_WIDTH_MM = 35;
    public static final double LABEL_HEIGHT_MM = 21.5;
    public static final int LABELS_PER_ROW = 3;

    public static final double QR_SIZE_MM = 12;

    /** Matches the printer's native resolution (TTP-224/244 Pro are both 203 dpi). */
    private static final double PRINT_DPI = 203.0;

    // -------------------- TSC/TSPL tuning knobs --------------------
    // These are the only things you should ever need to touch to match your
    // physical label stock/printer - everything else is computed.

    /** Physical gap between rows (the die-cut line running across all 3 labels), in mm. */
    private static final double GAP_MM = 2.0;
    /** 0 = normal feed direction, 1 = reversed. Flip if labels print upside-down. */
    private static final int PRINT_DIRECTION = 0;
    /** Print speed, inches/sec (lower = crisper). Lower this first if print looks smeared. */
    private static final int PRINT_SPEED = 4;
    /** Heat/darkness, 0-15. Raise if print looks too light, lower if labels curl/over-ink. */
    private static final int PRINT_DENSITY = 8;
    /** Flip to true only if a test print comes out inverted (black label, white text/QR). */
    private static final boolean INVERT_BITMAP = false;

    private static final String STORE_NAME = "CHENNAI FAASHIONS";

    // -------------------- unit conversion --------------------

    private static double mmToPoints(double mm) {
        return (mm / 25.4) * 72.0;
    }

    // -------------------- QR generation --------------------

    private static Image generateQrImage(String data, double sizeMm) {
        try {
            int sizePx = (int) ((sizeMm / 25.4) * PRINT_DPI);

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

    public static VBox buildSingleLabel(Product product, String careNoteText) {
        double wPoints = mmToPoints(LABEL_WIDTH_MM);
        double hPoints = mmToPoints(LABEL_HEIGHT_MM);

        VBox root = new VBox();
        root.setMinSize(wPoints, hPoints);
        root.setPrefSize(wPoints, hPoints);
        root.setMaxSize(wPoints, hPoints);
        root.setStyle("-fx-background-color: white;");
        root.setClip(new javafx.scene.shape.Rectangle(wPoints, hPoints));

        double horizontalPadding = 14;
        double interGap = 2;
        double availableWidthPoints = wPoints - horizontalPadding - interGap;

        double qrSizePoints = mmToPoints(QR_SIZE_MM);
        double textBlockWidth = availableWidthPoints - qrSizePoints;

        Text brand = new Text(STORE_NAME);
        brand.setFont(Font.font("Arial", FontWeight.BOLD, 6));
        brand.setWrappingWidth(textBlockWidth);

        String desc = (product.getBrand() != null && !product.getBrand().isEmpty()
                ? product.getBrand() + " " : "") + product.getName();
        Text descText = new Text(desc.toUpperCase());
        descText.setFont(Font.font("Arial", FontWeight.BOLD, 6));
        descText.setWrappingWidth(textBlockWidth);

        Text sizeText = new Text("SIZE : " + (product.getVariant() != null ? product.getVariant() : "-"));
        sizeText.setFont(Font.font("Arial", 6.5));
        sizeText.setWrappingWidth(textBlockWidth);

        String itemCode = (product.getSku() != null && !product.getSku().isEmpty())
                ? product.getSku() : product.getBarcode();
        Text codeText = new Text(itemCode);
        codeText.setFont(Font.font("Arial", 6.5));
        codeText.setWrappingWidth(textBlockWidth);

        Text priceText = new Text("PRICE:" + String.format("%.2f", product.getSellPrice()));
        priceText.setFont(Font.font("Arial", FontWeight.BOLD, 6.5));
        priceText.setWrappingWidth(textBlockWidth);

        VBox textBlock = new VBox(1, brand, descText, sizeText, codeText, priceText);

        textBlock.setPadding(new Insets(2, 0, 7, 12));
        textBlock.setAlignment(Pos.CENTER_LEFT);
        textBlock.setMaxWidth(textBlockWidth);
        textBlock.setPrefWidth(textBlockWidth);

        Image qrImage = generateQrImage(itemCode != null ? itemCode : product.getId(), QR_SIZE_MM);
        ImageView qrView = new ImageView(qrImage);
        qrView.setFitWidth(qrSizePoints);
        qrView.setFitHeight(qrSizePoints);

        HBox body = new HBox(interGap, textBlock, qrView);
        body.setAlignment(Pos.CENTER_LEFT);

        if (careNoteText != null && !careNoteText.isBlank()) {
            Text care = new Text(careNoteText.toUpperCase());
            care.setFont(Font.font("Arial", 4.5));
            care.setRotate(-90);
            VBox careHolder = new VBox(care);
            careHolder.setAlignment(Pos.CENTER);
            careHolder.setPrefWidth(mmToPoints(3));
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
        double w = mmToPoints(LABEL_WIDTH_MM);
        double h = mmToPoints(LABEL_HEIGHT_MM);
        r.setMinSize(w, h);
        r.setPrefSize(w, h);
        r.setMaxSize(w, h);
        return r;
    }

    private static HBox buildRow(List<Product> rowProducts, String careNoteText) {
        HBox row = new HBox(0);
        row.setAlignment(Pos.CENTER_LEFT);

        double totalWidth = mmToPoints(LABEL_WIDTH_MM * LABELS_PER_ROW);
        row.setMinWidth(totalWidth);
        row.setPrefWidth(totalWidth);
        row.setMaxWidth(totalWidth);

        for (Product p : rowProducts) {
            row.getChildren().add(buildSingleLabel(p, careNoteText));
        }
        while (row.getChildren().size() < LABELS_PER_ROW) {
            row.getChildren().add(blankLabelSlot());
        }
        return row;
    }

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

    public static VBox buildPreviewSheet(List<LabelPrintItem> items, String careNoteText) {
        VBox sheet = new VBox(2);
        for (List<Product> row : chunkIntoRows(flatten(items))) {
            sheet.getChildren().add(buildRow(row, careNoteText));
        }
        return sheet;
    }

    public static boolean exportPreviewAsPng(List<LabelPrintItem> items, String careNoteText, File outputFile) {
        try {
            VBox sheet = buildPreviewSheet(items, careNoteText);
            new Scene(sheet);
            sheet.applyCss();
            sheet.layout();

            SnapshotParameters params = new SnapshotParameters();
            params.setFill(Color.WHITE);
            params.setTransform(javafx.scene.transform.Transform.scale(PRINT_DPI / 72.0, PRINT_DPI / 72.0));
            WritableImage snapshot = sheet.snapshot(params, null);

            BufferedImage bufferedImage = SwingFXUtils.fromFXImage(snapshot, null);
            return ImageIO.write(bufferedImage, "png", outputFile);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // -------------------- raw TSPL printing --------------------

    /**
     * Renders one row (up to 3 labels) exactly like {@link #exportPreviewAsPng},
     * then packs it into a TSPL CLS/BITMAP/PRINT sequence for one physical label.
     */
    private static byte[] buildRowTsplCommand(List<Product> rowProducts, String careNoteText) throws Exception {
        HBox rowNode = buildRow(rowProducts, careNoteText);
        new Scene(rowNode);
        rowNode.applyCss();
        rowNode.layout();

        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.WHITE);
        params.setTransform(javafx.scene.transform.Transform.scale(PRINT_DPI / 72.0, PRINT_DPI / 72.0));
        WritableImage snap = rowNode.snapshot(params, null);

        BufferedImage bufferedImage = SwingFXUtils.fromFXImage(snap, null);
        int widthDots = bufferedImage.getWidth();
        int heightDots = bufferedImage.getHeight();
        int widthBytes = (widthDots + 7) / 8;
        byte[] bitmapData = packMonochromeBitmap(bufferedImage, widthBytes);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeAscii(out, "CLS\r\n");
        writeAscii(out, "BITMAP 0,0," + widthBytes + "," + heightDots + ",0,");
        out.write(bitmapData);
        writeAscii(out, "\r\n");
        writeAscii(out, "PRINT 1,1\r\n");
        return out.toByteArray();
    }

    /**
     * Packs an image into TSPL's 1-bit-per-pixel BITMAP format: MSB-first,
     * row-major, one bit per dot, 1 = print (black). Same convention ZPL/EPL
     * raster commands use.
     */
    private static byte[] packMonochromeBitmap(BufferedImage img, int widthBytes) {
        int width = img.getWidth();
        int height = img.getHeight();
        byte[] data = new byte[widthBytes * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = img.getRGB(x, y);
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                int luminance = (r * 299 + g * 587 + b * 114) / 1000;
                boolean ink = luminance < 128;
                if (INVERT_BITMAP) ink = !ink;
                if (ink) {
                    int byteIndex = y * widthBytes + (x / 8);
                    int bitIndex = 7 - (x % 8);
                    data[byteIndex] |= (byte) (1 << bitIndex);
                }
            }
        }
        return data;
    }

    private static void writeAscii(ByteArrayOutputStream out, String s) {
        out.write(s.getBytes(StandardCharsets.US_ASCII), 0, s.length());
    }

    private static String mm(double value) {
        return String.format(Locale.US, "%.2f mm", value);
    }

    /**
     * Prints the queued labels, one TSPL label (one row of up to 3) per
     * physical feed, sent as a single raw job to the configured/selected
     * TSC printer. Returns true if the whole batch was written successfully.
     */
    public static boolean printLabels(List<LabelPrintItem> items, String careNoteText, javafx.stage.Window ownerWindow) {
        if (items == null || items.isEmpty()) return false;

        String printerName = resolveLabelPrinterName(ownerWindow);
        if (printerName == null) return false;

        List<List<Product>> rows = chunkIntoRows(flatten(items));
        if (rows.isEmpty()) return false;

        try {
            ByteArrayOutputStream job = new ByteArrayOutputStream();
            writeAscii(job, "SIZE " + mm(LABEL_WIDTH_MM * LABELS_PER_ROW) + "," + mm(LABEL_HEIGHT_MM) + "\r\n");
            writeAscii(job, "GAP " + mm(GAP_MM) + ",0 mm\r\n");
            writeAscii(job, "DIRECTION " + PRINT_DIRECTION + "\r\n");
            writeAscii(job, "SPEED " + PRINT_SPEED + "\r\n");
            writeAscii(job, "DENSITY " + PRINT_DENSITY + "\r\n");

            for (List<Product> row : rows) {
                job.write(buildRowTsplCommand(row, careNoteText));
            }

            RawPrinterHelper.sendBytesToPrinter(printerName, job.toByteArray());
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * The printer chosen in Settings > Billing > Label Printer, if set and
     * still connected; otherwise prompts with a picker (mirrors
     * ThermalPrinterUtil's receipt-printer resolution).
     */
    private static String resolveLabelPrinterName(javafx.stage.Window ownerWindow) {
        List<String> names = new ArrayList<>();
        for (Printer p : Printer.getAllPrinters()) {
            names.add(p.getName());
        }
        if (names.isEmpty()) {
            System.err.println("No printers installed on this system.");
            return null;
        }

        try {
            String saved = com.vastra.dao.StoreSettingsDAO.get("label_printer_name", "");
            if (saved != null && !saved.isBlank() && names.contains(saved)) {
                return saved;
            }
            if (saved != null && !saved.isBlank()) {
                System.err.println("Configured label printer '" + saved +
                        "' isn't currently available (unplugged/off?) - falling back to a printer picker.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        Printer defaultPrinter = Printer.getDefaultPrinter();
        String defaultChoice = (defaultPrinter != null && names.contains(defaultPrinter.getName()))
                ? defaultPrinter.getName() : names.get(0);

        ChoiceDialog<String> dialog = new ChoiceDialog<>(defaultChoice, names);
        dialog.setTitle("Select Label Printer");
        dialog.setHeaderText("Choose the TSC label printer to print to");
        dialog.setContentText("Printer:");
        if (ownerWindow != null) dialog.initOwner(ownerWindow);
        Optional<String> result = dialog.showAndWait();
        return result.orElse(null);
    }
}