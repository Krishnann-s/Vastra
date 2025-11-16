package com.vastra.ui.controllers;

import com.vastra.dao.CustomerDAO;
import com.vastra.dao.ProductDAO;
import com.vastra.dao.SalesDAO;
import com.vastra.model.CartItem;
import com.vastra.model.Customer;
import com.vastra.model.Product;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.scene.control.*;

import java.util.ArrayList;
import java.util.Optional;


public class MainController {
    @FXML private TextField scanField;
    @FXML private TableView<?> cartTable;
    @FXML private TableColumn<CartItem, String> nameColumn;
    @FXML private TableColumn<CartItem, Integer> qtyColumn;
    @FXML private TableColumn<CartItem, Double> priceColumn;
    @FXML private TableColumn<CartItem, Double> totalColumn;
    @FXML private Label totalLabel;
    @FXML private Label subtotalLabel;
    @FXML private Label taxLabel;
    @FXML private Label customerNameLabel;
    @FXML private Label customerPointsLabel;
    @FXML private TextField discountField;

    private ObservableList<CartItem> cartItems = FXCollections.observableArrayList();
    private Customer currentCustomer = null;

    @FXML
    public void initialize(){
        setupCartTable();
        scanField.requestFocus();
        scanField.setOnAction(e -> handleBarcodeScan());
        discountField.setText("0");
    }

    private void setupCartTable() {
        nameColumn.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getProduct().getDisplayName()));

        qtyColumn.setCellValueFactory(data ->
                new SimpleIntegerProperty(data.getValue().getQuantity()).asObject());

        priceColumn.setCellValueFactory(data ->
                new SimpleDoubleProperty(data.getValue().getProduct().getSellPrice()).asObject());

        totalColumn.setCellValueFactory(data ->
                new SimpleDoubleProperty(data.getValue().getLineTotal()).asObject());

        cartTable.setItems(cartItems);
    }

    private void handleBarcodeScan() {
        String code = scanField.getText().trim();
        if (code.isEmpty()) return;

        try {
            Product product = ProductDAO.findById(code);
            if (product == null) {
                showError("Product not found: " + code);
                scanField.clear();
                return;
            }

            if (product.getStock() <= 0) {
                showError("Product out of stock: " + product.getDisplayName());
                scanField.clear();
                return;
            }

            addToCart(product);
            scanField.clear();
            updateTotals();

        } catch (Exception e) {
            showError("Error scanning product: " + e.getMessage());
        }
    }

    @FXML
    public void addToCart() {
        // Check if product already in cart
        for (CartItem item : cartItems) {
            if (item.getProduct().getId().equals(product.getId())) {
                if (item.getQuantity() < product.getStock()) {
                    item.incrementQuantity();
                    cartTable.refresh();
                    return;
                } else {
                    showError("Cannot add more. Only " + product.getStock() + " in stock");
                    return;
                }
            }
        }
        // Add new item
        cartItems.add(new CartItem(product, 1));
    }

    private void updateTotals() {
        double subtotal = 0;
        double tax = 0;

        for (CartItem item : cartItems) {
            subtotal += item.getLineTotal();
            tax += item.getTaxAmount();
        }

        double discount = 0;
        try {
            discount = Double.parseDouble(discountField.getText());
        } catch (NumberFormatException e) {
            discount = 0;
        }

        double total = subtotal - discount;

        subtotalLabel.setText(String.format("₹%.2f", subtotal));
        taxLabel.setText(String.format("₹%.2f", tax));
        totalLabel.setText(String.format("₹%.2f", total));
    }

    @FXML
    public void onAddProduct() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/vastra/ui/fxml/product_form.fxml"));
            Scene scene = new Scene(loader.load());
            Stage stage = new Stage();
            stage.setTitle("Add Product");
            stage.setScene(scene);
            stage.setResizable(false);
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    public void onAddCustomer() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add Customer");
        dialog.setHeaderText("Enter customer phone number");
        dialog.setContentText("Phone:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(phone -> {
            try {
                Customer customer = CustomerDAO.findByPhone(phone);
                if (customer == null) {
                    // Create new customer
                    TextInputDialog nameDialog = new TextInputDialog();
                    nameDialog.setTitle("New Customer");
                    nameDialog.setHeaderText("Customer not found. Create new?");
                    nameDialog.setContentText("Name:");

                    Optional<String> nameResult = nameDialog.showAndWait();
                    if (nameResult.isPresent()) {
                        customer = CustomerDAO.createCustomer(nameResult.get(), phone, "");
                    }
                }

                if (customer != null) {
                    currentCustomer = customer;
                    customerNameLabel.setText(customer.getName());
                    customerPointsLabel.setText(customer.getPoints() + " points");
                }

            } catch (Exception e) {
                showError("Error loading customer: " + e.getMessage());
            }
        });
    }

    @FXML
    public void onRedeemPoints() {
        if (currentCustomer == null) {
            showError("Please add customer first");
            return;
        }

        if (currentCustomer.getPoints() < 100) {
            showError("Customer needs at least 100 points to redeem");
            return;
        }

        TextInputDialog dialog = new TextInputDialog("100");
        dialog.setTitle("Redeem Points");
        dialog.setHeaderText("Customer has " + currentCustomer.getPoints() + " points");
        dialog.setContentText("Points to redeem (100 points = ₹100):");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(pointsStr -> {
            try {
                int points = Integer.parseInt(pointsStr);
                if (points > currentCustomer.getPoints()) {
                    showError("Customer doesn't have enough points");
                    return;
                }
                if (points % 100 != 0) {
                    showError("Points must be in multiples of 100");
                    return;
                }
                discountField.setText(String.valueOf(points));
                updateTotals();
            } catch (NumberFormatException e) {
                showError("Invalid points value");
            }
        });
    }

    @FXML
    public void onCompleteSale() {
        if (cartItems.isEmpty()) {
            showError("Cart is empty");
            return;
        }

        ChoiceDialog<String> paymentDialog = new ChoiceDialog<>("CASH", "CASH", "CARD", "UPI");
        paymentDialog.setTitle("Payment Method");
        paymentDialog.setHeaderText("Select payment method");

        Optional<String> paymentResult = paymentDialog.showAndWait();
        if (!paymentResult.isPresent()) return;

        try {
            int discountCents = (int) (Double.parseDouble(discountField.getText()) * 100);
            String customerId = currentCustomer != null ? currentCustomer.getId() : null;

            // Redeem points if used
            if (currentCustomer != null && discountCents > 0) {
                int pointsToRedeem = discountCents / 100; // 1 rupee = 1 point
                CustomerDAO.redeemPoints(currentCustomer.getId(), pointsToRedeem);
            }

            String saleId = SalesDAO.completeSale(
                    new ArrayList<>(cartItems),
                    customerId,
                    discountCents,
                    paymentResult.get()
            );

            showSuccess("Sale completed! ID: " + saleId);
            clearCart();

        } catch (Exception e) {
            showError("Error completing sale: " + e.getMessage());
        }
    }

    @FXML
    public void onClearCart() {
        clearCart();
    }

    private void clearCart() {
        cartItems.clear();
        currentCustomer = null;
        customerNameLabel.setText("");
        customerPointsLabel.setText("");
        discountField.setText("0");
        updateTotals();
        scanField.requestFocus();
    }

    private void showError(String msg) {
        new Alert(Alert.AlertType.ERROR, msg).show();
    }

    private void showSuccess(String msg) {
        new Alert(Alert.AlertType.INFORMATION, msg).show();
    }
}
