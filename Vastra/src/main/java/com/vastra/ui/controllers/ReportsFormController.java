package com.vastra.ui.controllers;

import com.vastra.dao.ExpenseDAO;
import com.vastra.model.DailySalesRow;
import com.vastra.model.Expense;
import com.vastra.model.StockValuationRow;
import com.vastra.service.ReportService;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.File;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class ReportsFormController {

    // ---- Daily Sales tab ----
    @FXML private DatePicker dailyStartDatePicker;
    @FXML private DatePicker dailyEndDatePicker;
    @FXML private TableView<DailySalesRow> dailySalesTable;
    @FXML private TableColumn<DailySalesRow, String> dsDateColumn;
    @FXML private TableColumn<DailySalesRow, Number> dsCountColumn;
    @FXML private TableColumn<DailySalesRow, Number> dsRevenueColumn;
    @FXML private TableColumn<DailySalesRow, Number> dsTaxColumn;
    @FXML private TableColumn<DailySalesRow, Number> dsDiscountColumn;
    @FXML private Label dailyTotalsLabel;

    // ---- Stock Valuation tab ----
    @FXML private TableView<StockValuationRow> stockTable;
    @FXML private TableColumn<StockValuationRow, String> svProductColumn;
    @FXML private TableColumn<StockValuationRow, String> svCategoryColumn;
    @FXML private TableColumn<StockValuationRow, Number> svStockColumn;
    @FXML private TableColumn<StockValuationRow, Number> svCostPriceColumn;
    @FXML private TableColumn<StockValuationRow, Number> svSellPriceColumn;
    @FXML private TableColumn<StockValuationRow, Number> svCostValueColumn;
    @FXML private TableColumn<StockValuationRow, Number> svSellValueColumn;
    @FXML private Label stockTotalsLabel;

    // ---- Expenses tab ----
    @FXML private DatePicker expenseStartDatePicker;
    @FXML private DatePicker expenseEndDatePicker;
    @FXML private ComboBox<String> expenseCategoryCombo;
    @FXML private TextField expenseDescField;
    @FXML private TextField expenseAmountField;
    @FXML private DatePicker expenseDatePicker;
    @FXML private ComboBox<String> expensePaymentModeCombo;
    @FXML private TableView<Expense> expensesTable;
    @FXML private TableColumn<Expense, String> exDateColumn;
    @FXML private TableColumn<Expense, String> exCategoryColumn;
    @FXML private TableColumn<Expense, String> exDescColumn;
    @FXML private TableColumn<Expense, Number> exAmountColumn;
    @FXML private TableColumn<Expense, String> exModeColumn;
    @FXML private TableColumn<Expense, Void> exActionColumn;
    @FXML private Label expenseTotalLabel;

    private final ObservableList<DailySalesRow> dailyRows = FXCollections.observableArrayList();
    private final ObservableList<StockValuationRow> stockRows = FXCollections.observableArrayList();
    private final ObservableList<Expense> expenseRows = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupDailySalesTab();
        setupStockValuationTab();
        setupExpensesTab();

        onLoadDailySales();
        onLoadStockValuation();
        onLoadExpenses();
    }

    // ---------------------------------------------------------------- Daily Sales

    private void setupDailySalesTab() {
        LocalDate today = LocalDate.now();
        dailyStartDatePicker.setValue(today.minusDays(6));
        dailyEndDatePicker.setValue(today);

        dsDateColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDate()));
        dsCountColumn.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().getNumSales()));
        dsRevenueColumn.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().getRevenue()));
        dsTaxColumn.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().getTax()));
        dsDiscountColumn.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().getDiscount()));
        dailySalesTable.setItems(dailyRows);
    }

    @FXML
    public void onLoadDailySales() {
        try {
            LocalDate start = dailyStartDatePicker.getValue();
            LocalDate end = dailyEndDatePicker.getValue();
            if (start == null || end == null) return;
            if (start.isAfter(end)) { showError("Start date must be before end date"); return; }

            List<DailySalesRow> rows = ReportService.getDailySales(start.toString(), end.toString());
            dailyRows.setAll(rows);

            int sales = 0; double revenue = 0, tax = 0, discount = 0;
            for (DailySalesRow r : rows) {
                sales += r.getNumSales();
                revenue += r.getRevenue();
                tax += r.getTax();
                discount += r.getDiscount();
            }
            dailyTotalsLabel.setText(String.format(
                    "%d sale%s  |  Revenue: \u20B9%.2f  |  Tax: \u20B9%.2f  |  Discount given: \u20B9%.2f",
                    sales, sales == 1 ? "" : "s", revenue, tax, discount));
        } catch (Exception e) {
            e.printStackTrace();
            showError("Error loading sales report: " + e.getMessage());
        }
    }

    @FXML
    public void onExportDailySales() {
        LocalDate start = dailyStartDatePicker.getValue();
        LocalDate end = dailyEndDatePicker.getValue();
        if (start == null || end == null) return;

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Daily Sales Report");
        chooser.setInitialFileName("sales_report_" + start + "_to_" + end + ".xlsx");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Workbook", "*.xlsx"));
        File file = chooser.showSaveDialog(ownerWindow());
        if (file == null) return;

        try {
            com.vastra.util.ExcelReportUtil.generateMonthlyReport(start.toString(), end.toString(), file.getAbsolutePath());
            showInfo("Report saved to:\n" + file.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
            showError("Export failed: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- Stock Valuation

    private void setupStockValuationTab() {
        svProductColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getProductName()));
        svCategoryColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getCategory()));
        svStockColumn.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().getStock()));
        svCostPriceColumn.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().getCostPrice()));
        svSellPriceColumn.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().getSellPrice()));
        svCostValueColumn.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().getCostValue()));
        svSellValueColumn.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().getSellValue()));
        stockTable.setItems(stockRows);
    }

    @FXML
    public void onLoadStockValuation() {
        try {
            List<StockValuationRow> rows = ReportService.getStockValuation();
            stockRows.setAll(rows);

            double totalCost = 0, totalSell = 0;
            for (StockValuationRow r : rows) {
                totalCost += r.getCostValue();
                totalSell += r.getSellValue();
            }
            stockTotalsLabel.setText(String.format(
                    "%d product%s on shelf  |  Stock at cost: \u20B9%.2f  |  Stock at selling price: \u20B9%.2f  |  Potential profit: \u20B9%.2f",
                    rows.size(), rows.size() == 1 ? "" : "s", totalCost, totalSell, totalSell - totalCost));
        } catch (Exception e) {
            e.printStackTrace();
            showError("Error loading stock valuation: " + e.getMessage());
        }
    }

    @FXML
    public void onExportStockValuation() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Stock Valuation Report");
        chooser.setInitialFileName("stock_valuation_" + LocalDate.now() + ".xlsx");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Workbook", "*.xlsx"));
        File file = chooser.showSaveDialog(ownerWindow());
        if (file == null) return;

        try {
            com.vastra.util.ExcelReportUtil.generateStockValuationReport(file.getAbsolutePath());
            showInfo("Report saved to:\n" + file.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
            showError("Export failed: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- Expenses

    private void setupExpensesTab() {
        LocalDate today = LocalDate.now();
        expenseStartDatePicker.setValue(today.withDayOfMonth(1));
        expenseEndDatePicker.setValue(today);
        expenseDatePicker.setValue(today);

        expenseCategoryCombo.setItems(FXCollections.observableArrayList(ExpenseDAO.CATEGORIES));
        expensePaymentModeCombo.setItems(FXCollections.observableArrayList("CASH", "CARD", "UPI", "BANK TRANSFER"));
        expensePaymentModeCombo.setValue("CASH");

        exDateColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getExpenseDate()));
        exCategoryColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getCategory()));
        exDescColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDescription()));
        exAmountColumn.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().getAmount()));
        exModeColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getPaymentMode()));
        exActionColumn.setCellFactory(col -> new TableCell<>() {
            private final Button deleteBtn = new Button("Delete");
            {
                deleteBtn.setStyle("-fx-background-color: #F44336; -fx-text-fill: white;");
                deleteBtn.setOnAction(e -> {
                    Expense expense = getTableView().getItems().get(getIndex());
                    try {
                        ExpenseDAO.deleteExpense(expense.getId());
                        onLoadExpenses();
                    } catch (Exception ex) {
                        showError("Could not delete expense: " + ex.getMessage());
                    }
                });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : deleteBtn);
            }
        });
        expensesTable.setItems(expenseRows);
    }

    @FXML
    public void onAddExpense() {
        String category = expenseCategoryCombo.getValue();
        String desc = expenseDescField.getText() != null ? expenseDescField.getText().trim() : "";
        String amountStr = expenseAmountField.getText();
        LocalDate date = expenseDatePicker.getValue();
        String mode = expensePaymentModeCombo.getValue();

        if (category == null || category.isBlank()) { showError("Choose a category"); return; }
        if (amountStr == null || amountStr.isBlank()) { showError("Enter an amount"); return; }
        if (date == null) { showError("Choose a date"); return; }

        try {
            int amountCents = (int) Math.round(Double.parseDouble(amountStr) * 100);
            if (amountCents <= 0) { showError("Enter a positive amount"); return; }

            ExpenseDAO.addExpense(category, desc, amountCents, date.toString(), mode, null);

            expenseDescField.clear();
            expenseAmountField.clear();
            onLoadExpenses();
        } catch (NumberFormatException e) {
            showError("Enter a valid amount");
        } catch (Exception e) {
            e.printStackTrace();
            showError("Error saving expense: " + e.getMessage());
        }
    }

    @FXML
    public void onLoadExpenses() {
        try {
            LocalDate start = expenseStartDatePicker.getValue();
            LocalDate end = expenseEndDatePicker.getValue();
            if (start == null || end == null) return;
            if (start.isAfter(end)) { showError("Start date must be before end date"); return; }

            List<Expense> rows = ReportService.getExpenses(start.toString(), end.toString());
            expenseRows.setAll(rows);

            double total = 0;
            for (Expense e : rows) total += e.getAmount();
            expenseTotalLabel.setText(String.format("%d expense%s  |  Total: \u20B9%.2f",
                    rows.size(), rows.size() == 1 ? "" : "s", total));
        } catch (Exception e) {
            e.printStackTrace();
            showError("Error loading expenses: " + e.getMessage());
        }
    }

    @FXML
    public void onExportExpenses() {
        LocalDate start = expenseStartDatePicker.getValue();
        LocalDate end = expenseEndDatePicker.getValue();
        if (start == null || end == null) return;

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Expense Report");
        chooser.setInitialFileName("expenses_" + start + "_to_" + end + ".xlsx");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Workbook", "*.xlsx"));
        File file = chooser.showSaveDialog(ownerWindow());
        if (file == null) return;

        try {
            com.vastra.util.ExcelReportUtil.generateExpenseReport(start.toString(), end.toString(), file.getAbsolutePath());
            showInfo("Report saved to:\n" + file.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
            showError("Export failed: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- Shared

    @FXML
    public void onBack() {
        ((Stage) dailySalesTable.getScene().getWindow()).close();
    }

    private Window ownerWindow() {
        return dailySalesTable.getScene().getWindow();
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private void showInfo(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, msg);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}