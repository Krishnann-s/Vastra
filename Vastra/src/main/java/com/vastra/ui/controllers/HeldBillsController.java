package com.vastra.ui.controllers;

import com.vastra.dao.HeldBillDAO;
import com.vastra.model.HeldBill;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.util.Optional;

/**
 * Lists bills parked with "Hold Sale" so the counter can pick one back up.
 * MainController opens this modally and reads getResumedBill() afterwards.
 */
public class HeldBillsController {

    @FXML private TableView<HeldBill> heldBillsTable;
    @FXML private TableColumn<HeldBill, String> labelColumn;
    @FXML private TableColumn<HeldBill, String> customerColumn;
    @FXML private TableColumn<HeldBill, Number> itemsColumn;
    @FXML private TableColumn<HeldBill, Number> totalColumn;
    @FXML private TableColumn<HeldBill, String> heldByColumn;
    @FXML private TableColumn<HeldBill, String> heldAtColumn;

    private final ObservableList<HeldBill> heldBills = FXCollections.observableArrayList();
    private HeldBill resumedBill = null;

    @FXML
    public void initialize() {
        labelColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getLabel()));
        customerColumn.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getCustomerName() != null ? d.getValue().getCustomerName() : "Walk-in"));
        itemsColumn.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().getItemCount()));
        totalColumn.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().getEstimatedTotal()));
        heldByColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getHeldBy()));
        heldAtColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getCreatedAt()));
        heldBillsTable.setItems(heldBills);

        refresh();
    }

    private void refresh() {
        try {
            heldBills.setAll(HeldBillDAO.getAllHeldBills());
        } catch (Exception e) {
            e.printStackTrace();
            showError("Error loading held bills: " + e.getMessage());
        }
    }

    @FXML
    public void onResume() {
        HeldBill selected = heldBillsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Select a held bill first");
            return;
        }
        if (selected.getItemCount() == 0) {
            showError("Every item in this held bill has since been deleted from your product list, so there's nothing left to resume. You can delete this held bill.");
            return;
        }
        resumedBill = selected;
        try {
            HeldBillDAO.deleteHeldBill(selected.getId());
        } catch (Exception e) {
            e.printStackTrace(); // non-fatal - the bill will just still show in the list next time
        }
        close();
    }

    @FXML
    public void onDelete() {
        HeldBill selected = heldBillsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Select a held bill first");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Delete this held bill? This cannot be undone.");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;

        try {
            HeldBillDAO.deleteHeldBill(selected.getId());
            refresh();
        } catch (Exception e) {
            e.printStackTrace();
            showError("Error deleting held bill: " + e.getMessage());
        }
    }

    @FXML
    public void onClose() {
        close();
    }

    private void close() {
        ((Stage) heldBillsTable.getScene().getWindow()).close();
    }

    /** Non-null after the dialog closes if the user chose to resume a bill. */
    public HeldBill getResumedBill() {
        return resumedBill;
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}