package com.vastra.ui.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;


public class MainController {
    @FXML private TextField scanField;
    @FXML private TableView<?> cartTable;
    @FXML private Label totalLabel;

    @FXML
    public void initialize(){
        scanField.requestFocus();
        scanField.setOnAction(e -> {
            String code = scanField.getText().trim();
            if(!code.isEmpty()) {
                // TODO: lookup product via ProductDAO.findById(Code) and add to cart
                scanField.clear();
            }
        });
    }

    @FXML
    public void onAddProduct() {
        // TODO: open product form window
    }
}
