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
    @FXML private TableColumn<CartItem, Integer> itemDiscountColumn;
    @FXML private TableColumn<CartItem, Double> taxColumn;
    @FXML private TableColumn<CartItem, Double> totalColumn;
    @FXML private TableColumn<CartItem, Void> actionColumn;

    @FXML private Label totalLabel;
    @FXML private Label subtotalLabel;
    @FXML private Label taxLabel;
    @FXML private Label discountTotalLabel;
    @FXML private Label customerNameLabel;
    @FXML private Label customerPointsLabel;
    @FXML private TextField discountField;
    @FXML private Label lowStockAlertLabel;
    @FXML private Label paymentReminderLabel;
    @FXML private Label backupReminderLabel;
    @FXML private TextField cashierNameField;
    @FXML private Button holdSaleButton;
    @FXML private TextField quickCustomerSearchField;
    @FXML private TextField quickAddNameField;
    @FXML private TextField quickAddPhoneField;
    @FXML private MenuItem backupMenuItem;
    @FXML private MenuItem addProductMenuItem;
    @FXML private MenuItem printBarcodesMenuItem;
    @FXML private MenuItem bulkImportMenuItem;
    @FXML private Menu suppliersMenu;
    @FXML private Menu reportsMenu;
    @FXML private Menu toolsMenu;
    @FXML private Button addProductToolbarBtn;
    @FXML private Button printBarcodesToolbarBtn;
    @FXML private Button suppliersToolbarBtn;
    @FXML private Button backupToolbarBtn;
    @FXML private Button reportsToolbarBtn;
    @FXML private Button settingsToolbarBtn;

    private ObservableList<CartItem> cartItems = FXCollections.observableArrayList();
    private Customer currentCustomer = null;
    private double pointsRedeemedThisSale = 0;
    private BarcodeScanner barcodeScanner;
    private Stage primaryStage; // Store reference to main stage for focus handling
    private SaleReceiptData lastCompletedSale;
    private String currencySymbol = "\u20B9";

    @FXML
    public void initialize() {
        currencySymbol = com.vastra.util.CurrencyUtil.symbol();
        setupCartTable();
        setupBarcodeScanner();
        checkLowStockAlerts();
        checkPaymentReminders();
        checkBackupReminder();
        refreshHeldBillsIndicator();

        if (discountField != null) {
            discountField.setText("0");
            discountField.textProperty().addListener((obs, old, newVal) -> updateTotals());
        }

        // Setup global key listener for barcode scanner, and F-key shortcuts
        Platform.runLater(() -> {
            if (cartTable != null && cartTable.getScene() != null) {
                primaryStage = (Stage) cartTable.getScene().getWindow();
                setupGlobalBarcodeListener();
                setupKeyboardShortcuts();
            }
        });

        if (cashierNameField != null) {
            com.vastra.model.User currentUser = com.vastra.util.Session.getCurrentUser();
            cashierNameField.setText(currentUser != null ? currentUser.getDisplayName() : "Unknown");
        }
        applyRoleGating();
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

        // Per-item discount, editable in rupees (stored internally as paise/cents)
        itemDiscountColumn.setCellValueFactory(data ->
                new SimpleIntegerProperty((int) Math.round(data.getValue().getDiscount())).asObject());
        itemDiscountColumn.setCellFactory(TextFieldTableCell.forTableColumn(new IntegerStringConverter()));
        itemDiscountColumn.setOnEditCommit(event -> {
            CartItem item = event.getRowValue();
            int newDiscountRupees = event.getNewValue() != null ? event.getNewValue() : 0;
            int lineSubtotalRupees = (int) item.getSubtotal();
            if (newDiscountRupees < 0 || newDiscountRupees > lineSubtotalRupees) {
                showError("Discount must be between " + currencySymbol + "0 and the line total (" + currencySymbol + lineSubtotalRupees + ")");
                cartTable.refresh();
                return;
            }
            item.setDiscountCents(newDiscountRupees * 100);
            updateTotals();
            cartTable.refresh();
        });

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

    /** Cashiers get a stripped-down POS screen - no inventory setup, no financial screens, no admin tools. */
    private void applyRoleGating() {
        boolean admin = com.vastra.util.Session.isAdmin();

        if (backupMenuItem != null) backupMenuItem.setVisible(admin);
        if (addProductMenuItem != null) addProductMenuItem.setVisible(admin);
        if (printBarcodesMenuItem != null) printBarcodesMenuItem.setVisible(admin);
        if (bulkImportMenuItem != null) bulkImportMenuItem.setVisible(admin);
        if (suppliersMenu != null) suppliersMenu.setVisible(admin);
        if (reportsMenu != null) reportsMenu.setVisible(admin);
        if (toolsMenu != null) toolsMenu.setVisible(admin);

        if (addProductToolbarBtn != null) { addProductToolbarBtn.setVisible(admin); addProductToolbarBtn.setManaged(admin); }
        if (printBarcodesToolbarBtn != null) { printBarcodesToolbarBtn.setVisible(admin); printBarcodesToolbarBtn.setManaged(admin); }
        if (suppliersToolbarBtn != null) { suppliersToolbarBtn.setVisible(admin); suppliersToolbarBtn.setManaged(admin); }
        if (backupToolbarBtn != null) { backupToolbarBtn.setVisible(admin); backupToolbarBtn.setManaged(admin); }
        if (reportsToolbarBtn != null) { reportsToolbarBtn.setVisible(admin); reportsToolbarBtn.setManaged(admin); }
        if (settingsToolbarBtn != null) { settingsToolbarBtn.setVisible(admin); settingsToolbarBtn.setManaged(admin); }
    }

    @FXML
    public void onLogout() {
        com.vastra.util.Session.logout();
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/com/vastra/ui/fxml/login.fxml"));
            javafx.scene.Parent root = loader.load();
            LoginController controller = loader.getController();
            Stage stage = primaryStage != null ? primaryStage : (Stage) cartTable.getScene().getWindow();
            controller.setStage(stage);
            stage.setTitle("Vastra - Login");
            com.vastra.util.WindowUtil.swapRoot(stage, root);
        } catch (Exception e) {
            e.printStackTrace();
            showError("Error logging out: " + e.getMessage());
        }
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
        if (primaryStage == null || primaryStage.getScene() == null) return;
        var accelerators = primaryStage.getScene().getAccelerators();

        // F1 - Add Product (admin only, matches the toolbar/menu item's own visibility)
        accelerators.put(javafx.scene.input.KeyCombination.keyCombination("F1"), () -> {
            if (com.vastra.util.Session.isAdmin()) onAddProduct();
        });
        // F2 - Add Customer
        accelerators.put(javafx.scene.input.KeyCombination.keyCombination("F2"), this::onAddCustomer);
        // F3 - Complete Sale
        accelerators.put(javafx.scene.input.KeyCombination.keyCombination("F3"), this::onCompleteSale);
        // F4 - Clear Cart
        accelerators.put(javafx.scene.input.KeyCombination.keyCombination("F4"), this::onClearCart);
        // F5 - Hold Sale
        accelerators.put(javafx.scene.input.KeyCombination.keyCombination("F5"), this::onHoldSale);
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
                    " | Price: " + currencySymbol + product.getSellPrice() +
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
        double itemDiscounts = 0;

        for (CartItem item : cartItems) {
            subtotal += item.getSubtotal();
            tax += item.getTaxAmount();
            itemDiscounts += item.getDiscount();
        }

        double billDiscount = 0;
        try {
            if (discountField != null && !discountField.getText().isEmpty()) {
                billDiscount = Double.parseDouble(discountField.getText());
            }
        } catch (NumberFormatException e) {
            billDiscount = 0;
            discountField.setText("0");
        }

        double totalDiscount = itemDiscounts + billDiscount;
        double total = subtotal - totalDiscount;

        if (subtotalLabel != null) subtotalLabel.setText(currencySymbol + String.format("%.2f", subtotal));
        if (taxLabel != null) taxLabel.setText(currencySymbol + String.format("%.2f", tax));
        if (discountTotalLabel != null) discountTotalLabel.setText("- " + currencySymbol + String.format("%.2f", totalDiscount));
        if (totalLabel != null) totalLabel.setText(currencySymbol + String.format("%.2f", total));
    }

    /** Same math as updateTotals(), but returns the number directly instead of formatting a label. */
    private double computeCurrentTotal() {
        double subtotal = 0;
        double itemDiscounts = 0;
        for (CartItem item : cartItems) {
            subtotal += item.getSubtotal();
            itemDiscounts += item.getDiscount();
        }
        double billDiscount = 0;
        try {
            if (discountField != null && !discountField.getText().isEmpty()) {
                billDiscount = Double.parseDouble(discountField.getText());
            }
        } catch (NumberFormatException ignored) { }
        return subtotal - itemDiscounts - billDiscount;
    }

    /**
     * Payment dialog: either a normal cash/card/UPI split that must add up to the total,
     * or a CREDIT (pay later) sale. Returns empty if the cashier cancels.
     */
    private Optional<List<com.vastra.model.SplitPayment>> showPaymentDialog(double total) {
        Dialog<List<com.vastra.model.SplitPayment>> dialog = new Dialog<>();
        dialog.setTitle("Payment");
        dialog.setHeaderText("Total to collect: " + currencySymbol + String.format("%.2f", total));

        ButtonType payButtonType = new ButtonType("Confirm Payment", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(payButtonType, ButtonType.CANCEL);

        TextField cashField = new TextField(String.format("%.2f", total));
        TextField cardField = new TextField("0.00");
        TextField upiField = new TextField("0.00");
        CheckBox creditCheck = new CheckBox("Mark as CREDIT (pay later) - no payment collected now");

        cashField.disableProperty().bind(creditCheck.selectedProperty());
        cardField.disableProperty().bind(creditCheck.selectedProperty());
        upiField.disableProperty().bind(creditCheck.selectedProperty());

        Label remainingLabel = new Label();
        Runnable updateRemaining = () -> {
            if (creditCheck.isSelected()) {
                remainingLabel.setText("No payment collected now - full amount goes to customer dues");
                remainingLabel.setStyle("-fx-text-fill: #856404;");
                return;
            }
            try {
                double c = cashField.getText().isBlank() ? 0 : Double.parseDouble(cashField.getText());
                double cd = cardField.getText().isBlank() ? 0 : Double.parseDouble(cardField.getText());
                double u = upiField.getText().isBlank() ? 0 : Double.parseDouble(upiField.getText());
                double remaining = total - (c + cd + u);
                if (Math.abs(remaining) < 0.01) {
                    remainingLabel.setText("✓ Fully covered");
                    remainingLabel.setStyle("-fx-text-fill: #2E7D32; -fx-font-weight: bold;");
                } else {
                    remainingLabel.setText("Remaining: " + currencySymbol + String.format("%.2f", remaining));
                    remainingLabel.setStyle("-fx-text-fill: #D32F2F; -fx-font-weight: bold;");
                }
            } catch (NumberFormatException e) {
                remainingLabel.setText("Enter valid numbers");
                remainingLabel.setStyle("-fx-text-fill: #D32F2F;");
            }
        };
        cashField.textProperty().addListener((o, ov, nv) -> updateRemaining.run());
        cardField.textProperty().addListener((o, ov, nv) -> updateRemaining.run());
        upiField.textProperty().addListener((o, ov, nv) -> updateRemaining.run());
        creditCheck.selectedProperty().addListener((o, ov, nv) -> updateRemaining.run());
        updateRemaining.run();

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(15));
        grid.add(new Label("Cash (" + currencySymbol + "):"), 0, 0); grid.add(cashField, 1, 0);
        grid.add(new Label("Card (" + currencySymbol + "):"), 0, 1); grid.add(cardField, 1, 1);
        grid.add(new Label("UPI (" + currencySymbol + "):"), 0, 2); grid.add(upiField, 1, 2);
        grid.add(remainingLabel, 1, 3);
        grid.add(new Separator(), 0, 4, 2, 1);
        grid.add(creditCheck, 0, 5, 2, 1);
        dialog.getDialogPane().setContent(grid);

        Button payBtn = (Button) dialog.getDialogPane().lookupButton(payButtonType);
        payBtn.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            if (creditCheck.isSelected()) return; // no amount validation needed for credit
            try {
                double c = cashField.getText().isBlank() ? 0 : Double.parseDouble(cashField.getText());
                double cd = cardField.getText().isBlank() ? 0 : Double.parseDouble(cardField.getText());
                double u = upiField.getText().isBlank() ? 0 : Double.parseDouble(upiField.getText());
                if (c < 0 || cd < 0 || u < 0) {
                    showError("Amounts cannot be negative");
                    event.consume();
                    return;
                }
                if (Math.abs((c + cd + u) - total) > 0.01) {
                    showError("Cash + Card + UPI must add up to the total (" + currencySymbol + String.format("%.2f", total) + ")");
                    event.consume();
                }
            } catch (NumberFormatException e) {
                showError("Enter valid amounts");
                event.consume();
            }
        });

        dialog.setResultConverter(bt -> {
            if (bt != payButtonType) return null;
            List<com.vastra.model.SplitPayment> result = new ArrayList<>();
            if (creditCheck.isSelected()) {
                result.add(new com.vastra.model.SplitPayment("CREDIT", (int) Math.round(total * 100)));
                return result;
            }
            double c = Double.parseDouble(cashField.getText());
            double cd = Double.parseDouble(cardField.getText());
            double u = Double.parseDouble(upiField.getText());
            if (c > 0) result.add(new com.vastra.model.SplitPayment("CASH", (int) Math.round(c * 100)));
            if (cd > 0) result.add(new com.vastra.model.SplitPayment("CARD", (int) Math.round(cd * 100)));
            if (u > 0) result.add(new com.vastra.model.SplitPayment("UPI", (int) Math.round(u * 100)));
            if (result.isEmpty()) result.add(new com.vastra.model.SplitPayment("CASH", 0));
            return result;
        });

        return dialog.showAndWait();
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
    public void onShowStockCheck() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/vastra/ui/fxml/stock_check.fxml"));
            Scene scene = new Scene(loader.load());
            Stage stage = new Stage();
            stage.setTitle("Check Stock (Scan)");
            IconUtil.applyAppIcon(stage);
            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            showError("Error opening stock check: " + e.getMessage());
        }
    }

    @FXML
    public void onShowFullStock() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/vastra/ui/fxml/product_list.fxml"));
            Scene scene = new Scene(loader.load());
            Stage stage = new Stage();
            stage.setTitle("Full Stock");
            IconUtil.applyAppIcon(stage);
            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            showError("Error opening full stock: " + e.getMessage());
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
        openCustomerSelectDialog(null);
    }

    @FXML
    public void onQuickCustomerSearch() {
        String term = quickCustomerSearchField != null ? quickCustomerSearchField.getText() : null;
        openCustomerSelectDialog(term);
    }

    private void openCustomerSelectDialog(String initialSearchTerm) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/vastra/ui/fxml/customer_select.fxml"));
            Scene scene = new Scene(loader.load());
            CustomerSelectController controller = loader.getController();
            if (initialSearchTerm != null && !initialSearchTerm.isBlank()) {
                controller.setInitialSearchTerm(initialSearchTerm.trim());
            }

            Stage stage = new Stage();
            stage.setTitle("Select Customer");
            com.vastra.util.IconUtil.applyAppIcon(stage);
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(scene);
            stage.showAndWait();

            if (controller.isWalkInChosen()) {
                currentCustomer = null;
                if (customerNameLabel != null) customerNameLabel.setText("Walk-in Customer");
                if (customerPointsLabel != null) customerPointsLabel.setText("0 points");
            } else if (controller.getSelectedCustomer() != null) {
                currentCustomer = controller.getSelectedCustomer();
                if (customerNameLabel != null) customerNameLabel.setText(currentCustomer.getName());
                if (customerPointsLabel != null) {
                    customerPointsLabel.setText(String.format("%.2f points available", currentCustomer.getPoints()));
                }
            }
            // else: dialog was closed (X button) without picking anyone - leave current selection as-is

            if (quickCustomerSearchField != null) quickCustomerSearchField.clear();

        } catch (Exception e) {
            e.printStackTrace();
            showError("Error opening customer selection: " + e.getMessage());
        }
    }

    @FXML
    public void onQuickAddCustomer() {
        String name = quickAddNameField != null && quickAddNameField.getText() != null ? quickAddNameField.getText().trim() : "";
        String phone = quickAddPhoneField != null && quickAddPhoneField.getText() != null ? quickAddPhoneField.getText().trim() : "";

        if (name.isEmpty() || phone.isEmpty()) {
            showError("Enter both name and mobile number to quick-add a customer");
            return;
        }

        try {
            Customer customer = CustomerDAO.findByPhone(phone);
            if (customer == null) {
                customer = CustomerDAO.createCustomer(name, phone, "");
            }

            currentCustomer = customer;
            if (customerNameLabel != null) customerNameLabel.setText(customer.getName());
            if (customerPointsLabel != null) {
                customerPointsLabel.setText(String.format("%.2f points available", customer.getPoints()));
            }

            quickAddNameField.clear();
            quickAddPhoneField.clear();

        } catch (Exception e) {
            e.printStackTrace();
            showError("Error adding customer: " + e.getMessage());
        }
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
        dialog.setHeaderText(String.format("Customer has %.2f points\n1 point = " + currencySymbol + "1 discount", currentCustomer.getPoints()));
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

        double currentTotal = computeCurrentTotal();
        Optional<List<com.vastra.model.SplitPayment>> paymentResult = showPaymentDialog(currentTotal);
        if (paymentResult.isEmpty()) return;

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
            boolean printed = ThermalPrinterUtil.printReceipt(receipt);
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
            List<com.vastra.model.LowStockSuggestion> suggestions = ProductDAO.getLowStockWithSuggestions();
            if (suggestions.isEmpty()) {
                showInfo("No low stock items");
                return;
            }

            StringBuilder sb = new StringBuilder("Low Stock Items - suggested reorder quantities:\n\n");
            for (com.vastra.model.LowStockSuggestion s : suggestions) {
                Product p = s.getProduct();
                String velocityNote = s.getSoldLast30Days() > 0
                        ? s.getSoldLast30Days() + " sold in last 30 days"
                        : "no recent sales - estimate only";
                sb.append(String.format("%s  |  Stock: %d (Min: %d)  |  Suggest ordering: %d  (%s)\n",
                        p.getFullDisplayName(), p.getStock(), p.getReorderThreshold(),
                        s.getSuggestedReorderQty(), velocityNote));
            }

            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Low Stock Alert");
            alert.setHeaderText("Items need restocking");
            alert.getDialogPane().setContent(new TextArea(sb.toString()) {{
                setEditable(false);
                setWrapText(true);
                setPrefSize(560, 320);
            }});
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
        if (paymentReminderLabel == null || !com.vastra.util.Session.isAdmin()) return;
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
                message = String.format("⚠ PAYMENT OVERDUE: %s — " + currencySymbol + "%.2f owed, was due %s (%d day%s overdue). Click to view.",
                        reminder.getSupplierName(), reminder.getAmountDue(), reminder.getNextDueDate(),
                        reminder.getDaysDiff(), reminder.getDaysDiff() == 1 ? "" : "s");
                style = "-fx-text-fill: white; -fx-background-color: #D32F2F; -fx-font-weight: bold; -fx-padding: 6 12 6 12; -fx-cursor: hand;";
            } else {
                long daysUntil = -reminder.getDaysDiff();
                message = String.format("💰 Next payment due: %s — " + currencySymbol + "%.2f due on %s (in %d day%s). Click to view.",
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
        if (!com.vastra.util.Session.isAdmin() || BackupUtil.wasBackedUpToday()) {
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
    @FXML public void onAddItemManually() { showInfo("Add Item Manually - Coming Soon!"); }

    @FXML
    public void onHoldSale() {
        if (cartItems.isEmpty()) {
            showError("Cart is empty - nothing to hold");
            return;
        }

        TextInputDialog labelDialog = new TextInputDialog(
                currentCustomer != null ? currentCustomer.getName() : "");
        labelDialog.setTitle("Hold Sale");
        labelDialog.setHeaderText("Park this cart so you can serve someone else, then come back to it.");
        labelDialog.setContentText("Label (optional, e.g. customer name):");
        Optional<String> labelResult = labelDialog.showAndWait();
        if (labelResult.isEmpty()) return;

        try {
            double billDiscount = 0;
            try {
                if (discountField != null && !discountField.getText().isEmpty()) {
                    billDiscount = Double.parseDouble(discountField.getText());
                }
            } catch (NumberFormatException ignored) { }
            int discountCents = (int) Math.round(billDiscount * 100);

            String heldBy = (cashierNameField != null && !cashierNameField.getText().isBlank())
                    ? cashierNameField.getText().trim() : "Admin";
            String custId = currentCustomer != null ? currentCustomer.getId() : null;
            String custName = currentCustomer != null ? currentCustomer.getName() : null;

            com.vastra.dao.HeldBillDAO.holdBill(labelResult.get().trim(), custId, custName,
                    new ArrayList<>(cartItems), discountCents, heldBy);

            clearCart();
            refreshHeldBillsIndicator();
            showSuccess("Sale held. Use \"Resume Held Bill\" to bring it back.");
        } catch (Exception e) {
            e.printStackTrace();
            showError("Could not hold sale: " + e.getMessage());
        }
    }

    @FXML
    public void onShowHeldBills() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/vastra/ui/fxml/held_bills.fxml"));
            Scene scene = new Scene(loader.load());
            HeldBillsController controller = loader.getController();

            Stage stage = new Stage();
            stage.setTitle("Held Bills");
            IconUtil.applyAppIcon(stage);
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(scene);
            stage.showAndWait();

            com.vastra.model.HeldBill resumed = controller.getResumedBill();
            if (resumed != null) {
                if (!cartItems.isEmpty()) {
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                            "Resuming will replace what's currently in your cart. Continue?");
                    Optional<ButtonType> confirmResult = confirm.showAndWait();
                    if (confirmResult.isEmpty() || confirmResult.get() != ButtonType.OK) {
                        refreshHeldBillsIndicator();
                        return;
                    }
                }

                cartItems.setAll(resumed.getItems());
                currentCustomer = resumed.getCustomerId() != null ? CustomerDAO.findById(resumed.getCustomerId()) : null;
                if (customerNameLabel != null) {
                    customerNameLabel.setText(currentCustomer != null ? currentCustomer.getName() : "Walk-in Customer");
                }
                if (customerPointsLabel != null) {
                    customerPointsLabel.setText(currentCustomer != null
                            ? String.format("%.2f points available", currentCustomer.getPoints()) : "0 points");
                }
                if (discountField != null) discountField.setText(String.format("%.2f", resumed.getDiscount()));
                updateTotals();
            }
            refreshHeldBillsIndicator();
        } catch (Exception e) {
            e.printStackTrace();
            showError("Error opening held bills: " + e.getMessage());
        }
    }

    /** Updates the "Hold Sale" button to show how many bills are currently parked. */
    private void refreshHeldBillsIndicator() {
        if (holdSaleButton == null) return;
        try {
            int count = com.vastra.dao.HeldBillDAO.countHeldBills();
            holdSaleButton.setText(count > 0 ? "Hold Sale / Resume (" + count + ")" : "Hold Sale");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    public void onShowReports() {
        if (!com.vastra.util.Session.requireAdmin()) return;
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/vastra/ui/fxml/reports.fxml"));
            Scene scene = new Scene(loader.load());
            Stage stage = new Stage();
            stage.setTitle("Reports");
            IconUtil.applyAppIcon(stage);
            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            showError("Error opening reports screen: " + e.getMessage());
        }
    }

    @FXML
    public void onShowSettings() {
        if (!com.vastra.util.Session.requireAdmin()) return;
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/vastra/ui/fxml/settings.fxml"));
            Scene scene = new Scene(loader.load());
            Stage stage = new Stage();
            stage.setTitle("Settings");
            IconUtil.applyAppIcon(stage);
            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            showError("Error opening settings screen: " + e.getMessage());
        }
    }
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
        if (!com.vastra.util.Session.requireAdmin()) return;
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
        if (!com.vastra.util.Session.requireAdmin()) return;
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