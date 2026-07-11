package com.vastra.ui.controllers;

import com.vastra.dao.DashboardDAO;
import com.vastra.dao.StoreSettingsDAO;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class DashboardController {

    @FXML private Label storeNameLabel;
    @FXML private Label dateLabel;
    @FXML private Label todaysSalesLabel;
    @FXML private Label todaysSalesCountLabel;
    @FXML private Label lowStockLabel;
    @FXML private Label outOfStockLabel;
    @FXML private Label supplierDueLabel;
    @FXML private Label customerDueLabel;

    private Stage stage;

    public void setStage(Stage stage) { this.stage = stage; }

    @FXML
    public void initialize() throws java.sql.SQLException{
        storeNameLabel.setText(StoreSettingsDAO.get("store_name", "Vastra"));
        dateLabel.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy")));
        refresh();
    }

    @FXML
    public void onRefresh() { refresh(); }

    private void refresh() {
        try {
            todaysSalesLabel.setText(String.format("₹%.2f", DashboardDAO.getTodaysSalesCents() / 100.0));
            todaysSalesCountLabel.setText(DashboardDAO.getTodaysSalesCount() + " bills");
            lowStockLabel.setText(String.valueOf(DashboardDAO.getLowStockCount()));
            outOfStockLabel.setText(DashboardDAO.getOutOfStockCount() + " out of stock");
            supplierDueLabel.setText(String.format("₹%.2f", DashboardDAO.getTotalSupplierDueCents() / 100.0));
            customerDueLabel.setText(String.format("₹%.2f", DashboardDAO.getTotalCustomerDueCents() / 100.0));
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
    public void onShowSuppliers() {openPopup("/com/vastra/ui/fxml/supplier.fxml", "Suppliers & Dues");}
    @FXML
    public void onShowBackup() {openPopup("/com/vastra/ui/fxml/backup.fxml", "Backup, Restore & Excel Archive");}

    private void openPopup(String fxmlPath, String title) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Scene scene = new Scene(loader.load());
            Stage popup = new Stage();
            popup.setTitle(title);
            com.vastra.util.IconUtil.applyAppIcon(popup);
            popup.setScene(scene);
            popup.show();
        } catch (Exception e) {
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Error opening screen: " + e.getMessage()).showAndWait();
        }
    }

    @FXML
    public void onShowLowStock() {
        onStartSale(); // low stock alert lives in the POS screen for now - jump there
    }

    private void loadIntoStage(String fxmlPath, String title) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Scene scene = new Scene(loader.load());
            stage.setTitle(title);
            stage.setScene(scene);
        } catch (Exception e) {
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Error opening screen: " + e.getMessage()).showAndWait();
        }
    }
}