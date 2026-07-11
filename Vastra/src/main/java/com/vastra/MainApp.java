package com.vastra;

import com.vastra.util.DBUtil;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import com.vastra.ui.controllers.DashboardController;
import com.vastra.util.IconUtil;

public class MainApp extends Application {

    public void start(Stage stage) throws Exception {
        DBUtil.init();
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/vastra/ui/fxml/dashboard.fxml"));
        Scene scene = new Scene(loader.load());

        DashboardController controller = loader.getController();
        controller.setStage(stage);

        stage.setTitle("Vastra");
        stage.setScene(scene);
        IconUtil.applyAppIcon(stage);
        stage.setWidth(1100);
        stage.setHeight(700);
        stage.show();
    }

    public static void main(String[] args) { launch(); }
}
