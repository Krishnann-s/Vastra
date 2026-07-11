package com.vastra.ui.controllers;

import com.vastra.dao.ReturnDAO;
import com.vastra.dao.SalesDAO;
import com.vastra.model.ReturnRecord;
import com.vastra.model.Sale;
import com.vastra.model.SaleItem;
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

import java.util.List;
import java.util.Optional;

public class ReturnController {

    @FXML private TextField invoiceField;
    @FXML private Label saleInfoLabel;
    @FXML private TableView<ReturnLineRow> itemsTable;
    @FXML private TableColumn<ReturnLineRow, String> nameColumn;
    @FXML private TableColumn<ReturnLineRow, Integer> soldQtyColumn;
    @FXML private TableColumn<ReturnLineRow, Integer> returnedQtyColumn;
    @FXML private TableColumn<ReturnLineRow, Integer> returnQtyColumn;
    @FXML private TableColumn<ReturnLineRow, Double> unitPriceColumn;
    @FXML private TableColumn<ReturnLineRow, Double> refundColumn;
    @FXML private TextField reasonField;
    @FXML private CheckBox restockCheckBox;
    @FXML private Label refundTotalLabel;

    @FXML private TableView<ReturnRecord> recentReturnsTable;
    @FXML private TableColumn<ReturnRecord, String> rDateColumn;
    @FXML private TableColumn<ReturnRecord, String> rInvoiceColumn;
    @FXML private TableColumn<ReturnRecord, String> rCustomerColumn;
    @FXML private TableColumn<ReturnRecord, String> rReasonColumn;
    @FXML private TableColumn<ReturnRecord, Double> rAmountColumn;
    @FXML private TableColumn<ReturnRecord, String> rStatusColumn;

    private Sale currentSale;
    private final ObservableList<ReturnLineRow> rows = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupItemsTable();
        setupRecentReturnsTable();
        loadRecentReturns();
    }

    private void setupItemsTable() {
        nameColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getSaleItem().getProductName()));
        soldQtyColumn.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().getSaleItem().getQty()).asObject());
        returnedQtyColumn.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().getAlreadyReturned()).asObject());

        returnQtyColumn.setCellValueFactory(d -> d.getValue().returnQtyProperty().asObject());
        returnQtyColumn.setCellFactory(TextFieldTableCell.forTableColumn(new IntegerStringConverter()));
        returnQtyColumn.setOnEditCommit(event -> {
            ReturnLineRow row = event.getRowValue();
            int newQty = event.getNewValue();
            if (newQty < 0 || newQty > row.getReturnableQty()) {
                showError("Return qty must be between 0 and " + row.getReturnableQty());
                itemsTable.refresh();
                return;
            }
            row.setReturnQty(newQty);
            itemsTable.refresh();
            updateRefundTotal();
        });

        unitPriceColumn.setCellValueFactory(d -> new SimpleDoubleProperty(
                d.getValue().getSaleItem().getUnitPriceCents() / 100.0).asObject());
        refundColumn.setCellValueFactory(d -> new SimpleDoubleProperty(
                d.getValue().getRefundCents() / 100.0).asObject());

        itemsTable.setItems(rows);
        itemsTable.setEditable(true);
    }

    private void setupRecentReturnsTable() {
        rDateColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getReturnDate()));
        rInvoiceColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getInvoiceNumber()));
        rCustomerColumn.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getCustomerName() != null ? d.getValue().getCustomerName() : "Walk-in"));
        rReasonColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getReason()));
        rAmountColumn.setCellValueFactory(d -> new SimpleDoubleProperty(
                d.getValue().getRefundAmountCents() / 100.0).asObject());
        rStatusColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getStatus()));
    }

    private void loadRecentReturns() {
        try {
            recentReturnsTable.setItems(FXCollections.observableArrayList(ReturnDAO.getRecentReturns(30)));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    public void onFindBill() {
        String invoice = invoiceField.getText() != null ? invoiceField.getText().trim() : "";
        if (invoice.isEmpty()) {
            showError("Enter an invoice/bill number");
            return;
        }

        try {
            Sale sale = SalesDAO.findByInvoiceNumber(invoice);
            if (sale == null) {
                showError("No bill found with invoice number: " + invoice);
                currentSale = null;
                rows.clear();
                saleInfoLabel.setText("");
                updateRefundTotal();
                return;
            }

            currentSale = sale;
            saleInfoLabel.setText(String.format("%s | %s | Total: ₹%.2f",
                    sale.getCustomerName() != null ? sale.getCustomerName() : "Walk-in",
                    sale.getTs(), sale.getTotalCents() / 100.0));

            List<SaleItem> items = SalesDAO.getSaleItems(sale.getId());
            rows.clear();
            for (SaleItem item : items) {
                int alreadyReturned = ReturnDAO.getReturnedQtyForSaleItem(item.getId());
                rows.add(new ReturnLineRow(item, alreadyReturned));
            }
            updateRefundTotal();

        } catch (Exception e) {
            e.printStackTrace();
            showError("Error looking up bill: " + e.getMessage());
        }
    }

    private void updateRefundTotal() {
        int totalCents = 0;
        for (ReturnLineRow row : rows) totalCents += row.getRefundCents();
        refundTotalLabel.setText(String.format("₹%.2f", totalCents / 100.0));
    }

    @FXML
    public void onProcessReturn() {
        if (currentSale == null) {
            showError("Find a bill first");
            return;
        }

        List<ReturnLineRow> selected = rows.stream().filter(r -> r.getReturnQty() > 0).toList();
        if (selected.isEmpty()) {
            showError("Enter a return quantity for at least one item");
            return;
        }

        String reason = reasonField.getText() != null ? reasonField.getText().trim() : "";
        if (reason.isEmpty()) {
            showError("Enter a reason for the return");
            return;
        }

        int totalRefundCents = selected.stream().mapToInt(ReturnLineRow::getRefundCents).sum();

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Return");
        confirm.setHeaderText(String.format("Refund ₹%.2f for %d item line(s)?", totalRefundCents / 100.0, selected.size()));
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;

        try {
            List<ReturnDAO.ReturnLine> lines = selected.stream()
                    .map(r -> new ReturnDAO.ReturnLine(
                            r.getSaleItem().getId(), r.getSaleItem().getProductId(),
                            r.getReturnQty(), r.getRefundCents()))
                    .toList();

            ReturnDAO.processReturn(currentSale.getId(), currentSale.getCustomerId(), reason,
                    lines, restockCheckBox.isSelected(), "Admin", null);

            showSuccess(String.format("Return processed. Refund: ₹%.2f", totalRefundCents / 100.0));

            onFindBill(); // refresh returned-qty state for the same bill
            loadRecentReturns();

        } catch (Exception e) {
            e.printStackTrace();
            showError("Error processing return: " + e.getMessage());
        }
    }

    @FXML
    public void onCancel() {
        ((Stage) invoiceField.getScene().getWindow()).close();
    }

    private void showError(String msg) {
        new Alert(Alert.AlertType.ERROR, msg).showAndWait();
    }

    private void showSuccess(String msg) {
        new Alert(Alert.AlertType.INFORMATION, msg).showAndWait();
    }

    /** Wraps a sold line item with how much has already been returned, plus an editable "return now" qty. */
    public static class ReturnLineRow {
        private final SaleItem saleItem;
        private final int alreadyReturned;
        private final SimpleIntegerProperty returnQty = new SimpleIntegerProperty(0);

        public ReturnLineRow(SaleItem saleItem, int alreadyReturned) {
            this.saleItem = saleItem;
            this.alreadyReturned = alreadyReturned;
        }

        public SaleItem getSaleItem() { return saleItem; }
        public int getAlreadyReturned() { return alreadyReturned; }
        public int getReturnableQty() { return saleItem.getQty() - alreadyReturned; }
        public int getReturnQty() { return returnQty.get(); }
        public void setReturnQty(int v) { returnQty.set(v); }
        public SimpleIntegerProperty returnQtyProperty() { return returnQty; }
        public int getRefundCents() { return getReturnQty() * saleItem.getUnitPriceCents(); }
    }
}