package com.vastra.ui.controllers;

import com.vastra.dao.DashboardDAO;
import com.vastra.dao.CashRegisterDAO;
import com.vastra.dao.StoreSettingsDAO;
import com.vastra.model.CashRegisterSession;
import com.vastra.model.DailySalesRow;
import com.vastra.model.TopProductRow;
import com.vastra.model.User;
import com.vastra.util.IconUtil;
import com.vastra.util.Session;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class DashboardController {

    @FXML private Label storeNameLabel;
    @FXML private Label dateLabel;
    @FXML private Label userLabel;
    @FXML private Label todaysSalesLabel;
    @FXML private Label todaysSalesCountLabel;
    @FXML private Label lowStockLabel;
    @FXML private Label outOfStockLabel;
    @FXML private Label supplierDueLabel;
    @FXML private Label customerDueLabel;
    @FXML private VBox supplierDueCard;
    @FXML private VBox customerDueCard;
    @FXML private Button suppliersButton;
    @FXML private Button backupButton;
    @FXML private Button manageUsersButton;

    @FXML private Label cashRegisterStatusLabel;
    @FXML private Button cashRegisterButton;
    @FXML private BarChart<String, Number> weeklyRevenueChart;
    @FXML private CategoryAxis weeklyRevenueXAxis;
    @FXML private VBox topProductsBox;

    private Stage stage;

    public void setStage(Stage stage) { this.stage = stage; }

    @FXML
    public void initialize() {
        try {
            storeNameLabel.setText(StoreSettingsDAO.get("store_name", "Vastra"));
        } catch (Exception e) {
            storeNameLabel.setText("Vastra");
        }
        dateLabel.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy")));

        User user = Session.getCurrentUser();
        if (user != null) {
            userLabel.setText(user.getDisplayName() + " (" + user.getRole() + ")");
        }

        applyRoleGating();
        refresh();
    }

    private void applyRoleGating() {
        boolean admin = Session.isAdmin();

        suppliersButton.setVisible(admin);
        suppliersButton.setManaged(admin);
        backupButton.setVisible(admin);
        backupButton.setManaged(admin);
        manageUsersButton.setVisible(admin);
        manageUsersButton.setManaged(admin);

        supplierDueCard.setVisible(admin);
        supplierDueCard.setManaged(admin);
        customerDueCard.setVisible(admin);
        customerDueCard.setManaged(admin);
    }

    @FXML
    public void onRefresh() { refresh(); }

    private void refresh() {
        try {
            todaysSalesLabel.setText(String.format("₹%.2f", DashboardDAO.getTodaysSalesCents() / 100.0));
            todaysSalesCountLabel.setText(DashboardDAO.getTodaysSalesCount() + " bills");
            lowStockLabel.setText(String.valueOf(DashboardDAO.getLowStockCount()));
            outOfStockLabel.setText(DashboardDAO.getOutOfStockCount() + " out of stock");
            if (Session.isAdmin()) {
                supplierDueLabel.setText(String.format("₹%.2f", DashboardDAO.getTotalSupplierDueCents() / 100.0));
                customerDueLabel.setText(String.format("₹%.2f", DashboardDAO.getTotalCustomerDueCents() / 100.0));
            }
            refreshCashRegisterStatus();
            refreshWeeklyChart();
            refreshTopProducts();
        } catch (Exception e) {
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Error loading dashboard: " + e.getMessage()).showAndWait();
        }
    }

    private void refreshCashRegisterStatus() {
        if (cashRegisterStatusLabel == null) return;
        try {
            CashRegisterSession session = CashRegisterDAO.getTodaySession();
            if (session == null) {
                cashRegisterStatusLabel.setText("Not opened yet today");
                cashRegisterStatusLabel.setStyle("-fx-text-fill: #F57C00; -fx-font-weight: bold;");
            } else if (session.isOpen()) {
                cashRegisterStatusLabel.setText("Open since " + session.getOpenedAt());
                cashRegisterStatusLabel.setStyle("-fx-text-fill: #2E7D32; -fx-font-weight: bold;");
            } else {
                String varianceNote = session.getVariance() != null && Math.abs(session.getVariance()) < 0.01
                        ? "matched" : String.format("variance ₹%.2f", session.getVariance());
                cashRegisterStatusLabel.setText("Closed for today (" + varianceNote + ")");
                cashRegisterStatusLabel.setStyle("-fx-text-fill: #666;");
            }
        } catch (Exception e) {
            e.printStackTrace();
            cashRegisterStatusLabel.setText("Could not load status");
        }
    }

    private void refreshWeeklyChart() {
        if (weeklyRevenueChart == null) return;
        try {
            List<DailySalesRow> days = DashboardDAO.getLast7DaysRevenue();
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName("Revenue");
            for (DailySalesRow row : days) {
                String label = LocalDate.parse(row.getDate()).format(DateTimeFormatter.ofPattern("dd MMM"));
                series.getData().add(new XYChart.Data<>(label, row.getRevenue()));
            }
            weeklyRevenueChart.getData().clear();
            weeklyRevenueChart.getData().add(series);
            weeklyRevenueChart.setLegendVisible(false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void refreshTopProducts() {
        if (topProductsBox == null) return;
        try {
            List<TopProductRow> top = DashboardDAO.getTopSellingProducts(5, 30);
            topProductsBox.getChildren().clear();
            if (top.isEmpty()) {
                Label empty = new Label("No sales yet in the last 30 days");
                empty.setStyle("-fx-text-fill: #999; -fx-font-size: 12px;");
                topProductsBox.getChildren().add(empty);
                return;
            }
            int rank = 1;
            for (TopProductRow p : top) {
                Label row = new Label(String.format("%d. %s  -  %d sold  (₹%.2f)",
                        rank++, p.getProductName(), p.getQtySold(), p.getRevenue()));
                row.setStyle("-fx-font-size: 12px; -fx-padding: 3 0 3 0;");
                topProductsBox.getChildren().add(row);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    public void onShowCashRegister() {
        openPopup("/com/vastra/ui/fxml/cash_register.fxml", "Cash Register");
        refreshCashRegisterStatus();
    }

    /**
     * A shortcut to Excel export that doesn't require going through the full
     * Reports screen - pick a report type (and a date range where relevant),
     * choose a save location, done. The same underlying reports are also
     * available with more detail (on-screen tables, totals) from Reports.
     */
    @FXML
    public void onQuickExport() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Export to Excel");
        dialog.setHeaderText("Choose what to export");

        ButtonType exportButtonType = new ButtonType("Export", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(exportButtonType, ButtonType.CANCEL);

        javafx.scene.control.ChoiceBox<String> typeChoice = new javafx.scene.control.ChoiceBox<>(
                javafx.collections.FXCollections.observableArrayList(
                        "Sales & Purchases Archive", "Stock Valuation", "Expense Log"));
        typeChoice.setValue("Sales & Purchases Archive");

        javafx.scene.control.DatePicker fromPicker = new javafx.scene.control.DatePicker(LocalDate.now().minusDays(6));
        javafx.scene.control.DatePicker toPicker = new javafx.scene.control.DatePicker(LocalDate.now());
        Label rangeLabel = new Label("Date range:");

        // Stock Valuation is always a current snapshot - no date range needed
        Runnable updateRangeVisibility = () -> {
            boolean needsRange = !"Stock Valuation".equals(typeChoice.getValue());
            rangeLabel.setVisible(needsRange);
            rangeLabel.setManaged(needsRange);
            fromPicker.setVisible(needsRange);
            fromPicker.setManaged(needsRange);
            toPicker.setVisible(needsRange);
            toPicker.setManaged(needsRange);
        };
        typeChoice.valueProperty().addListener((o, ov, nv) -> updateRangeVisibility.run());
        updateRangeVisibility.run();

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(15));
        grid.add(new Label("Report:"), 0, 0);
        grid.add(typeChoice, 1, 0);
        grid.add(rangeLabel, 0, 1);
        javafx.scene.layout.HBox rangeBox = new javafx.scene.layout.HBox(8, fromPicker, new Label("to"), toPicker);
        grid.add(rangeBox, 1, 1);
        dialog.getDialogPane().setContent(grid);

        Button exportBtn = (Button) dialog.getDialogPane().lookupButton(exportButtonType);
        exportBtn.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            String type = typeChoice.getValue();
            boolean needsRange = !"Stock Valuation".equals(type);
            LocalDate from = fromPicker.getValue();
            LocalDate to = toPicker.getValue();
            if (needsRange && (from == null || to == null || from.isAfter(to))) {
                new Alert(Alert.AlertType.ERROR, "Pick a valid date range").showAndWait();
                event.consume();
                return;
            }

            javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
            chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Excel Workbook", "*.xlsx"));

            try {
                switch (type) {
                    case "Sales & Purchases Archive" -> {
                        chooser.setInitialFileName("sales_archive_" + from + "_to_" + to + ".xlsx");
                        File file = chooser.showSaveDialog(dialog.getOwner());
                        if (file == null) { event.consume(); return; }
                        com.vastra.util.ExcelReportUtil.generateMonthlyReport(from.toString(), to.toString(), file.getAbsolutePath());
                        showInfoStatic("Saved to:\n" + file.getAbsolutePath());
                    }
                    case "Stock Valuation" -> {
                        chooser.setInitialFileName("stock_valuation_" + LocalDate.now() + ".xlsx");
                        File file = chooser.showSaveDialog(dialog.getOwner());
                        if (file == null) { event.consume(); return; }
                        com.vastra.util.ExcelReportUtil.generateStockValuationReport(file.getAbsolutePath());
                        showInfoStatic("Saved to:\n" + file.getAbsolutePath());
                    }
                    default -> { // Expense Log
                        chooser.setInitialFileName("expenses_" + from + "_to_" + to + ".xlsx");
                        File file = chooser.showSaveDialog(dialog.getOwner());
                        if (file == null) { event.consume(); return; }
                        com.vastra.util.ExcelReportUtil.generateExpenseReport(from.toString(), to.toString(), file.getAbsolutePath());
                        showInfoStatic("Saved to:\n" + file.getAbsolutePath());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                new Alert(Alert.AlertType.ERROR, "Export failed: " + e.getMessage()).showAndWait();
                event.consume();
            }
        });

        dialog.showAndWait();
    }

    private void showInfoStatic(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, msg);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    @FXML
    public void onStartSale() {
        loadIntoStage("/com/vastra/ui/fxml/main.fxml", "Vastra");
    }

    @FXML
    public void onShowSuppliers() {
        if (!Session.requireAdmin()) return;
        openPopup("/com/vastra/ui/fxml/supplier.fxml", "Suppliers & Dues");
    }

    @FXML
    public void onShowBackup() {
        if (!Session.requireAdmin()) return;
        openPopup("/com/vastra/ui/fxml/backup.fxml", "Backup, Restore & Excel Archive");
    }

    @FXML
    public void onManageUsers() {
        if (!Session.requireAdmin()) return;
        openPopup("/com/vastra/ui/fxml/manage_users.fxml", "Manage Users", false);
    }

    @FXML
    public void onShowLowStock() {
        onStartSale(); // low stock alert lives in the POS screen for now - jump there
    }

    @FXML
    public void onLogout() {
        Session.logout();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/vastra/ui/fxml/login.fxml"));
            javafx.scene.Parent root = loader.load();
            LoginController controller = loader.getController();
            controller.setStage(stage);
            stage.setTitle("Vastra - Login");
            com.vastra.util.WindowUtil.swapRoot(stage, root);
        } catch (Exception e) {
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Error logging out: " + e.getMessage()).showAndWait();
        }
    }

    private void openPopup(String fxmlPath, String title) {
        openPopup(fxmlPath, title, true);
    }

    private void openPopup(String fxmlPath, String title, boolean resizable) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Scene scene = new Scene(loader.load());
            Stage popup = new Stage();
            popup.setTitle(title);
            popup.setResizable(resizable);
            IconUtil.applyAppIcon(popup);
            popup.setScene(scene);
            popup.show();
        } catch (Exception e) {
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Error opening screen: " + e.getMessage()).showAndWait();
        }
    }

    private void loadIntoStage(String fxmlPath, String title) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            javafx.scene.Parent root = loader.load();
            stage.setTitle(title);
            com.vastra.util.WindowUtil.swapRoot(stage, root);
        } catch (Exception e) {
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Error opening screen: " + e.getMessage()).showAndWait();
        }
    }
}