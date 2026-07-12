package com.vastra.util;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class WindowUtil {
    /**
     * Swaps the visible content of a stage without creating a new Scene or
     * touching the window's size/position. Creating a new Scene on every
     * navigation is what caused the shrinking, the flicker, and the DPI
     * cutoff in earlier attempts - the window's real maximized state and
     * size are only ever set once (at true app startup); after that,
     * everything just swaps the root node inside the same Scene, which
     * JavaFX resizes correctly with zero animation.
     */
    public static void swapRoot(Stage stage, Parent newRoot) {
        Scene currentScene = stage.getScene();
        if (currentScene == null) {
            stage.setScene(new Scene(newRoot));
        } else {
            currentScene.setRoot(newRoot);
        }
    }
}