package com.vastra.ui.controllers;

import com.vastra.dao.CashRegisterDAO;
import com.vastra.model.CashRegisterSession;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.util.List;
import java.util.Optional;

/**
 * Cash drawer open/close reconciliation. One of three panels is shown at a time:
 * open-a-new-session, an active session (with a live "expected cash" figure and a
 * close form), or a summary once today's session is already closed.
 */
public class CashRegisterController {

    @FXML private javafx.scene.layout.VBox openRegisterPane;
    @FXML private javafx.scene.layout.VBox activeRegisterPane;
    @FXML private javafx.scene.layout.VBox closedTodayPane;

    @FXML private TextField openingAmountField;
    @FXML private TextArea openingNotesField;

    @FXML private Label sessionInfoLabel;
    @FXML private Label expectedCashLabel;
    @FXML private TextField closingAmountField;
    @FXML private TextArea closingNotesField;

    @FXML private Label closedSummaryLabel;

    @FXML private TableView<CashRegisterSession> historyTable;
    @FXML private TableColumn<CashRegisterSession, String> hDateColumn;
    @FXML private TableColumn<CashRegisterSession, String> hOpenedByColumn;
    @FXML private TableColumn<CashRegisterSession, String> hOpeningColumn;
    @FXML private TableColumn<CashRegisterSession, String> hClosingColumn;
    @FXML private TableColumn<CashRegisterSession, String> hExpectedColumn;
    @FXML private TableColumn<CashRegisterSession, String> hVarianceColumn;
    @FXML private TableColumn<CashRegisterSession, String> hStatusColumn;

    private final ObservableList<CashRegisterSession> historyRows = FXCollections.observableArrayList();
    private CashRegisterSession todaySession;

    @FXML
    public void initialize() {
        hDateColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getBusinessDate()));
        hOpenedByColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getOpenedBy()));
        hOpeningColumn.setCellValueFactory(d -> new SimpleStringProperty(String.format("\u20B9%.2f", d.getValue().getOpening())));
        hClosingColumn.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getClosing() != null ? String.format("\u20B9%.2f", d.getValue().getClosing()) : "-"));
        hExpectedColumn.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getExpected() != null ? String.format("\u20B9%.2f", d.getValue().getExpected()) : "-"));
        hVarianceColumn.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getVariance() != null ? formatVariance(d.getValue().getVariance()) : "-"));
        hStatusColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getStatus()));
        historyTable.setItems(historyRows);

        refresh();
    }

    private String formatVariance(double variance) {
        if (Math.abs(variance) < 0.01) return "✓ Matched";
        return (variance > 0 ? "+\u20B9" : "-\u20B9") + String.format("%.2f", Math.abs(variance));
    }

    private void refresh() {
        try {
            todaySession = CashRegisterDAO.getTodaySession();

            boolean noSessionToday = todaySession == null;
            boolean openToday = todaySession != null && todaySession.isOpen();
            boolean closedToday = todaySession != null && !todaySession.isOpen();

            openRegisterPane.setVisible(noSessionToday);
            openRegisterPane.setManaged(noSessionToday);
            activeRegisterPane.setVisible(openToday);
            activeRegisterPane.setManaged(openToday);
            closedTodayPane.setVisible(closedToday);
            closedTodayPane.setManaged(closedToday);

            if (openToday) {
                sessionInfoLabel.setText(String.format("Opened at %s by %s  |  Opening float: \u20B9%.2f",
                        todaySession.getOpenedAt(), todaySession.getOpenedBy(), todaySession.getOpening()));
                refreshExpectedCash();
            } else if (closedToday) {
                closedSummaryLabel.setText(String.format(
                        "Opening: \u20B9%.2f  |  Expected: \u20B9%.2f  |  Counted: \u20B9%.2f  |  Variance: %s\nClosed at %s by %s",
                        todaySession.getOpening(), todaySession.getExpected(), todaySession.getClosing(),
                        formatVariance(todaySession.getVariance()), todaySession.getClosedAt(), todaySession.getClosedBy()));
            }

            List<CashRegisterSession> history = CashRegisterDAO.getRecentSessions(30);
            historyRows.setAll(history);

        } catch (Exception e) {
            e.printStackTrace();
            showError("Error loading cash register: " + e.getMessage());
        }
    }

    @FXML
    public void onOpenRegister() {
        try {
            String amountStr = openingAmountField.getText();
            if (amountStr == null || amountStr.isBlank()) {
                showError("Enter the opening cash amount");
                return;
            }
            double amount = Double.parseDouble(amountStr);
            if (amount < 0) {
                showError("Opening amount cannot be negative");
                return;
            }
            String openedBy = currentUserName();
            CashRegisterDAO.openRegister((int) Math.round(amount * 100), openingNotesField.getText(), openedBy);
            openingAmountField.clear();
            openingNotesField.clear();
            refresh();
        } catch (NumberFormatException e) {
            showError("Enter a valid opening amount");
        } catch (Exception e) {
            e.printStackTrace();
            showError("Error opening register: " + e.getMessage());
        }
    }

    @FXML
    public void onRefreshExpected() {
        refreshExpectedCash();
    }

    private void refreshExpectedCash() {
        if (todaySession == null) return;
        try {
            int expectedCents = CashRegisterDAO.computeExpectedCashCents(
                    todaySession.getBusinessDate(), todaySession.getOpeningCents());
            expectedCashLabel.setText(String.format("Expected cash right now: \u20B9%.2f", expectedCents / 100.0));
        } catch (Exception e) {
            e.printStackTrace();
            expectedCashLabel.setText("Could not calculate expected cash");
        }
    }

    @FXML
    public void onCloseRegister() {
        if (todaySession == null) return;
        try {
            String amountStr = closingAmountField.getText();
            if (amountStr == null || amountStr.isBlank()) {
                showError("Count the drawer and enter the closing amount");
                return;
            }
            double amount = Double.parseDouble(amountStr);
            if (amount < 0) {
                showError("Closing amount cannot be negative");
                return;
            }

            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Close the register with a counted amount of \u20B9" + String.format("%.2f", amount) + "?\n" +
                    "This can't be edited afterwards - double check your count first.");
            Optional<ButtonType> result = confirm.showAndWait();
            if (result.isEmpty() || result.get() != ButtonType.OK) return;

            CashRegisterDAO.closeRegister(todaySession.getId(), (int) Math.round(amount * 100),
                    closingNotesField.getText(), currentUserName());

            closingAmountField.clear();
            closingNotesField.clear();
            refresh();
        } catch (NumberFormatException e) {
            showError("Enter a valid closing amount");
        } catch (Exception e) {
            e.printStackTrace();
            showError("Error closing register: " + e.getMessage());
        }
    }

    private String currentUserName() {
        com.vastra.model.User user = com.vastra.util.Session.getCurrentUser();
        return user != null ? user.getDisplayName() : "Admin";
    }

    @FXML
    public void onClose() {
        ((Stage) historyTable.getScene().getWindow()).close();
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}