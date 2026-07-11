package com.vastra.util;

import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.InputStream;

public class IconUtil {

    private static final String IMAGES_PATH = "/com/vastra/ui/images/";
    private static final String[] ICON_FILES = {
            "app_icon_16.png", "app_icon_32.png", "app_icon_48.png", "app_icon_256.png"
    };

    /** Adds every available resolution so Windows/JavaFX can pick the sharpest one for each context. */
    public static void applyAppIcon(Stage stage) {
        boolean any = false;
        for (String name : ICON_FILES) {
            try (InputStream is = IconUtil.class.getResourceAsStream(IMAGES_PATH + name)) {
                if (is != null) {
                    stage.getIcons().add(new Image(is));
                    any = true;
                }
            } catch (Exception ignored) { }
        }
        if (!any) System.err.println("[IconUtil] No icon files found in " + IMAGES_PATH);
    }
}