package com.vastra.ui.controllers;

import com.vastra.dao.CustomerDAO;
import com.vastra.model.Customer;
import com.vastra.util.Session;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.stage.Stage;

import java.util.List;
import java.util.Optional;

public class CustomerSelectController {

    @FXML private TextField searchField;
    @FXML private TableView<Customer> resultsTable;
    @FXML private TableColumn<Customer, String> nameColumn;
    @FXML private TableColumn<Customer, String> phoneColumn;
    @FXML private TableColumn<Customer, Double> pointsColumn;
    @FXML private TableColumn<Customer, Double> dueColumn;
    @FXML private TableColumn<Customer, String> addressColumn;
    @FXML private TableColumn<Customer, Void> actionColumn;

    @FXML private TextField newNameField;
    @FXML private TextField newPhoneField;
    @FXML private TextField newAddressField;
    @FXML private TextField newCityField;
    @FXML private TextField newEmailField;
    @FXML private TextField newPincodeField;

    private final ObservableList<Customer> results = FXCollections.observableArrayList();

    private Customer selectedCustomer;
    private boolean walkInChosen = false;

    public Customer getSelectedCustomer() { return selectedCustomer; }
    public boolean isWalkInChosen() { return walkInChosen; }

    /** Called by MainController to pre-fill and immediately run a search when opened from the POS screen's quick-search field. */
    public void setInitialSearchTerm(String term) {
        if (searchField != null) {
            searchField.setText(term);
            onSearch();
        }
    }

    @FXML
    public void initialize() {
        nameColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getName()));
        phoneColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getPhone()));
        pointsColumn.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().getPoints()).asObject());
        addressColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getFullAddress()));

        dueColumn.setCellValueFactory(d -> {
            try {
                return new SimpleDoubleProperty(CustomerDAO.getCurrentDueCents(d.getValue().getId()) / 100.0).asObject();
            } catch (Exception e) {
                return new SimpleDoubleProperty(0).asObject();
            }
        });

        actionColumn.setCellFactory(col -> new TableCell<>() {
            private final Button deleteBtn = new Button("🗑");
            {
                deleteBtn.setStyle("-fx-background-color: #D32F2F; -fx-text-fill: white; -fx-padding: 2 8 2 8; -fx-font-size: 11px;");
                deleteBtn.setTooltip(new Tooltip("Delete this customer"));
                deleteBtn.setOnAction(e -> onDeleteCustomer(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                // Only admins see the delete button - cashiers use this same screen just to pick a customer
                setGraphic((empty || !Session.isAdmin()) ? null : deleteBtn);
            }
        });

        resultsTable.setItems(results);
        resultsTable.setRowFactory(tv -> {
            TableRow<Customer> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty() && event.getButton() == MouseButton.PRIMARY) {
                    selectedCustomer = row.getItem();
                    closeWindow();
                }
            });
            return row;
        });

        onSearch(); // show everyone by default so staff can browse without typing
    }

    @FXML
    public void onSearch() {
        try {
            String term = searchField.getText() == null ? "" : searchField.getText().trim();
            List<Customer> list = term.isEmpty() ? CustomerDAO.getAllCustomers() : CustomerDAO.searchByName(term);
            results.setAll(list);
        } catch (Exception e) {
            showError("Search failed: " + e.getMessage());
        }
    }

    @FXML
    public void onSelectHighlighted() {
        Customer selected = resultsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Select a customer from the list, or use Quick Add below");
            return;
        }
        selectedCustomer = selected;
        closeWindow();
    }

    @FXML
    public void onAddNewCustomer() {
        String name = newNameField.getText() == null ? "" : newNameField.getText().trim();
        String phone = newPhoneField.getText() == null ? "" : newPhoneField.getText().trim();

        if (name.isEmpty()) {
            showError("Name is required");
            return;
        }
        if (phone.isEmpty()) {
            showError("Mobile number is required");
            return;
        }

        try {
            Customer existing = CustomerDAO.findByPhone(phone);
            if (existing != null) {
                showError("A customer with this mobile number already exists: " + existing.getName());
                return;
            }

            Customer created = CustomerDAO.createCustomer(name, phone, newEmailField.getText());
            created.setAddress(newAddressField.getText());
            created.setCity(newCityField.getText());
            created.setPincode(newPincodeField.getText());
            CustomerDAO.updateCustomerDetails(created);

            selectedCustomer = created;
            closeWindow();
        } catch (Exception e) {
            showError("Error adding customer: " + e.getMessage());
        }
    }

    private void onDeleteCustomer(Customer c) {
        if (!Session.requireAdmin()) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Customer");
        confirm.setHeaderText("Delete " + c.getName() + "?");
        confirm.setContentText("Their sales history is kept, but they'll no longer appear in searches or the customer list.");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;

        try {
            CustomerDAO.deactivateCustomer(c.getId());
            onSearch();
        } catch (Exception e) {
            showError("Error deleting customer: " + e.getMessage());
        }
    }

    @FXML
    public void onWalkIn() {
        walkInChosen = true;
        closeWindow();
    }

    private void closeWindow() {
        ((Stage) searchField.getScene().getWindow()).close();
    }

    private void showError(String msg) {
        new Alert(Alert.AlertType.ERROR, msg).showAndWait();
    }
}