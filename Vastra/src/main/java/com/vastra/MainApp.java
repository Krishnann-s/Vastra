package com.vastra;

import com.vastra.util.DBUtil;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {

    public void start(Stage stage) throws Exception {
        DBUtil.init();
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/vastra/ui/fxml/main.fxml"));
        Scene scene = new Scene(loader.load());
        stage.setTitle("Vastra");
        stage.setScene(scene);
        stage.setWidth(1000);
        stage.setHeight(700);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}
