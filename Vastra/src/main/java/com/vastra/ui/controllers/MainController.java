package com.vastra.ui.controllers;

import com.vastra.dao.CustomerDAO;
import com.vastra.dao.ProductDAO;
import com.vastra.dao.SalesDAO;
import com.vastra.dao.SupplierDAO;
import com.vastra.model.CartItem;
import com.vastra.model.Customer;
import com.vastra.model.PayableReminder;
import com.vastra.model.Product;
import com.vastra.model.SaleReceiptData;
import com.vastra.util.BackupUtil;
import com.vastra.util.BarcodeScanner;
import com.vastra.util.ThermalPrinterUtil;
import com.vastra.util.WhatsAppUtil;
import com.vastra.util.IconUtil;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.converter.IntegerStringConverter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MainController {
    @FXML private TableView<CartItem> cartTable;
    @FXML private TableColumn<CartItem, String> nameColumn;
    @FXML private TableColumn<CartItem, Integer> qtyColumn;
    @FXML private TableColumn<CartItem, Double> priceColumn;
    @FXML private TableColumn<CartItem, Double> taxColumn;
    @FXML private TableColumn<CartItem, Double> totalColumn;
    @FXML private TableColumn<CartItem, Void> actionColumn;

    @FXML private Label totalLabel;
    @FXML private Label subtotalLabel;
    @FXML private Label taxLabel;
    @FXML private Label customerNameLabel;
    @FXML private Label customerPointsLabel;
    @FXML private TextField discountField;
    @FXML private Label lowStockAlertLabel;
    @FXML private Label paymentReminderLabel;
    @FXML private Label backupReminderLabel;
    @FXML private TextField cashierNameField;

    private ObservableList<CartItem> cartItems = FXCollections.observableArrayList();
    private Customer currentCustomer = null;
    private double pointsRedeemedThisSale = 0;
    private BarcodeScanner barcodeScanner;
    private Stage primaryStage; // Store reference to main stage for focus handling
    private SaleReceiptData lastCompletedSale;

    @FXML
    public void initialize() {
        setupCartTable();
        setupBarcodeScanner();
        setupKeyboardShortcuts();
        checkLowStockAlerts();
        checkPaymentReminders();
        checkBackupReminder();

        if (discountField != null) {
            discountField.setText("0");
            discountField.textProperty().addListener((obs, old, newVal) -> updateTotals());
        }

        // Setup global key listener for barcode scanner
        Platform.runLater(() -> {
            if (cartTable != null && cartTable.getScene() != null) {
                primaryStage = (Stage) cartTable.getScene().getWindow();
                setupGlobalBarcodeListener();
            }
        });
    }

    private void setupCartTable() {
        nameColumn.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getProduct().getFullDisplayName()));

        qtyColumn.setCellValueFactory(data ->
                new SimpleIntegerProperty(data.getValue().getQuantity()).asObject());

        // Make quantity column editable
        qtyColumn.setCellFactory(TextFieldTableCell.forTableColumn(new IntegerStringConverter()));
        qtyColumn.setOnEditCommit(event -> {
            CartItem item = event.getRowValue();
            int newQty = event.getNewValue();
            if (newQty > 0 && newQty <= item.getProduct().getStock()) {
                item.setQuantity(newQty);
                updateTotals();
            } else {
                showError("Invalid quantity. Available stock: " + item.getProduct().getStock());
                cartTable.refresh();
            }
        });

        priceColumn.setCellValueFactory(data ->
                new SimpleDoubleProperty(data.getValue().getProduct().getSellPrice()).asObject());

        taxColumn.setCellValueFactory(data ->
                new SimpleDoubleProperty(data.getValue().getTaxAmount()).asObject());

        totalColumn.setCellValueFactory(data ->
                new SimpleDoubleProperty(data.getValue().getLineTotal()).asObject());

        // Add action column with remove button
        actionColumn.setCellFactory(param -> new TableCell<>() {
            private final Button deleteButton = new Button("Remove");
            {
                deleteButton.setOnAction(event -> {
                    CartItem item = getTableView().getItems().get(getIndex());
                    cartItems.remove(item);
                    updateTotals();
                });
                deleteButton.setStyle("-fx-background-color: #F44336; -fx-text-fill: white;");
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : deleteButton);
            }
        });

        cartTable.setItems(cartItems);
        cartTable.setEditable(true);
    }

    private void setupBarcodeScanner() {
        // Barcode scanner will work globally - no need for specific text field
        // The scanner acts as keyboard input and we'll capture it at window level
    }

    /**
     * Setup global keyboard listener to capture barcode scanner input
     */
    private void setupGlobalBarcodeListener() {
        if (primaryStage == null || primaryStage.getScene() == null) return;

        StringBuilder scanBuffer = new StringBuilder();
        final long[] lastKeyTime = {0};

        primaryStage.getScene().setOnKeyPressed(event -> {
            // If the user is actively typing into any text field (discount, search
            // boxes, dialogs, etc.), let it type normally - don't treat it as a scan.
            javafx.scene.Node focusOwner = primaryStage.getScene().getFocusOwner();
            if (focusOwner instanceof javafx.scene.control.TextInputControl) {
                return;
            }

            long currentTime = System.currentTimeMillis();

            // If more than 100ms between keys, reset buffer (manual typing)
            if (currentTime - lastKeyTime[0] > 100 && scanBuffer.length() > 0) {
                scanBuffer.setLength(0);
            }

            lastKeyTime[0] = currentTime;

            // Capture character keys
            if (event.getCode().isLetterKey() || event.getCode().isDigitKey()) {
                scanBuffer.append(event.getText());
            }

            // Enter key indicates end of barcode scan
            if (event.getCode() == javafx.scene.input.KeyCode.ENTER) {
                if (scanBuffer.length() > 0) {
                    String barcode = scanBuffer.toString();
                    scanBuffer.setLength(0);
                    handleBarcodeScanned(barcode);
                    event.consume();
                }
            }
        });
    }

    private void setupKeyboardShortcuts() {
        // F1 - Add Product
        // F2 - Add Customer
        // F3 - Complete Sale
        // F4 - Clear Cart
        // ESC - Clear current field
    }

    /**
     * Handle barcode scanned from hardware scanner
     */
    private void handleBarcodeScanned(String barcode) {
        try {
            // Try to find product by barcode, SKU, or ID
            Product product = ProductDAO.findByBarcode(barcode);

            if (product == null) {
                product = ProductDAO.findBySku(barcode);
            }

            if (product == null) {
                product = ProductDAO.findById(barcode);
            }

            if (product == null) {
                showError("Product not found for barcode: " + barcode);
                playBeep(); // Error beep
                return;
            }

            if (!product.isActive()) {
                showError("Product is inactive: " + product.getName());
                playBeep();
                return;
            }

            if (product.getStock() <= 0) {
                showError("OUT OF STOCK: " + product.getDisplayName());
                playBeep();
                return;
            }

            addToCart(product);
            updateTotals();
            playSuccessBeep(); // Success beep

            // Show quick feedback
            System.out.println("✓ Added: " + product.getFullDisplayName() +
                    " | Price: ₹" + product.getSellPrice() +
                    " | Stock: " + product.getStock());

        } catch (Exception e) {
            showError("Error scanning product: " + e.getMessage());
            playBeep();
            e.printStackTrace();
        }
    }

    private void addToCart(Product product) {
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
            subtotal += item.getSubtotal();
            tax += item.getTaxAmount();
        }

        double discount = 0;
        try {
            if (discountField != null && !discountField.getText().isEmpty()) {
                discount = Double.parseDouble(discountField.getText());
            }
        } catch (NumberFormatException e) {
            discount = 0;
            discountField.setText("0");
        }

        double total = subtotal - discount;

        if (subtotalLabel != null) subtotalLabel.setText(String.format("₹%.2f", subtotal));
        if (taxLabel != null) taxLabel.setText(String.format("₹%.2f", tax));
        if (totalLabel != null) totalLabel.setText(String.format("₹%.2f", total));
    }

    @FXML
    public void onShowCustomerLedger() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/vastra/ui/fxml/customer_ledger.fxml"));
            Scene scene = new Scene(loader.load());
            Stage stage = new Stage();
            stage.setTitle("Customer Ledger");
            IconUtil.applyAppIcon(stage);
            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            showError("Error opening customer ledger: " + e.getMessage());
        }
    }

    @FXML
    public void onAddProduct() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/vastra/ui/fxml/product_form.fxml"));
            Scene scene = new Scene(loader.load(), 800, 700);
            Stage stage = new Stage();
            stage.setTitle("Add Product");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(scene);
            stage.setResizable(true);
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            showError("Error opening product form: " + e.getMessage());
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
                    // Create new customer dialog
                    Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
                    confirmDialog.setTitle("New Customer");
                    confirmDialog.setHeaderText("Customer not found");
                    confirmDialog.setContentText("Create new customer with phone: " + phone + "?");

                    Optional<ButtonType> confirmResult = confirmDialog.showAndWait();
                    if (confirmResult.isPresent() && confirmResult.get() == ButtonType.OK) {
                        TextInputDialog nameDialog = new TextInputDialog();
                        nameDialog.setTitle("Customer Name");
                        nameDialog.setHeaderText("Enter customer name");
                        nameDialog.setContentText("Name:");

                        Optional<String> nameResult = nameDialog.showAndWait();
                        if (nameResult.isPresent() && !nameResult.get().trim().isEmpty()) {
                            customer = CustomerDAO.createCustomer(nameResult.get().trim(), phone, "");
                            showSuccess("Customer created successfully!");
                        }
                    }
                }

                if (customer != null) {
                    currentCustomer = customer;
                    if (customerNameLabel != null) {
                        customerNameLabel.setText(customer.getName());
                    }
                    if (customerPointsLabel != null) {
                        customerPointsLabel.setText(String.format("%.2f points available", customer.getPoints()));
                    }
                }

            } catch (Exception e) {
                showError("Error loading customer: " + e.getMessage());
                e.printStackTrace();
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
        dialog.setHeaderText(String.format("Customer has %.2f points\n1 point = ₹1 discount", currentCustomer.getPoints()));
        dialog.setContentText("Points to redeem:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(pointsStr -> {
            try {
                int points = Integer.parseInt(pointsStr);
                if (points > currentCustomer.getPoints()) {
                    showError("Customer doesn't have enough points");
                    return;
                }
                if (points < 100) {
                    showError("Minimum 100 points required for redemption");
                    return;
                }
                if (discountField != null) {
                    double currentDiscount = Double.parseDouble(discountField.getText());
                    discountField.setText(String.valueOf(currentDiscount + points));
                    pointsRedeemedThisSale += points;
                    updateTotals();
                    showSuccess(points + " points will be redeemed");
                }
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

        // Confirm sale
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Complete Sale");
        confirmAlert.setHeaderText("Complete this sale?");
        confirmAlert.setContentText("Total: " + totalLabel.getText());

        Optional<ButtonType> confirmResult = confirmAlert.showAndWait();
        if (!confirmResult.isPresent() || confirmResult.get() != ButtonType.OK) {
            return;
        }

        // Select payment method
        ChoiceDialog<String> paymentDialog = new ChoiceDialog<>("CASH", "CASH", "CARD", "UPI", "CREDIT", "OTHER");
        paymentDialog.setTitle("Payment Method");
        paymentDialog.setHeaderText("Select payment method");

        Optional<String> paymentResult = paymentDialog.showAndWait();
        if (!paymentResult.isPresent()) return;

        try {
            int discountCents = 0;
            if (discountField != null && !discountField.getText().isEmpty()) {
                discountCents = (int) (Double.parseDouble(discountField.getText()) * 100);
            }
            String customerId = currentCustomer != null ? currentCustomer.getId() : null;

            // Capture the customer's balance BEFORE redemption so the receipt's
            // "Points Opening" line is accurate, regardless of what happens next.
            double pointsOpeningForReceipt = currentCustomer != null ? currentCustomer.getPoints() : 0;

            // Redeem points if used
            if (currentCustomer != null && discountCents > 0) {
                int pointsToRedeem = discountCents / 100; // 1 rupee = 1 point
                if (pointsToRedeem <= currentCustomer.getPoints()) {
                    CustomerDAO.redeemPoints(currentCustomer.getId(), pointsToRedeem);
                }
            }

            String cashierName = (cashierNameField != null && !cashierNameField.getText().isBlank())
                    ? cashierNameField.getText().trim() : "Admin";

            // Complete sale - returns everything needed to print the receipt
            SaleReceiptData receipt = SalesDAO.completeSale(
                    new ArrayList<>(cartItems),
                    customerId,
                    pointsOpeningForReceipt,
                    pointsRedeemedThisSale,
                    discountCents,
                    paymentResult.get(),
                    cashierName
            );

            showSuccess("Sale completed!\nBill No: " + receipt.getBillNumber());

            lastCompletedSale = receipt;
            handleBillSharing(receipt);

            // Clear cart and refresh customer points
            clearCart();
            if (currentCustomer != null) {
                currentCustomer = CustomerDAO.findById(currentCustomer.getId());
                if (customerPointsLabel != null && currentCustomer != null) {
                    customerPointsLabel.setText(String.format("%.2f points available", currentCustomer.getPoints()));
                }
            }

        } catch (Exception e) {
            showError("Error completing sale: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void printBill(SaleReceiptData receipt) {
        try {
            // Since SaleReceiptData stores strings instead of a Customer object,
            // we create a temporary Customer object for the printer to use.
            com.vastra.model.Customer customer = null;
            if (receipt.isHasCustomer()) {
                customer = new com.vastra.model.Customer();
                // Note: If your Customer class uses different setters (e.g., setCustomerName), adjust these two lines:
                customer.setName(receipt.getCustomerName());
                customer.setPhone(receipt.getCustomerPhone());
            }

            // Now we pass the exact names from your SaleReceiptData class
            boolean printed = ThermalPrinterUtil.printReceipt(
                    receipt.getBillNumber(),    // Fixed name
                    receipt.getItems(),
                    customer,                   // Passing the temporary customer object we made above
                    receipt.getSubtotal(),
                    receipt.getTax(),
                    receipt.getDiscount(),
                    receipt.getTotal(),
                    receipt.getPaymentMode()    // Fixed name
            );

            if (!printed) {
                showWarning("Bill could not be printed. Please check printer connection.");
            }
        } catch (Exception e) {
            showWarning("Error printing bill: " + e.getMessage());
        }
    }

    private enum ShareMode { PRINT_ONLY, WHATSAPP_ONLY, BOTH, SKIP }

    private void handleBillSharing(SaleReceiptData receipt) {
        ShareMode mode = askShareMode();
        if (mode == ShareMode.PRINT_ONLY || mode == ShareMode.BOTH) {
            printBill(receipt);
        }
        if (mode == ShareMode.WHATSAPP_ONLY || mode == ShareMode.BOTH) {
            shareViaWhatsApp(receipt);
        }
    }

    private ShareMode askShareMode() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Share Bill");
        alert.setHeaderText("How would you like to share this bill?");

        ButtonType printBtn = new ButtonType("🖨️ Print Only");
        ButtonType whatsappBtn = new ButtonType("📱 WhatsApp Only");
        ButtonType bothBtn = new ButtonType("🖨️📱 Both");
        ButtonType skipBtn = new ButtonType("Skip", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(printBtn, whatsappBtn, bothBtn, skipBtn);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isEmpty() || result.get() == skipBtn) return ShareMode.SKIP;
        if (result.get() == printBtn) return ShareMode.PRINT_ONLY;
        if (result.get() == whatsappBtn) return ShareMode.WHATSAPP_ONLY;
        return ShareMode.BOTH;
    }

    private void shareViaWhatsApp(SaleReceiptData receipt) {
        String phone = receipt.isHasCustomer() ? receipt.getCustomerPhone() : null;
        if (phone == null || phone.isBlank()) {
            TextInputDialog phoneDialog = new TextInputDialog();
            phoneDialog.setTitle("WhatsApp Bill");
            phoneDialog.setHeaderText("Enter customer's WhatsApp number");
            phoneDialog.setContentText("Phone (10 digits, or with country code):");
            Optional<String> result = phoneDialog.showAndWait();
            if (result.isEmpty() || result.get().isBlank()) return;
            phone = result.get().trim();
        }

        try {
            WhatsAppUtil.shareBill(phone, receipt);
        } catch (Exception e) {
            showWarning("Could not open WhatsApp: " + e.getMessage());
        }
    }

    @FXML
    public void onClearCart() {
        if (cartItems.isEmpty()) return;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Clear Cart");
        alert.setHeaderText("Clear all items from cart?");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            clearCart();
        }
    }

    private void clearCart() {
        cartItems.clear();
        currentCustomer = null;
        pointsRedeemedThisSale = 0;
        if (customerNameLabel != null) customerNameLabel.setText("Walk-in Customer");
        if (customerPointsLabel != null) customerPointsLabel.setText("0 points");
        if (discountField != null) discountField.setText("0");
        updateTotals();
    }

    @FXML
    public void onClearCustomer() {
        currentCustomer = null;
        if (customerNameLabel != null) customerNameLabel.setText("Walk-in Customer");
        if (customerPointsLabel != null) customerPointsLabel.setText("0 points");
    }

    @FXML
    public void onShowDashboard() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/vastra/ui/fxml/dashboard.fxml"));
            Scene scene = new Scene(loader.load());
            Stage stage = primaryStage != null ? primaryStage : (Stage) cartTable.getScene().getWindow();
            com.vastra.ui.controllers.DashboardController controller = loader.getController();
            controller.setStage(stage);
            stage.setTitle("Vastra");
            stage.setScene(scene);
        } catch (Exception e) {
            e.printStackTrace();
            showError("Error opening dashboard: " + e.getMessage());
        }
    }

    @FXML
    public void onShowLowStock() {
        try {
            List<Product> lowStockProducts = ProductDAO.getLowStockProducts();
            if (lowStockProducts.isEmpty()) {
                showInfo("No low stock items");
                return;
            }

            StringBuilder sb = new StringBuilder("Low Stock Items:\n\n");
            for (Product p : lowStockProducts) {
                sb.append(String.format("%s - Stock: %d (Min: %d)\n",
                        p.getFullDisplayName(), p.getStock(), p.getReorderThreshold()));
            }

            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Low Stock Alert");
            alert.setHeaderText("Items need restocking");
            alert.setContentText(sb.toString());
            alert.showAndWait();

        } catch (Exception e) {
            showError("Error fetching low stock: " + e.getMessage());
        }
    }

    private void checkLowStockAlerts() {
        try {
            List<Product> lowStock = ProductDAO.getLowStockProducts();
            if (!lowStock.isEmpty() && lowStockAlertLabel != null) {
                lowStockAlertLabel.setText("⚠ " + lowStock.size() + " items low on stock");
                lowStockAlertLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Shows a banner at the top of the main screen reminding you which
     * supplier to pay next - the one with the earliest outstanding due date
     * (most overdue first, then soonest upcoming). Click the label to jump
     * straight to that supplier's ledger.
     */
    private void checkPaymentReminders() {
        if (paymentReminderLabel == null) return;
        try {
            PayableReminder reminder = SupplierDAO.getNextPaymentReminder();
            if (reminder == null) {
                paymentReminderLabel.setText("");
                paymentReminderLabel.setVisible(false);
                paymentReminderLabel.setManaged(false);
                return;
            }

            String message;
            String style;
            if (reminder.isOverdue()) {
                message = String.format("⚠ PAYMENT OVERDUE: %s — ₹%.2f owed, was due %s (%d day%s overdue). Click to view.",
                        reminder.getSupplierName(), reminder.getAmountDue(), reminder.getNextDueDate(),
                        reminder.getDaysDiff(), reminder.getDaysDiff() == 1 ? "" : "s");
                style = "-fx-text-fill: white; -fx-background-color: #D32F2F; -fx-font-weight: bold; -fx-padding: 6 12 6 12; -fx-cursor: hand;";
            } else {
                long daysUntil = -reminder.getDaysDiff();
                message = String.format("💰 Next payment due: %s — ₹%.2f due on %s (in %d day%s). Click to view.",
                        reminder.getSupplierName(), reminder.getAmountDue(), reminder.getNextDueDate(),
                        daysUntil, daysUntil == 1 ? "" : "s");
                style = "-fx-text-fill: #333; -fx-background-color: #FFF3CD; -fx-font-weight: bold; -fx-padding: 6 12 6 12; -fx-cursor: hand;";
            }

            paymentReminderLabel.setText(message);
            paymentReminderLabel.setStyle(style);
            paymentReminderLabel.setVisible(true);
            paymentReminderLabel.setManaged(true);
            paymentReminderLabel.setOnMouseClicked(e -> onShowSuppliers());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Reminds you to run today's backup before closing up, matching your
     * end-of-day workflow. Shown only when no backup has been taken today.
     */
    private void checkBackupReminder() {
        if (backupReminderLabel == null) return;
        if (BackupUtil.wasBackedUpToday()) {
            backupReminderLabel.setText("");
            backupReminderLabel.setVisible(false);
            backupReminderLabel.setManaged(false);
            return;
        }
        backupReminderLabel.setText("📦 No backup taken today — remember to back up before closing. Click to open Backup.");
        backupReminderLabel.setStyle("-fx-text-fill: #333; -fx-background-color: #E1F5FE; -fx-font-weight: bold; -fx-padding: 6 12 6 12; -fx-cursor: hand;");
        backupReminderLabel.setVisible(true);
        backupReminderLabel.setManaged(true);
        backupReminderLabel.setOnMouseClicked(e -> onShowBackup());
    }

    private void playSuccessBeep() {
        // Implement sound feedback for successful scan
        java.awt.Toolkit.getDefaultToolkit().beep();
    }

    private void playBeep() {
        // Implement error beep
        java.awt.Toolkit.getDefaultToolkit().beep();
    }

    // Stub methods for future implementation
    @FXML public void onBulkImport() { showInfo("Bulk Import - Coming Soon!"); }
    @FXML public void onShowReports() { showInfo("Reports - Coming Soon!"); }
    @FXML public void onShowSettings() { showInfo("Settings - Coming Soon!"); }
    @FXML public void onAddItemManually() { showInfo("Add Item Manually - Coming Soon!"); }
    @FXML public void onHoldSale() { showInfo("Hold Sale - Coming Soon!"); }
    @FXML
    public void onShareLastBillWhatsApp() {
        if (lastCompletedSale == null) {
            showInfo("No recent bill to share yet — complete a sale first.");
            return;
        }
        shareViaWhatsApp(lastCompletedSale);
    }

    @FXML
    public void onShowReturns() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/vastra/ui/fxml/returns.fxml"));
            Scene scene = new Scene(loader.load());
            Stage stage = new Stage();
            stage.setTitle("Returns & Exchange");
            IconUtil.applyAppIcon(stage);
            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            showError("Error opening returns screen: " + e.getMessage());
        }
    }

    @FXML
    public void onExit() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Exit Vastra");
        confirm.setHeaderText("Close the application?");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            Platform.exit();
        }
    }

    @FXML
    public void onPrintBarcodes() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/vastra/ui/fxml/label_print.fxml"));
            Scene scene = new Scene(loader.load());
            Stage stage = new Stage();
            stage.setTitle("Print Labels");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            showError("Error opening label print screen: " + e.getMessage());
        }
    }

    @FXML
    public void onShowSuppliers() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/vastra/ui/fxml/supplier.fxml"));
            Scene scene = new Scene(loader.load());
            Stage stage = new Stage();
            stage.setTitle("Suppliers & Dues");
            stage.setScene(scene);
            stage.setOnHidden(e -> checkPaymentReminders()); // refresh banner after payments/purchases are recorded
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            showError("Error opening suppliers screen: " + e.getMessage());
        }
    }

    @FXML
    public void onShowBackup() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/vastra/ui/fxml/backup.fxml"));
            Scene scene = new Scene(loader.load());
            Stage stage = new Stage();
            stage.setTitle("Backup, Restore & Excel Archive");
            stage.setScene(scene);
            stage.setOnHidden(e -> checkBackupReminder()); // refresh banner after a backup is taken
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            showError("Error opening backup screen: " + e.getMessage());
        }
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

    private void showInfo(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, msg);
        alert.setTitle("Information");
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private void showWarning(String msg) {
        Alert alert = new Alert(Alert.AlertType.WARNING, msg);
        alert.setTitle("Warning");
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}