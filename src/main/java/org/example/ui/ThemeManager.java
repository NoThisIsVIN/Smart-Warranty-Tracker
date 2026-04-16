package org.example.ui;

import javafx.application.Platform;
import javafx.scene.Scene;

public class ThemeManager {
    private static Scene primaryScene;

    public static void setPrimaryScene(Scene scene) {
        primaryScene = scene;
    }

    public static void applyTheme(String theme) {
        if (primaryScene == null) return;
        Platform.runLater(() -> {
            try {
                String darkPath = ThemeManager.class.getResource("/styles/dark.css").toExternalForm();

                String base = ThemeManager.class.getResource("/styles/app.css").toExternalForm();
                if (!primaryScene.getStylesheets().contains(base)) {
                    primaryScene.getStylesheets().add(base);
                }
                boolean darkSelected = "Dark".equalsIgnoreCase(theme);
                boolean hasDark = primaryScene.getStylesheets().contains(darkPath);
                if (darkSelected && !hasDark) primaryScene.getStylesheets().add(darkPath);
                if (!darkSelected && hasDark) primaryScene.getStylesheets().remove(darkPath);
            } catch (Exception e) {

                System.err.println("ThemeManager.applyTheme error: " + e.getMessage());
            }
        });
    }
}
