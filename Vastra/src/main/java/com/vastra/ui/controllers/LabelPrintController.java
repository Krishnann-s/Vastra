package com.vastra.ui.controllers;

import com.vastra.dao.ProductDAO;
import com.vastra.model.LabelPrintItem;
import com.vastra.model.Product;
import com.vastra.util.LabelPrintUtil;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.converter.IntegerStringConverter;

import java.io.File;
import java.util.List;

public class LabelPrintController {

    @FXML private TextField searchField;
    @FXML private TableView<Product> searchResultsTable;
    @FXML private TableColumn<Product, String> resultNameColumn;
    @FXML private TableColumn<Product, String> resultCodeColumn;
    @FXML private TableColumn<Product, Number> resultPriceColumn;

    @FXML private TableView<LabelPrintItem> queueTable;
    @FXML private TableColumn<LabelPrintItem, String> queueNameColumn;
    @FXML private TableColumn<LabelPrintItem, Integer> queueQtyColumn;
    @FXML private TableColumn<LabelPrintItem, Void> queueRemoveColumn;

    @FXML private ScrollPane previewPane;
    @FXML private TextField careNoteField;
    @FXML private Label statusLabel;

    private final ObservableList<Product> searchResults = FXCollections.observableArrayList();
    private final ObservableList<LabelPrintItem> queue = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        resultNameColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue().getFullDisplayName()));
        resultCodeColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(
                        data.getValue().getSku() != null && !data.getValue().getSku().isEmpty()
                                ? data.getValue().getSku() : data.getValue().getBarcode()));
        resultPriceColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleDoubleProperty(data.getValue().getSellPrice()));
        searchResultsTable.setItems(searchResults);

        queueNameColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue().getProduct().getFullDisplayName()));

        queueQtyColumn.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        queueQtyColumn.setCellFactory(TextFieldTableCell.forTableColumn(new IntegerStringConverter()));
        queueQtyColumn.setOnEditCommit(event -> {
            event.getRowValue().setQuantity(event.getNewValue());
            queueTable.refresh();
        });

        queueRemoveColumn.setCellFactory(col -> new TableCell<>() {
            private final Button removeBtn = new Button("Remove");
            {
                removeBtn.setOnAction(e -> {
                    LabelPrintItem item = getTableView().getItems().get(getIndex());
                    queue.remove(item);
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : removeBtn);
            }
        });

        queueTable.setItems(queue);
        queueTable.setEditable(true);
    }

    @FXML
    public void onSearch() {
        try {
            String term = searchField.getText() == null ? "" : searchField.getText().trim();
            List<Product> results = term.isEmpty()
                    ? ProductDAO.getAllProducts()
                    : ProductDAO.searchByName(term);
            searchResults.setAll(results);
        } catch (Exception e) {
            showError("Error searching products: " + e.getMessage());
        }
    }

    @FXML
    public void onAddToQueue() {
        Product selected = searchResultsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Select a product first");
            return;
        }
        for (LabelPrintItem item : queue) {
            if (item.getProduct().getId().equals(selected.getId())) {
                item.setQuantity(item.getQuantity() + 1);
                queueTable.refresh();
                return;
            }
        }
        queue.add(new LabelPrintItem(selected, 1));
    }

    @FXML
    public void onPreview() {
        String careNote = careNoteField != null ? careNoteField.getText() : null;
        if (queue.isEmpty()) {
            showError("Add at least one product to the queue first");
            return;
        }
        previewPane.setContent(LabelPrintUtil.buildPreviewSheet(queue, careNote));
        int totalLabels = queue.stream().mapToInt(LabelPrintItem::getQuantity).sum();
        int rows = (int) Math.ceil(totalLabels / (double) LabelPrintUtil.LABELS_PER_ROW);
        statusLabel.setText(totalLabels + " labels queued (" + rows + " rows / print feeds)");
    }

    @FXML
    public void onPrint() {
        if (queue.isEmpty()) {
            showError("Add at least one product to the queue first");
            return;
        }
        int totalLabels = queue.stream().mapToInt(LabelPrintItem::getQuantity).sum();

        String careNote = careNoteField != null ? careNoteField.getText() : null;
        boolean success = LabelPrintUtil.printLabels(queue, careNote, previewPane.getScene().getWindow());
        if (success) {
            statusLabel.setText("Printed " + totalLabels + " label(s).");
        } else {
            showError("Printing failed or was cancelled. Check that the TSC printer is powered on and " +
                    "connected, and that it's selected under Settings > Label Printer (or picked correctly " +
                    "in the printer dialog). See the console/log for the exact error.");
        }
    }

    @FXML
    public void onExportPng() {
        if (queue.isEmpty()) {
            showError("Add at least one product to the queue first");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Label Preview PNG");
        chooser.setInitialFileName("label_preview.png");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG Image", "*.png"));
        Window ownerWindow = careNoteField.getScene().getWindow();
        File file = chooser.showSaveDialog(ownerWindow);
        if (file == null) return;

        String careNote = careNoteField != null ? careNoteField.getText() : null;
        boolean success = LabelPrintUtil.exportPreviewAsPng(queue, careNote, file);
        if (success) {
            showInfo("Saved to " + file.getAbsolutePath() +
                    "\nPrint it at 100% scale (not \"fit to page\") to test against a real label.");
        } else {
            showError("Could not export PNG. See console for details.");
        }
    }

    @FXML
    public void onClearQueue() {
        queue.clear();
        previewPane.setContent(null);
        statusLabel.setText("");
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