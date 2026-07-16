package com.vastra.ui.controllers;

import com.vastra.dao.ProductDAO;
import com.vastra.model.Product;
import com.vastra.util.BarcodeUtil;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;

public class ProductFormController {
    @FXML private Label headerLabel;
    @FXML private Label subHeaderLabel;
    @FXML private TextField nameField;
    @FXML private TextField variantField;
    @FXML private TextField categoryField;
    @FXML private TextField brandField;
    @FXML private TextField skuField;
    @FXML private TextField mrpField;
    @FXML private TextField sellPriceField;
    @FXML private TextField purchasePriceField;
    @FXML private TextField gstField;
    @FXML private TextField hsnField;
    @FXML private TextField stockField;
    @FXML private TextField openingStockField;
    @FXML private TextField reorderField;
    @FXML private ComboBox<String> unitCombo;
    @FXML private TextArea descriptionField;
    @FXML private ComboBox<String> sizeCombo;
    @FXML private TextField colorField;

    /** Non-null when this form is editing an existing product instead of creating a new one. */
    private Product editingProduct;

    /** Once the user types into MRP directly, stop auto-mirroring Selling Price into it. */
    private boolean mrpManuallyEdited = false;
    private boolean syncingMrp = false;

    @FXML
    public void initialize() {
        // Setup unit combo box
        if (unitCombo != null) {
            unitCombo.getItems().addAll("PCS", "KG", "GRAM", "LITER", "METER", "BOX", "DOZEN");
            unitCombo.setValue("PCS");
        }

        // Set default values
        if (gstField != null) {
            String defaultGst = "18";
            try {
                defaultGst = com.vastra.dao.StoreSettingsDAO.get("default_gst_percent", "18");
            } catch (Exception e) {
                e.printStackTrace();
            }
            gstField.setText(defaultGst);
        }
        if (reorderField != null) reorderField.setText("5");

        if (sizeCombo != null) {
            sizeCombo.setEditable(true);
            sizeCombo.getItems().addAll("XS", "S", "M", "L", "XL", "XXL", "XXXL",
                    "0-1Y", "1-2Y", "2-3Y", "3-4Y", "4-5Y", "6-7Y", "8-9Y", "10-11Y", "Free Size");
        }

        // MRP defaults to whatever Selling Price is typed, since for most items here
        // they're the same - but the moment the user types into MRP themselves
        // (e.g. the garment's printed MRP tag differs from what it's sold for),
        // stop overwriting it.
        if (sellPriceField != null && mrpField != null) {
            sellPriceField.textProperty().addListener((obs, oldV, newV) -> {
                if (!mrpManuallyEdited) {
                    syncingMrp = true;
                    mrpField.setText(newV);
                    syncingMrp = false;
                }
            });
            mrpField.textProperty().addListener((obs, oldV, newV) -> {
                if (!syncingMrp) {
                    mrpManuallyEdited = true;
                }
            });
        }
    }

    /** Switches this form into edit mode, pre-filled with an existing product's details. */
    public void setEditingProduct(Product product) {
        this.editingProduct = product;
        // This product already has a real (possibly different) MRP - don't clobber it
        // with the auto-mirror-from-selling-price behaviour meant for new products.
        mrpManuallyEdited = true;

        if (headerLabel != null) headerLabel.setText("Edit Product");
        if (subHeaderLabel != null) subHeaderLabel.setText("Update the product details below");

        nameField.setText(product.getName());
        variantField.setText(product.getVariant());
        categoryField.setText(product.getCategory());
        brandField.setText(product.getBrand());
        skuField.setText(product.getSku());
        mrpField.setText(String.format("%.2f", product.getMrp()));
        sellPriceField.setText(String.format("%.2f", product.getSellPrice()));
        purchasePriceField.setText(String.format("%.2f", product.getPurchasePrice()));
        gstField.setText(String.valueOf(product.getGstPercent()));
        if (hsnField != null) hsnField.setText(product.getHsnCode());
        stockField.setText(String.valueOf(product.getStock()));
        if (openingStockField != null) openingStockField.setText(String.valueOf(product.getOpeningStock()));
        reorderField.setText(String.valueOf(product.getReorderThreshold()));
        if (unitCombo != null) unitCombo.setValue(product.getUnit());
        if (descriptionField != null) descriptionField.setText(product.getDescription());
        if (sizeCombo != null) sizeCombo.setValue(product.getSize());
        if (colorField != null) colorField.setText(product.getColor());
    }

    @FXML
    public void onSave() {
        try {
            // Validate required fields
            if (nameField.getText().trim().isEmpty()) {
                showError("Product name is required");
                nameField.requestFocus();
                return;
            }

            if (sellPriceField.getText().trim().isEmpty()) {
                showError("Selling price is required");
                sellPriceField.requestFocus();
                return;
            }

            if (stockField.getText().trim().isEmpty()) {
                showError("Stock quantity is required");
                stockField.requestFocus();
                return;
            }

            // Parse values
            String name = nameField.getText().trim();
            String variant = variantField.getText().trim();
            String category = categoryField.getText().trim();
            String brand = brandField.getText().trim();
            String sku = skuField.getText().trim();

            int mrp = (int) (Double.parseDouble(mrpField.getText().trim()) * 100);
            int sellPrice = (int) (Double.parseDouble(sellPriceField.getText().trim()) * 100);
            int purchasePrice = 0;
            if (!purchasePriceField.getText().trim().isEmpty()) {
                purchasePrice = (int) (Double.parseDouble(purchasePriceField.getText().trim()) * 100);
            }

            int gst = Integer.parseInt(gstField.getText().trim());
            int stock = Integer.parseInt(stockField.getText().trim());
            int openingStock = 0;
            if (openingStockField != null && !openingStockField.getText().trim().isEmpty()) {
                openingStock = Integer.parseInt(openingStockField.getText().trim());
            }
            int reorderThreshold = Integer.parseInt(reorderField.getText().trim());
            String hsnCode = hsnField != null ? hsnField.getText().trim() : "";
            String description = descriptionField != null ? descriptionField.getText().trim() : "";
            String size = sizeCombo != null && sizeCombo.getValue() != null ? sizeCombo.getValue().trim() : "";
            String color = colorField != null ? colorField.getText().trim() : "";

            // Validation
            if (sellPrice <= 0) {
                showError("Selling price must be greater than 0");
                return;
            }

            if (stock < 0) {
                showError("Stock cannot be negative");
                return;
            }

            if (openingStock < 0) {
                showError("Opening stock cannot be negative");
                return;
            }

            if (gst < 0 || gst > 28) {
                showError("GST must be between 0 and 28");
                return;
            }

            // Check if SKU already exists on a DIFFERENT product
            if (!sku.isEmpty()) {
                Product withSameSku = ProductDAO.findBySku(sku);
                if (withSameSku != null && (editingProduct == null || !withSameSku.getId().equals(editingProduct.getId()))) {
                    showError("SKU already exists: " + sku);
                    return;
                }
            }

            if (editingProduct == null) {
                String productId = ProductDAO.insertProduct(
                        name, variant, mrp, sellPrice, purchasePrice, gst, stock, openingStock,
                        category, brand, sku, hsnCode, reorderThreshold, description,
                        size, color
                );

                // Generate barcode image for printing
                try {
                    String barcode = sku.isEmpty() ? productId : sku;
                    BarcodeUtil.generateCode128(barcode, "labels/" + productId + ".png");
                } catch (Exception e) {
                    System.err.println("Could not generate barcode image: " + e.getMessage());
                }

                showSuccess("Product added successfully!\nBarcode label created.");
            } else {
                editingProduct.setName(name);
                editingProduct.setVariant(variant);
                editingProduct.setCategory(category);
                editingProduct.setBrand(brand);
                editingProduct.setSku(sku);
                editingProduct.setMrpCents(mrp);
                editingProduct.setSellPriceCents(sellPrice);
                editingProduct.setPurchasePriceCents(purchasePrice);
                editingProduct.setGstPercent(gst);
                editingProduct.setHsnCode(hsnCode);
                editingProduct.setStock(stock);
                editingProduct.setOpeningStock(openingStock);
                editingProduct.setReorderThreshold(reorderThreshold);
                editingProduct.setUnit(unitCombo != null ? unitCombo.getValue() : editingProduct.getUnit());
                editingProduct.setDescription(description);
                editingProduct.setSize(size);
                editingProduct.setColor(color);

                ProductDAO.updateProduct(editingProduct);
                showSuccess("Product updated successfully!");
            }

            closeWindow();

        } catch (NumberFormatException e) {
            showError("Please enter valid numbers for price, GST, and stock fields");
        } catch (Exception e) {
            showError("Error saving product: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    public void onCancel() {
        closeWindow();
    }

    private void closeWindow() {
        Stage stage = (Stage) nameField.getScene().getWindow();
        stage.close();
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private void showSuccess(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, msg);
        alert.setTitle("Success");
        alert.setHeaderText(null);
        alert.show();
    }
}
