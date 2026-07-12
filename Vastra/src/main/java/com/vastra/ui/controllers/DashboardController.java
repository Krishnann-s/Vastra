package com.vastra.ui.controllers;

import com.vastra.dao.DashboardDAO;
import com.vastra.dao.StoreSettingsDAO;
import com.vastra.model.User;
import com.vastra.util.IconUtil;
import com.vastra.util.Session;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

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
        } catch (Exception e) {
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Error loading dashboard: " + e.getMessage()).showAndWait();
        }
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