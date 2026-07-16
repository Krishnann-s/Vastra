package com.vastra.ui.controllers;

import com.vastra.dao.ProductDAO;
import com.vastra.model.Product;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.List;
import java.util.Optional;

/**
 * "Full Stock": every active product - code, name, brand, category, size,
 * color, prices, and stock - in one searchable, editable table. Replaces the
 * old Stock Matrix (size/color grid), which only showed products that had
 * size/color set and none of the other details (code, prices, category).
 */
public class ProductListController {

    @FXML private TextField searchField;
    @FXML private TableView<Product> table;
    @FXML private TableColumn<Product, String> codeColumn;
    @FXML private TableColumn<Product, String> nameColumn;
    @FXML private TableColumn<Product, String> brandColumn;
    @FXML private TableColumn<Product, String> categoryColumn;
    @FXML private TableColumn<Product, String> sizeColumn;
    @FXML private TableColumn<Product, String> colorColumn;
    @FXML private TableColumn<Product, Number> mrpColumn;
    @FXML private TableColumn<Product, Number> sellPriceColumn;
    @FXML private TableColumn<Product, Number> purchasePriceColumn;
    @FXML private TableColumn<Product, Number> stockColumn;
    @FXML private TableColumn<Product, Number> openingStockColumn;
    @FXML private TableColumn<Product, Void> actionsColumn;
    @FXML private Label statusLabel;

    private final ObservableList<Product> products = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        codeColumn.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getSku() != null && !d.getValue().getSku().isEmpty()
                        ? d.getValue().getSku() : d.getValue().getBarcode()));
        nameColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getFullDisplayName()));
        brandColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getBrand()));
        categoryColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getCategory()));
        sizeColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getSize()));
        colorColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getColor()));
        mrpColumn.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().getMrp()));
        sellPriceColumn.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().getSellPrice()));
        purchasePriceColumn.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().getPurchasePrice()));
        stockColumn.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().getStock()));
        openingStockColumn.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().getOpeningStock()));

        actionsColumn.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn = new Button("Edit");
            private final Button deleteBtn = new Button("Delete");
            private final HBox box = new HBox(6, editBtn, deleteBtn);
            {
                editBtn.setOnAction(e -> onEdit(getTableView().getItems().get(getIndex())));
                deleteBtn.setOnAction(e -> onDelete(getTableView().getItems().get(getIndex())));
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });

        table.setItems(products);
        loadAll();
    }

    @FXML
    public void onSearch() {
        try {
            String term = searchField.getText() == null ? "" : searchField.getText().trim();
            List<Product> results = term.isEmpty() ? ProductDAO.getAllProducts() : ProductDAO.searchByName(term);
            products.setAll(results);
            statusLabel.setText(results.size() + " product(s)");
        } catch (Exception e) {
            showError("Search failed: " + e.getMessage());
        }
    }

    private void loadAll() {
        try {
            List<Product> all = ProductDAO.getAllProducts();
            products.setAll(all);
            statusLabel.setText(all.size() + " product(s)");
        } catch (Exception e) {
            showError("Error loading products: " + e.getMessage());
        }
    }

    private void onEdit(Product product) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/vastra/ui/fxml/product_form.fxml"));
            Scene scene = new Scene(loader.load(), 800, 700);
            ProductFormController controller = loader.getController();
            controller.setEditingProduct(product);

            Stage stage = new Stage();
            stage.setTitle("Edit Product");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(scene);
            stage.showAndWait();

            loadAll();
        } catch (Exception e) {
            e.printStackTrace();
            showError("Error opening edit form: " + e.getMessage());
        }
    }

    private void onDelete(Product product) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Product");
        confirm.setHeaderText("Delete \"" + product.getFullDisplayName() + "\"?");
        confirm.setContentText("This removes it from search and billing. Past sales/reports are unaffected.");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;

        try {
            ProductDAO.deactivateProduct(product.getId());
            loadAll();
        } catch (Exception e) {
            showError("Error deleting product: " + e.getMessage());
        }
    }

    @FXML
    public void onClose() {
        ((Stage) table.getScene().getWindow()).close();
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
