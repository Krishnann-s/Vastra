package com.vastra.ui.controllers;

import com.vastra.dao.UserDAO;
import com.vastra.model.User;
import com.vastra.util.Session;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

public class LoginController {

    @FXML private ComboBox<String> roleCombo;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField pinField;
    @FXML private Label errorLabel;

    private Stage stage;

    public void setStage(Stage stage) { this.stage = stage; }

    @FXML
    public void initialize() {
        roleCombo.setItems(FXCollections.observableArrayList("Admin", "Cashier"));
        roleCombo.setValue("Admin");
        roleCombo.valueProperty().addListener((obs, old, val) -> {
            boolean isCashier = "Cashier".equals(val);
            passwordField.setVisible(!isCashier);
            passwordField.setManaged(!isCashier);
            pinField.setVisible(isCashier);
            pinField.setManaged(isCashier);
            errorLabel.setText("");
        });

        pinField.textProperty().addListener((obs, old, val) -> {
            String digitsOnly = val.replaceAll("[^0-9]", "");
            if (digitsOnly.length() > 4) digitsOnly = digitsOnly.substring(0, 4);
            if (!digitsOnly.equals(val)) pinField.setText(digitsOnly);
        });
    }

    @FXML
    public void onLogin() {
        String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
        if (username.isEmpty()) {
            errorLabel.setText("Enter a username");
            return;
        }

        boolean isCashier = "Cashier".equals(roleCombo.getValue());

        try {
            User user;
            if (isCashier) {
                String pin = pinField.getText();
                if (pin == null || pin.length() != 4) {
                    errorLabel.setText("Enter your 4-digit PIN");
                    return;
                }
                user = UserDAO.verifyCashierLogin(username, pin);
            } else {
                String password = passwordField.getText();
                if (password == null || password.isBlank()) {
                    errorLabel.setText("Enter your password");
                    return;
                }
                user = UserDAO.verifyAdminLogin(username, password);
            }

            if (user == null) {
                errorLabel.setText("Incorrect username/" + (isCashier ? "PIN" : "password") + ", or account not found");
                return;
            }

            Session.setCurrentUser(user);
            openDashboard();

        } catch (Exception e) {
            e.printStackTrace();
            errorLabel.setText("Login failed: " + e.getMessage());
        }
    }

    private void openDashboard() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/vastra/ui/fxml/dashboard.fxml"));
            javafx.scene.Parent root = loader.load();
            DashboardController controller = loader.getController();
            controller.setStage(stage);
            stage.setTitle("Vastra");
            com.vastra.util.WindowUtil.swapRoot(stage, root);
        } catch (Exception e) {
            e.printStackTrace();
            errorLabel.setText("Error opening dashboard: " + e.getMessage());
        }
    }
}