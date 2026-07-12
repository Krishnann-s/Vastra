package com.vastra.ui.controllers;

import com.vastra.model.ActivityLogEntry;
import com.vastra.util.ActivityLogUtil;
import com.vastra.util.BackupUtil;
import com.vastra.util.ExcelReportUtil;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BackupController {

    @FXML private Label lastBackupLabel;
    @FXML private TableView<File> backupTable;
    @FXML private TableColumn<File, String> backupNameColumn;
    @FXML private TableColumn<File, String> backupSizeColumn;

    @FXML private ComboBox<Integer> yearCombo;
    @FXML private ComboBox<String> monthCombo;

    @FXML private TableView<ActivityLogEntry> deletionsTable;
    @FXML private TableColumn<ActivityLogEntry, String> delTimeColumn;
    @FXML private TableColumn<ActivityLogEntry, String> delTypeColumn;
    @FXML private TableColumn<ActivityLogEntry, String> delDetailsColumn;

    private final ObservableList<File> backups = FXCollections.observableArrayList();
    private final ObservableList<ActivityLogEntry> deletions = FXCollections.observableArrayList();

    private static final String[] MONTH_NAMES = {
            "Whole Year", "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
    };

    @FXML
    public void initialize() {
        backupNameColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getName()));
        backupSizeColumn.setCellValueFactory(d -> new SimpleStringProperty(formatSize(d.getValue().length())));
        backupTable.setItems(backups);

        delTimeColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTimestamp()));
        delTypeColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getEntityType()));
        delDetailsColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDetails()));
        deletionsTable.setItems(deletions);

        int currentYear = LocalDate.now().getYear();
        List<Integer> years = new ArrayList<>();
        for (int y = currentYear; y >= currentYear - 5; y--) years.add(y);
        yearCombo.setItems(FXCollections.observableArrayList(years));
        yearCombo.setValue(currentYear);

        monthCombo.setItems(FXCollections.observableArrayList(MONTH_NAMES));
        monthCombo.setValue("Whole Year");

        refreshBackupList();
        refreshDeletionsList();
        refreshLastBackupLabel();
    }

    private void refreshBackupList() {
        backups.setAll(BackupUtil.listBackups());
    }

    private void refreshDeletionsList() {
        try {
            deletions.setAll(ActivityLogUtil.getRecentDeletions(50));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void refreshLastBackupLabel() {
        try {
            String last = BackupUtil.getLastBackupTimestamp();
            if (last == null) {
                lastBackupLabel.setText("⚠ No backup has been taken yet");
                lastBackupLabel.setStyle("-fx-text-fill: #D32F2F; -fx-font-weight: bold;");
            } else if (BackupUtil.wasBackedUpToday()) {
                lastBackupLabel.setText("✓ Last backup: " + last + " (today)");
                lastBackupLabel.setStyle("-fx-text-fill: #388E3C; -fx-font-weight: bold;");
            } else {
                lastBackupLabel.setText("⚠ Last backup: " + last + " (not today - back up before closing!)");
                lastBackupLabel.setStyle("-fx-text-fill: #F57C00; -fx-font-weight: bold;");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    public void onBackupNow() {
        try {
            File backupFile = BackupUtil.createBackup();
            refreshBackupList();
            refreshLastBackupLabel();
            showInfo("Backup created:\n" + backupFile.getAbsolutePath() +
                    "\nSize: " + formatSize(backupFile.length()));
        } catch (Exception e) {
            e.printStackTrace();
            showError("Backup failed: " + e.getMessage());
        }
    }

    @FXML
    public void onRestoreSelected() {
        File selected = backupTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Select a backup from the list first");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Restore Backup");
        confirm.setHeaderText("This will REPLACE your current data with:\n" + selected.getName());
        confirm.setContentText("Your current data will be safety-backed-up first, but after this you MUST restart the app. Continue?");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;

        try {
            BackupUtil.restoreBackup(selected);
            refreshBackupList();
            refreshLastBackupLabel();
            showInfo("Restore complete. Please CLOSE and RESTART the application now so all screens reload the restored data.");
        } catch (Exception e) {
            e.printStackTrace();
            showError("Restore failed: " + e.getMessage());
        }
    }

    @FXML
    public void onExportExcel() {
        Integer year = yearCombo.getValue();
        String monthName = monthCombo.getValue();
        Integer month = null;
        if (monthName != null && !monthName.equals("Whole Year")) {
            for (int i = 1; i < MONTH_NAMES.length; i++) {
                if (MONTH_NAMES[i].equals(monthName)) { month = i; break; }
            }
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Excel Archive");
        String suggestedName = "vastra_archive_" + year + (month != null ? "_" + String.format("%02d", month) : "") + ".xlsx";
        chooser.setInitialFileName(suggestedName);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Workbook", "*.xlsx"));
        Window ownerWindow = lastBackupLabel.getScene().getWindow();
        File file = chooser.showSaveDialog(ownerWindow);
        if (file == null) return;

        try {
            ExcelReportUtil.generateYearlyArchive(year, month, file.getAbsolutePath());
            showInfo("Excel archive saved to:\n" + file.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
            showError("Export failed: " + e.getMessage());
        }
    }

    @FXML
    public void onBack() {
        ((Stage) lastBackupLabel.getScene().getWindow()).close();
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
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