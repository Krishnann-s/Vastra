package com.vastra.ui.controllers;

import com.vastra.dao.StoreSettingsDAO;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

public class SettingsController {

    @FXML private TextField storeNameField;
    @FXML private TextField storeAddressField;
    @FXML private TextField storePhoneField;
    @FXML private TextField storeEmailField;
    @FXML private TextField storeGstinField;
    @FXML private TextField currencySymbolField;
    @FXML private TextField counterNameField;
    @FXML private TextArea receiptFooterField;
    @FXML private TextArea exchangePolicyField;

    @FXML private CheckBox lowStockAlertCheckBox;
    @FXML private CheckBox loyaltyEnabledCheckBox;
    @FXML private TextField pointsPer100Field;
    @FXML private TextField minPointsRedemptionField;
    @FXML private CheckBox autoBackupEnabledCheckBox;
    @FXML private ComboBox<String> receiptPaperWidthCombo;
    @FXML private ComboBox<String> receiptPrinterCombo;
    @FXML private TextField defaultGstPercentField;

    @FXML
    public void initialize() {
        try {
            storeNameField.setText(StoreSettingsDAO.get("store_name", ""));
            storeAddressField.setText(StoreSettingsDAO.get("store_address", ""));
            storePhoneField.setText(StoreSettingsDAO.get("store_phone", ""));
            storeEmailField.setText(StoreSettingsDAO.get("store_email", ""));
            storeGstinField.setText(StoreSettingsDAO.get("store_gstin", ""));
            currencySymbolField.setText(StoreSettingsDAO.get("currency_symbol", "₹"));
            counterNameField.setText(StoreSettingsDAO.get("counter_name", "Serve"));
            receiptFooterField.setText(StoreSettingsDAO.get("receipt_footer", "Thank you for shopping with us!"));
            exchangePolicyField.setText(StoreSettingsDAO.get("exchange_policy", ""));

            lowStockAlertCheckBox.setSelected("1".equals(StoreSettingsDAO.get("low_stock_alert_enabled", "1")));
            loyaltyEnabledCheckBox.setSelected("1".equals(StoreSettingsDAO.get("loyalty_enabled", "1")));
            autoBackupEnabledCheckBox.setSelected("1".equals(StoreSettingsDAO.get("auto_backup_enabled", "1")));

            receiptPaperWidthCombo.setItems(javafx.collections.FXCollections.observableArrayList("58mm", "80mm"));
            receiptPaperWidthCombo.setValue(StoreSettingsDAO.get("receipt_paper_width", "80mm"));
            defaultGstPercentField.setText(StoreSettingsDAO.get("default_gst_percent", "18"));

            javafx.collections.ObservableList<String> printerNames = javafx.collections.FXCollections.observableArrayList();
            printerNames.add("(Ask me each time)");
            for (javafx.print.Printer p : javafx.print.Printer.getAllPrinters()) {
                printerNames.add(p.getName());
            }
            receiptPrinterCombo.setItems(printerNames);
            String savedPrinter = StoreSettingsDAO.get("receipt_printer_name", "");
            receiptPrinterCombo.setValue((savedPrinter == null || savedPrinter.isBlank())
                    ? "(Ask me each time)" : savedPrinter);
            pointsPer100Field.setText(StoreSettingsDAO.get("points_per_100_rupees", "1"));
            minPointsRedemptionField.setText(StoreSettingsDAO.get("min_points_redemption", "100"));
        } catch (Exception e) {
            e.printStackTrace();
            showError("Error loading settings: " + e.getMessage());
        }
    }

    @FXML
    public void onSave() {
        try {
            // Basic validation on the numeric fields before writing anything
            int pointsPer100 = Integer.parseInt(pointsPer100Field.getText().trim());
            int minRedemption = Integer.parseInt(minPointsRedemptionField.getText().trim());
            if (pointsPer100 < 0 || minRedemption < 0) {
                showError("Points values must be zero or positive");
                return;
            }
            int defaultGst = Integer.parseInt(defaultGstPercentField.getText().trim());
            if (defaultGst < 0 || defaultGst > 100) {
                showError("Default GST % must be between 0 and 100");
                return;
            }

            StoreSettingsDAO.set("store_name", storeNameField.getText().trim());
            StoreSettingsDAO.set("store_address", storeAddressField.getText().trim());
            StoreSettingsDAO.set("store_phone", storePhoneField.getText().trim());
            StoreSettingsDAO.set("store_email", storeEmailField.getText().trim());
            StoreSettingsDAO.set("store_gstin", storeGstinField.getText().trim());
            StoreSettingsDAO.set("currency_symbol", currencySymbolField.getText().trim());
            StoreSettingsDAO.set("counter_name", counterNameField.getText().trim());
            StoreSettingsDAO.set("receipt_footer", receiptFooterField.getText().trim());
            StoreSettingsDAO.set("exchange_policy", exchangePolicyField.getText().trim());
            StoreSettingsDAO.set("receipt_paper_width", receiptPaperWidthCombo.getValue());
            StoreSettingsDAO.set("default_gst_percent", String.valueOf(defaultGst));

            String chosenPrinter = receiptPrinterCombo.getValue();
            StoreSettingsDAO.set("receipt_printer_name",
                    (chosenPrinter == null || chosenPrinter.equals("(Ask me each time)")) ? "" : chosenPrinter);

            StoreSettingsDAO.set("low_stock_alert_enabled", lowStockAlertCheckBox.isSelected() ? "1" : "0");
            StoreSettingsDAO.set("auto_backup_enabled", autoBackupEnabledCheckBox.isSelected() ? "1" : "0");
            StoreSettingsDAO.set("loyalty_enabled", loyaltyEnabledCheckBox.isSelected() ? "1" : "0");
            StoreSettingsDAO.set("points_per_100_rupees", String.valueOf(pointsPer100));
            StoreSettingsDAO.set("min_points_redemption", String.valueOf(minRedemption));

            showInfo("Settings saved. Some changes (like store name on receipts) take effect on your next sale.");
        } catch (NumberFormatException e) {
            showError("Points and GST% fields must be whole numbers");
        } catch (Exception e) {
            e.printStackTrace();
            showError("Error saving settings: " + e.getMessage());
        }
    }

    @FXML
    public void onBack() {
        ((Stage) storeNameField.getScene().getWindow()).close();
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private void showInfo(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, msg);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}