package com.vastra.ui.controllers;

import com.vastra.dao.UserDAO;
import com.vastra.model.User;
import com.vastra.util.Session;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import java.util.List;
import java.util.Optional;

public class ManageUsersController {

    @FXML private TextField myUsernameField;
    @FXML private TextField myDisplayNameField;

    @FXML private TextField newUsernameField;
    @FXML private TextField newDisplayNameField;
    @FXML private PasswordField newPinField;

    @FXML private TableView<User> cashierTable;
    @FXML private TableColumn<User, String> usernameColumn;
    @FXML private TableColumn<User, String> displayNameColumn;
    @FXML private TableColumn<User, String> createdColumn;
    @FXML private TableColumn<User, Void> actionColumn;

    private final ObservableList<User> cashiers = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        User me = Session.getCurrentUser();
        if (me != null) {
            myUsernameField.setText(me.getUsername());
            myDisplayNameField.setText(me.getDisplayName());
        }

        usernameColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getUsername()));
        displayNameColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDisplayName()));
        createdColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getCreatedAt()));

        actionColumn.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn = new Button("Edit");
            private final Button resetPinBtn = new Button("PIN");
            private final Button removeBtn = new Button("Del");
            private final HBox box = new HBox(4, editBtn, resetPinBtn, removeBtn);
            {
                editBtn.setTooltip(new Tooltip("Edit username / display name"));
                resetPinBtn.setTooltip(new Tooltip("Reset 4-digit PIN"));
                removeBtn.setTooltip(new Tooltip("Delete (deactivate) this cashier"));

                editBtn.setOnAction(e -> onEditCashier(getTableView().getItems().get(getIndex())));
                resetPinBtn.setOnAction(e -> onResetPin(getTableView().getItems().get(getIndex())));
                removeBtn.setOnAction(e -> onDeactivate(getTableView().getItems().get(getIndex())));

                String compact = "-fx-padding: 3 8 3 8; -fx-font-size: 11px;";
                editBtn.setStyle(compact);
                resetPinBtn.setStyle(compact);
                removeBtn.setStyle(compact + " -fx-background-color: #D32F2F; -fx-text-fill: white;");
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });

        cashierTable.setItems(cashiers);
        refresh();
    }

    private void refresh() {
        try {
            List<User> list = UserDAO.getAllCashiers();
            cashiers.setAll(list);
        } catch (Exception e) {
            showError("Error loading cashiers: " + e.getMessage());
        }
    }

    @FXML
    public void onSaveMyProfile() {
        User me = Session.getCurrentUser();
        if (me == null) return;

        String newUsername = myUsernameField.getText() == null ? "" : myUsernameField.getText().trim();
        if (newUsername.isEmpty()) {
            showError("Username cannot be empty");
            return;
        }

        try {
            UserDAO.updateAdminProfile(me.getId(), newUsername, myDisplayNameField.getText());
            me.setUsername(newUsername);
            me.setDisplayName(myDisplayNameField.getText());
            showInfo("Profile updated");
        } catch (Exception e) {
            showError("Error updating profile: " + e.getMessage());
        }
    }

    @FXML
    public void onChangeMyPassword() {
        User me = Session.getCurrentUser();
        if (me == null) return;

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Change Password");
        dialog.setHeaderText("Set a new password for " + me.getUsername());

        PasswordField newPassField = new PasswordField();
        newPassField.setPromptText("New password");
        PasswordField confirmPassField = new PasswordField();
        confirmPassField.setPromptText("Confirm new password");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.addRow(0, new Label("New Password:"), newPassField);
        grid.addRow(1, new Label("Confirm:"), confirmPassField);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;

        if (!newPassField.getText().equals(confirmPassField.getText())) {
            showError("Passwords don't match");
            return;
        }

        try {
            UserDAO.changeAdminPassword(me.getId(), newPassField.getText());
            showInfo("Password changed. Use it next time you log in.");
        } catch (Exception e) {
            showError("Error changing password: " + e.getMessage());
        }
    }

    @FXML
    public void onAddCashier() {
        String username = newUsernameField.getText() == null ? "" : newUsernameField.getText().trim();
        String pin = newPinField.getText();

        if (username.isEmpty()) {
            showError("Username is required");
            return;
        }
        if (pin == null || !pin.matches("\\d{4}")) {
            showError("PIN must be exactly 4 digits");
            return;
        }

        try {
            UserDAO.createCashier(username, newDisplayNameField.getText(), pin);
            newUsernameField.clear();
            newDisplayNameField.clear();
            newPinField.clear();
            refresh();
        } catch (Exception e) {
            showError("Error adding cashier: " + e.getMessage());
        }
    }

    private void onEditCashier(User user) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit Cashier");
        dialog.setHeaderText("Editing " + user.getUsername());

        TextField usernameField = new TextField(user.getUsername());
        TextField displayNameField = new TextField(user.getDisplayName());

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.addRow(0, new Label("Username:"), usernameField);
        grid.addRow(1, new Label("Display Name:"), displayNameField);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;

        String newUsername = usernameField.getText() == null ? "" : usernameField.getText().trim();
        if (newUsername.isEmpty()) {
            showError("Username cannot be empty");
            return;
        }

        try {
            UserDAO.updateCashier(user.getId(), newUsername, displayNameField.getText());
            refresh();
        } catch (Exception e) {
            showError("Error updating cashier: " + e.getMessage());
        }
    }

    private void onResetPin(User user) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Reset PIN");
        dialog.setHeaderText("New 4-digit PIN for " + user.getUsername());
        dialog.setContentText("PIN:");
        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty() || result.get().isBlank()) return;

        try {
            UserDAO.resetCashierPin(user.getId(), result.get().trim());
            showInfo("PIN updated for " + user.getUsername());
        } catch (Exception e) {
            showError("Error resetting PIN: " + e.getMessage());
        }
    }

    private void onDeactivate(User user) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Cashier");
        confirm.setHeaderText("Remove login access for " + user.getUsername() + "?");
        confirm.setContentText("They won't be able to log in anymore. Their sales history is kept.");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;

        try {
            UserDAO.deactivateUser(user.getId());
            refresh();
        } catch (Exception e) {
            showError("Error deleting cashier: " + e.getMessage());
        }
    }

    @FXML
    public void onBack() {
        ((Stage) cashierTable.getScene().getWindow()).close();
    }

    private void showError(String msg) {
        new Alert(Alert.AlertType.ERROR, msg).showAndWait();
    }

    private void showInfo(String msg) {
        new Alert(Alert.AlertType.INFORMATION, msg).showAndWait();
    }
}