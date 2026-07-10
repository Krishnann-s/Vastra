package com.vastra.ui.controllers;

import com.vastra.dao.ProductDAO;
import com.vastra.dao.PurchaseDAO;
import com.vastra.model.Product;
import com.vastra.model.PurchaseItem;
import com.vastra.model.Supplier;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.stage.Stage;
import javafx.util.converter.IntegerStringConverter;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class PurchaseEntryController {

    @FXML private Label supplierNameLabel;
    @FXML private TextField invoiceNumberField;
    @FXML private DatePicker invoiceDatePicker;
    @FXML private DatePicker dueDatePicker;
    @FXML private TextArea notesField;

    @FXML private TextField productSearchField;
    @FXML private TableView<Product> searchResultsTable;
    @FXML private TableColumn<Product, String> resultNameColumn;
    @FXML private TableColumn<Product, Number> resultStockColumn;

    @FXML private TableView<PurchaseItem> itemsTable;
    @FXML private TableColumn<PurchaseItem, String> itemNameColumn;
    @FXML private TableColumn<PurchaseItem, Integer> itemQtyColumn;
    @FXML private TableColumn<PurchaseItem, Double> itemCostColumn;
    @FXML private TableColumn<PurchaseItem, Double> itemTotalColumn;
    @FXML private TableColumn<PurchaseItem, Void> itemRemoveColumn;

    @FXML private Label totalLabel;
    @FXML private TextField amountPaidNowField;
    @FXML private ComboBox<String> paymentModeCombo;

    private final ObservableList<Product> searchResults = FXCollections.observableArrayList();
    private final ObservableList<PurchaseItem> items = FXCollections.observableArrayList();
    private Supplier supplier;

    /** Call this right after loading the FXML, before showing the window. */
    public void setSupplier(Supplier supplier) {
        this.supplier = supplier;
        supplierNameLabel.setText(supplier.getName() + "  (Currently owed: \u20B9" + String.format("%.2f", supplier.getDue()) + ")");
    }

    @FXML
    public void initialize() {
        invoiceDatePicker.setValue(LocalDate.now());
        dueDatePicker.setValue(LocalDate.now().plusDays(30));
        paymentModeCombo.setItems(FXCollections.observableArrayList("CASH", "CARD", "UPI", "BANK TRANSFER"));
        paymentModeCombo.setValue("CASH");
        amountPaidNowField.setText("0");

        resultNameColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getFullDisplayName()));
        resultStockColumn.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().getStock()));
        searchResultsTable.setItems(searchResults);

        itemNameColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getProductName()));

        itemQtyColumn.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().getQty()).asObject());
        itemQtyColumn.setCellFactory(TextFieldTableCell.forTableColumn(new IntegerStringConverter()));
        itemQtyColumn.setOnEditCommit(e -> {
            e.getRowValue().setQty(e.getNewValue());
            itemsTable.refresh();
            updateTotal();
        });

        itemCostColumn.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().getCostPrice()).asObject());
        itemCostColumn.setCellFactory(col -> new TextFieldTableCell<>(new javafx.util.converter.DoubleStringConverter()));
        itemCostColumn.setOnEditCommit(e -> {
            e.getRowValue().setCostPriceCents((int) Math.round(e.getNewValue() * 100));
            itemsTable.refresh();
            updateTotal();
        });

        itemTotalColumn.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().getLineTotal()).asObject());

        itemRemoveColumn.setCellFactory(col -> new TableCell<>() {
            private final Button removeBtn = new Button("Remove");
            { removeBtn.setOnAction(e -> {
                items.remove(getTableView().getItems().get(getIndex()));
                updateTotal();
            }); }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : removeBtn);
            }
        });

        itemsTable.setItems(items);
        itemsTable.setEditable(true);
    }

    @FXML
    public void onSearchProduct() {
        try {
            String term = productSearchField.getText() == null ? "" : productSearchField.getText().trim();
            List<Product> results = term.isEmpty() ? ProductDAO.getAllProducts() : ProductDAO.searchByName(term);
            searchResults.setAll(results);
        } catch (Exception e) {
            showError("Search failed: " + e.getMessage());
        }
    }

    @FXML
    public void onAddItem() {
        Product selected = searchResultsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Select a product first");
            return;
        }
        for (PurchaseItem item : items) {
            if (item.getProductId().equals(selected.getId())) {
                item.setQty(item.getQty() + 1);
                itemsTable.refresh();
                updateTotal();
                return;
            }
        }
        // default cost price = product's current purchase price if set, else 0 - editable either way
        items.add(new PurchaseItem(selected, 1, selected.getPurchasePriceCents()));
        updateTotal();
    }

    private void updateTotal() {
        double total = items.stream().mapToDouble(PurchaseItem::getLineTotal).sum();
        totalLabel.setText(String.format("\u20B9%.2f", total));
    }

    @FXML
    public void onSave() {
        if (items.isEmpty()) {
            showError("Add at least one item");
            return;
        }
        try {
            int amountPaidNowCents = 0;
            if (amountPaidNowField.getText() != null && !amountPaidNowField.getText().isBlank()) {
                amountPaidNowCents = (int) Math.round(Double.parseDouble(amountPaidNowField.getText()) * 100);
            }

            String purchaseId = PurchaseDAO.recordPurchase(
                    supplier.getId(),
                    invoiceNumberField.getText(),
                    invoiceDatePicker.getValue().toString(),
                    items,
                    dueDatePicker.getValue() != null ? dueDatePicker.getValue().toString() : null,
                    notesField.getText(),
                    amountPaidNowCents,
                    paymentModeCombo.getValue()
            );

            Alert alert = new Alert(Alert.AlertType.INFORMATION,
                    "Purchase recorded. Stock updated for " + items.size() + " product(s).");
            alert.setHeaderText(null);
            alert.showAndWait();

            closeWindow();
        } catch (NumberFormatException e) {
            showError("Enter a valid amount paid now");
        } catch (Exception e) {
            e.printStackTrace();
            showError("Error saving purchase: " + e.getMessage());
        }
    }

    @FXML
    public void onCancel() {
        closeWindow();
    }

    private void closeWindow() {
        Stage stage = (Stage) invoiceNumberField.getScene().getWindow();
        stage.close();
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}