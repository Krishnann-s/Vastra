package com.vastra.ui.controllers;

import com.vastra.dao.ProductDAO;
import com.vastra.model.Product;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

/**
 * Lets a cashier scan the QR/barcode printed on a price tag and immediately
 * see that product's current stock quantity, without adding it to a cart or
 * starting a sale. The scan field auto-refocuses after every lookup so you
 * can keep scanning tag after tag without touching the mouse.
 */
public class StockCheckController {

    @FXML private TextField scanField;
    @FXML private Label nameLabel;
    @FXML private Label detailsLabel;
    @FXML private Label stockLabel;
    @FXML private Label priceLabel;
    @FXML private Label statusLabel;

    @FXML private TableView<Product> historyTable;
    @FXML private TableColumn<Product, String> historyCodeColumn;
    @FXML private TableColumn<Product, String> historyNameColumn;
    @FXML private TableColumn<Product, Number> historyStockColumn;

    private final ObservableList<Product> history = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        historyCodeColumn.setCellValueFactory(d -> new SimpleStringProperty(codeOf(d.getValue())));
        historyNameColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getFullDisplayName()));
        historyStockColumn.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().getStock()));
        historyTable.setItems(history);

        showEmptyState();
        Platform.runLater(() -> scanField.requestFocus());
    }

    @FXML
    public void onLookup() {
        String code = scanField.getText() == null ? "" : scanField.getText().trim();
        scanField.clear();
        scanField.requestFocus();
        if (code.isEmpty()) return;

        try {
            Product product = ProductDAO.findByBarcode(code);
            if (product == null) product = ProductDAO.findBySku(code);

            if (product == null) {
                showNotFound(code);
                return;
            }

            showProduct(product);
            history.add(0, product);
        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("Error looking up product: " + e.getMessage());
            statusLabel.setStyle("-fx-text-fill: #C62828; -fx-font-weight: bold;");
        }
    }

    private void showProduct(Product p) {
        nameLabel.setText(p.getFullDisplayName());

        StringBuilder details = new StringBuilder("Code: " + codeOf(p));
        if (p.getSize() != null && !p.getSize().isBlank()) details.append("   Size: ").append(p.getSize());
        if (p.getColor() != null && !p.getColor().isBlank()) details.append("   Color: ").append(p.getColor());
        detailsLabel.setText(details.toString());

        stockLabel.setText(String.valueOf(p.getStock()));
        stockLabel.setStyle("-fx-font-size: 48px; -fx-font-weight: bold; -fx-text-fill: " + stockColor(p) + ";");

        priceLabel.setText(com.vastra.util.CurrencyUtil.symbol() + String.format("%.2f", p.getSellPrice()));

        statusLabel.setText(p.getStock() <= 0 ? "OUT OF STOCK" : p.isLowStock() ? "LOW STOCK" : "");
        statusLabel.setStyle("-fx-text-fill: " + stockColor(p) + "; -fx-font-weight: bold;");
    }

    private String stockColor(Product p) {
        if (p.getStock() <= 0) return "#C62828";
        if (p.isLowStock()) return "#EF6C00";
        return "#2E7D32";
    }

    private String codeOf(Product p) {
        return p.getSku() != null && !p.getSku().isEmpty() ? p.getSku() : p.getBarcode();
    }

    private void showNotFound(String code) {
        nameLabel.setText("Not found");
        detailsLabel.setText("No product matches code: " + code);
        stockLabel.setText("-");
        stockLabel.setStyle("-fx-font-size: 48px; -fx-font-weight: bold; -fx-text-fill: #999;");
        priceLabel.setText("");
        statusLabel.setText("");
    }

    private void showEmptyState() {
        nameLabel.setText("Scan a product tag to begin");
        detailsLabel.setText("");
        stockLabel.setText("-");
        stockLabel.setStyle("-fx-font-size: 48px; -fx-font-weight: bold; -fx-text-fill: #999;");
        priceLabel.setText("");
        statusLabel.setText("");
    }

    @FXML
    public void onClose() {
        ((Stage) scanField.getScene().getWindow()).close();
    }
}
