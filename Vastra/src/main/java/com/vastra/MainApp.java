package com.vastra;

import com.vastra.ui.controllers.LoginController;
import com.vastra.util.AutoBackupUtil;
import com.vastra.util.DBUtil;
import com.vastra.util.IconUtil;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {

    public void start(Stage stage) throws Exception {
        DBUtil.init();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/vastra/ui/fxml/login.fxml"));
        Parent root = loader.load();

        LoginController controller = loader.getController();
        controller.setStage(stage);

        stage.setTitle("Vastra - Login");
        stage.setScene(new Scene(root));
        IconUtil.applyAppIcon(stage);

        // Auto-backup checkpoint #1: shortly after opening the app (background thread,
        // so it never delays login or the first screen appearing).
        AutoBackupUtil.runOnStartupIfDue();

        // Auto-backup checkpoint #2: just before the window actually closes, as a
        // safety net for a shop that's left the app running all day.
        stage.setOnCloseRequest(event -> AutoBackupUtil.runOnShutdownIfDue());

        // This is the ONLY place a real Scene gets created and the ONLY
        // place setMaximized is called - every screen after this reuses the
        // same Scene via WindowUtil.swapRoot(), so the window itself is
        // never touched again.
        stage.show();
        stage.setMaximized(true);
    }

    public static void main(String[] args) { launch(); }
}