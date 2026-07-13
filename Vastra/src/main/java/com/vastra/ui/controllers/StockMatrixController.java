package com.vastra.ui.controllers;

import com.vastra.dao.ProductDAO;
import com.vastra.model.Product;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.*;

/**
 * Shows every size/color variant of one "style" (products sharing the same
 * name) as a grid - sizes down the side, colors across the top, stock count
 * in each cell - instead of hunting through a flat product list one row at
 * a time. Only products that have a Size and/or Color set (in the product
 * form) show up here; plain single-variant products aren't affected.
 */
public class StockMatrixController {

    @FXML private ComboBox<String> styleCombo;
    @FXML private VBox gridContainer;
    @FXML private Label emptyLabel;

    @FXML
    public void initialize() {
        try {
            List<String> styles = ProductDAO.getStyleNamesWithVariants();
            styleCombo.setItems(FXCollections.observableArrayList(styles));
            if (!styles.isEmpty()) {
                styleCombo.setValue(styles.get(0));
                loadMatrix(styles.get(0));
            } else {
                emptyLabel.setVisible(true);
                emptyLabel.setManaged(true);
            }
        } catch (Exception e) {
            e.printStackTrace();
            showError("Error loading styles: " + e.getMessage());
        }
    }

    @FXML
    public void onStyleSelected() {
        String style = styleCombo.getValue();
        if (style != null) loadMatrix(style);
    }

    private void loadMatrix(String styleName) {
        gridContainer.getChildren().clear();
        try {
            List<Product> variants = ProductDAO.getVariantsForStyle(styleName);
            if (variants.isEmpty()) {
                gridContainer.getChildren().add(new Label("No variants found for this style."));
                return;
            }

            // Distinct sizes (rows) and colors (columns), in a sensible order where possible
            TreeSet<String> sizes = new TreeSet<>(Comparator.comparingInt(this::sizeSortOrder).thenComparing(s -> s));
            TreeSet<String> colors = new TreeSet<>();
            for (Product p : variants) {
                sizes.add(blankToDash(p.getSize()));
                colors.add(blankToDash(p.getColor()));
            }

            // Look up each (size, color) combination directly
            Map<String, Product> bySizeColor = new HashMap<>();
            for (Product p : variants) {
                bySizeColor.put(blankToDash(p.getSize()) + "||" + blankToDash(p.getColor()), p);
            }

            GridPane grid = new GridPane();
            grid.setHgap(6);
            grid.setVgap(6);
            grid.setPadding(new Insets(10));

            // Header row
            grid.add(new Label(""), 0, 0);
            int col = 1;
            for (String color : colors) {
                Label header = boldLabel(color);
                grid.add(header, col, 0);
                col++;
            }

            int row = 1;
            for (String size : sizes) {
                grid.add(boldLabel(size), 0, row);
                col = 1;
                for (String color : colors) {
                    Product p = bySizeColor.get(size + "||" + color);
                    grid.add(buildCell(p), col, row);
                    col++;
                }
                row++;
            }

            ScrollPane scroll = new ScrollPane(grid);
            scroll.setFitToHeight(true);
            gridContainer.getChildren().add(scroll);

        } catch (Exception e) {
            e.printStackTrace();
            gridContainer.getChildren().add(new Label("Error loading matrix: " + e.getMessage()));
        }
    }

    private VBox buildCell(Product p) {
        VBox cell = new VBox(2);
        cell.setAlignment(Pos.CENTER);
        cell.setPadding(new Insets(8));
        cell.setPrefSize(90, 55);

        if (p == null) {
            cell.setStyle("-fx-background-color: #F5F5F5; -fx-background-radius: 4;");
            Label dash = new Label("-");
            dash.setStyle("-fx-text-fill: #ccc;");
            cell.getChildren().add(dash);
            return cell;
        }

        String bg;
        if (p.getStock() <= 0) bg = "#FFEBEE";
        else if (p.isLowStock()) bg = "#FFF3E0";
        else bg = "#E8F5E9";
        cell.setStyle("-fx-background-color: " + bg + "; -fx-background-radius: 4; -fx-cursor: hand;");

        Label stockLabel = new Label(String.valueOf(p.getStock()));
        stockLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        Label priceLabel = new Label(com.vastra.util.CurrencyUtil.symbol() + String.format("%.0f", p.getSellPrice()));
        priceLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #666;");
        cell.getChildren().addAll(stockLabel, priceLabel);

        Tooltip tooltip = new Tooltip(p.getFullDisplayName() +
                "\nBarcode: " + p.getBarcode() +
                "\nStock: " + p.getStock() + " (reorder at " + p.getReorderThreshold() + ")");
        Tooltip.install(cell, tooltip);

        return cell;
    }

    private Label boldLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-weight: bold;");
        return l;
    }

    private String blankToDash(String s) {
        return (s == null || s.isBlank()) ? "-" : s.trim();
    }

    /** Puts common clothing sizes in a natural order instead of alphabetical (M before S, etc.) */
    private int sizeSortOrder(String size) {
        List<String> order = List.of("XS", "S", "M", "L", "XL", "XXL", "XXXL");
        int idx = order.indexOf(size);
        return idx >= 0 ? idx : 100;
    }

    @FXML
    public void onClose() {
        ((Stage) styleCombo.getScene().getWindow()).close();
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}