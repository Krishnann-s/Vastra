package com.vastra.ui.controllers;

import com.vastra.dao.CustomerDAO;
import com.vastra.dao.CustomerPaymentDAO;
import com.vastra.model.Customer;
import com.vastra.model.CustomerLedgerEntry;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.paint.Color;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class CustomerLedgerController {

    @FXML private TextField searchField;
    @FXML private TableView<Customer> customerTable;
    @FXML private TableColumn<Customer, String> nameColumn;
    @FXML private TableColumn<Customer, String> phoneColumn;
    @FXML private TableColumn<Customer, Double> dueColumn;

    @FXML private Label selectedCustomerLabel;
    @FXML private Label totalDueLabel;
    @FXML private TableView<CustomerLedgerEntry> ledgerTable;
    @FXML private TableColumn<CustomerLedgerEntry, String> ledgerDateColumn;
    @FXML private TableColumn<CustomerLedgerEntry, String> ledgerTypeColumn;
    @FXML private TableColumn<CustomerLedgerEntry, String> ledgerRefColumn;
    @FXML private TableColumn<CustomerLedgerEntry, Double> ledgerDebitColumn;
    @FXML private TableColumn<CustomerLedgerEntry, Double> ledgerCreditColumn;
    @FXML private TableColumn<CustomerLedgerEntry, Double> ledgerBalanceColumn;

    private final ObservableList<Customer> customers = FXCollections.observableArrayList();
    private final ObservableList<CustomerLedgerEntry> ledgerEntries = FXCollections.observableArrayList();
    private Customer selectedCustomer;

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
        customerTable.setItems(customers);
        customerTable.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (sel != null) loadLedger(sel);
        });

        ledgerDateColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDate()));
        ledgerTypeColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getType().toString()));
        ledgerRefColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getRefNo()));
        ledgerDebitColumn.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().getDebit()).asObject());
        ledgerCreditColumn.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().getCredit()).asObject());
        ledgerBalanceColumn.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().getRunningBalance()).asObject());
        ledgerTable.setItems(ledgerEntries);

        refreshCustomerList();
    }

    private void refreshCustomerList() {
        try {
            List<Customer> list = CustomerDAO.getAllCustomers();
            for (Customer c : list) c.setDueCents(CustomerDAO.getCurrentDueCents(c.getId()));
            customers.setAll(list);
        } catch (Exception e) {
            showError("Error loading customers: " + e.getMessage());
        }
    }

    private void loadLedger(Customer customer) {
        this.selectedCustomer = customer;
        try {
            selectedCustomerLabel.setText(customer.getName() +
                    (customer.getPhone() != null && !customer.getPhone().isEmpty() ? " | " + customer.getPhone() : ""));
            int dueCents = CustomerDAO.getCurrentDueCents(customer.getId());
            totalDueLabel.setText(String.format("Currently owes: \u20B9%.2f", dueCents / 100.0));
            ledgerEntries.setAll(CustomerDAO.getLedger(customer.getId()));
        } catch (Exception e) {
            showError("Error loading ledger: " + e.getMessage());
        }
    }

    @FXML
    public void onSearch() {
        try {
            String term = searchField.getText() == null ? "" : searchField.getText().trim();
            List<Customer> list = term.isEmpty() ? CustomerDAO.getAllCustomers() : CustomerDAO.searchByName(term);
            for (Customer c : list) c.setDueCents(CustomerDAO.getCurrentDueCents(c.getId()));
            customers.setAll(list);
        } catch (Exception e) {
            showError("Search failed: " + e.getMessage());
        }
    }

    @FXML
    public void onRecordPayment() {
        if (selectedCustomer == null) {
            showError("Select a customer first");
            return;
        }

        TextInputDialog amountDialog = new TextInputDialog();
        amountDialog.setTitle("Record Payment");
        amountDialog.setHeaderText(selectedCustomer.getName() +
                "\nCurrently owes: \u20B9" + String.format("%.2f", selectedCustomer.getDue()));
        amountDialog.setContentText("Amount received (\u20B9):");
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
            String mode = modeDialog.showAndWait().orElse("CASH");

            CustomerPaymentDAO.recordPayment(selectedCustomer.getId(), null, amountCents,
                    LocalDate.now().toString(), mode, null);

            refreshCustomerList();
            loadLedger(selectedCustomer);
        } catch (NumberFormatException e) {
            showError("Enter a valid amount");
        } catch (Exception e) {
            showError("Error recording payment: " + e.getMessage());
        }
    }

    private void showError(String msg) {
        new Alert(Alert.AlertType.ERROR, msg).showAndWait();
    }
}