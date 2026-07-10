package com.vastra.ui.controllers;

import com.vastra.dao.SupplierDAO;
import com.vastra.dao.SupplierPaymentDAO;
import com.vastra.model.LedgerEntry;
import com.vastra.model.Supplier;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class SupplierController {

    @FXML private TextField searchField;
    @FXML private TableView<Supplier> supplierTable;
    @FXML private TableColumn<Supplier, String> nameColumn;
    @FXML private TableColumn<Supplier, String> phoneColumn;
    @FXML private TableColumn<Supplier, Double> dueColumn;

    @FXML private TextField newNameField, newPhoneField, newAddressField, newGstField, newOpeningBalanceField;

    @FXML private Label selectedSupplierLabel;
    @FXML private Label totalDueLabel;
    @FXML private TableView<LedgerEntry> ledgerTable;
    @FXML private TableColumn<LedgerEntry, String> ledgerDateColumn;
    @FXML private TableColumn<LedgerEntry, String> ledgerTypeColumn;
    @FXML private TableColumn<LedgerEntry, String> ledgerRefColumn;
    @FXML private TableColumn<LedgerEntry, Double> ledgerDebitColumn;
    @FXML private TableColumn<LedgerEntry, Double> ledgerCreditColumn;
    @FXML private TableColumn<LedgerEntry, Double> ledgerBalanceColumn;

    private final ObservableList<Supplier> suppliers = FXCollections.observableArrayList();
    private final ObservableList<LedgerEntry> ledgerEntries = FXCollections.observableArrayList();
    private Supplier selectedSupplier;

    @FXML
    public void initialize() {
        nameColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getName()));
        phoneColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getPhone()));
        dueColumn.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().getDue()).asObject());
        dueColumn.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Double value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) { setText(null); return; }
                setText(String.format("\u20B9%.2f", value));
                setTextFill(value > 0 ? Color.web("#D32F2F") : Color.web("#388E3C"));
            }
        });
        supplierTable.setItems(suppliers);

        supplierTable.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (sel != null) loadLedger(sel);
        });

        ledgerDateColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDate()));
        ledgerTypeColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getType().toString()));
        ledgerRefColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getRefNo()));
        ledgerDebitColumn.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().getDebit()).asObject());
        ledgerCreditColumn.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().getCredit()).asObject());
        ledgerBalanceColumn.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().getRunningBalance()).asObject());
        ledgerTable.setItems(ledgerEntries);

        refreshSupplierList();
    }

    private void refreshSupplierList() {
        try {
            List<Supplier> list = SupplierDAO.getAllActive();
            for (Supplier s : list) {
                s.setDueCents(SupplierDAO.getCurrentDueCents(s.getId()));
            }
            suppliers.setAll(list);
        } catch (Exception e) {
            showError("Error loading suppliers: " + e.getMessage());
        }
    }

    private void loadLedger(Supplier supplier) {
        this.selectedSupplier = supplier;
        try {
            selectedSupplierLabel.setText(supplier.getName() +
                    (supplier.getPhone() != null && !supplier.getPhone().isEmpty() ? " | " + supplier.getPhone() : ""));
            int dueCents = SupplierDAO.getCurrentDueCents(supplier.getId());
            totalDueLabel.setText(String.format("Currently owed: \u20B9%.2f", dueCents / 100.0));
            ledgerEntries.setAll(SupplierDAO.getLedger(supplier.getId()));
        } catch (Exception e) {
            showError("Error loading ledger: " + e.getMessage());
        }
    }

    @FXML
    public void onSearch() {
        try {
            String term = searchField.getText() == null ? "" : searchField.getText().trim();
            List<Supplier> list = term.isEmpty() ? SupplierDAO.getAllActive() : SupplierDAO.searchByName(term);
            for (Supplier s : list) {
                s.setDueCents(SupplierDAO.getCurrentDueCents(s.getId()));
            }
            suppliers.setAll(list);
        } catch (Exception e) {
            showError("Search failed: " + e.getMessage());
        }
    }

    @FXML
    public void onAddSupplier() {
        if (newNameField.getText() == null || newNameField.getText().trim().isEmpty()) {
            showError("Supplier name is required");
            return;
        }
        try {
            int openingCents = 0;
            if (newOpeningBalanceField.getText() != null && !newOpeningBalanceField.getText().isBlank()) {
                openingCents = (int) Math.round(Double.parseDouble(newOpeningBalanceField.getText()) * 100);
            }
            SupplierDAO.createSupplier(
                    newNameField.getText().trim(),
                    newPhoneField.getText(),
                    newAddressField.getText(),
                    newGstField.getText(),
                    openingCents,
                    null
            );
            newNameField.clear(); newPhoneField.clear(); newAddressField.clear();
            newGstField.clear(); newOpeningBalanceField.clear();
            refreshSupplierList();
        } catch (NumberFormatException e) {
            showError("Enter a valid opening balance");
        } catch (Exception e) {
            showError("Error adding supplier: " + e.getMessage());
        }
    }

    @FXML
    public void onRecordPurchase() {
        if (selectedSupplier == null) {
            showError("Select a supplier first");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/vastra/ui/fxml/purchase_entry.fxml"));
            Scene scene = new Scene(loader.load());
            PurchaseEntryController controller = loader.getController();
            controller.setSupplier(selectedSupplier);

            Stage stage = new Stage();
            stage.setTitle("New Purchase - " + selectedSupplier.getName());
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(scene);
            stage.showAndWait();

            refreshSupplierList();
            loadLedger(selectedSupplier);
        } catch (Exception e) {
            e.printStackTrace();
            showError("Error opening purchase entry: " + e.getMessage());
        }
    }

    @FXML
    public void onRecordPayment() {
        if (selectedSupplier == null) {
            showError("Select a supplier first");
            return;
        }

        TextInputDialog amountDialog = new TextInputDialog();
        amountDialog.setTitle("Record Payment");
        amountDialog.setHeaderText("Paying " + selectedSupplier.getName() +
                "\nCurrently owed: \u20B9" + String.format("%.2f", selectedSupplier.getDue()));
        amountDialog.setContentText("Amount paid (\u20B9):");
        Optional<String> amountResult = amountDialog.showAndWait();
        if (amountResult.isEmpty() || amountResult.get().isBlank()) return;

        try {
            int amountCents = (int) Math.round(Double.parseDouble(amountResult.get()) * 100);
            if (amountCents <= 0) {
                showError("Enter a positive amount");
                return;
            }

            ChoiceDialog<String> modeDialog = new ChoiceDialog<>("CASH", "CASH", "CARD", "UPI", "BANK TRANSFER");
            modeDialog.setTitle("Payment Mode");
            modeDialog.setHeaderText("How was this paid?");
            Optional<String> modeResult = modeDialog.showAndWait();
            String mode = modeResult.orElse("CASH");

            SupplierPaymentDAO.recordPayment(selectedSupplier.getId(), null, amountCents,
                    LocalDate.now().toString(), mode, null);

            refreshSupplierList();
            loadLedger(selectedSupplier);
        } catch (NumberFormatException e) {
            showError("Enter a valid amount");
        } catch (Exception e) {
            showError("Error recording payment: " + e.getMessage());
        }
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}