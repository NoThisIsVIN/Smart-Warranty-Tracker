package org.example.ui;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

public class WindowsNotifier {

    public static void notify(String title, String body) {
        notify(title, body, NotificationType.INFO);
    }

    public static void notify(String title, String body, NotificationType type) {

        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> showNotification(title, body, type));
        } else {
            showNotification(title, body, type);
        }
    }
    
    private static int notificationCount = 0;
    
    private static void showNotification(String title, String body, NotificationType type) {
        try {

            String accentColor = "#3498db";
            String iconEmoji = "ℹ";
            switch (type) {
                case SUCCESS:
                    accentColor = "#2ecc71";
                    iconEmoji = "✓";
                    break;
                case WARNING:
                    accentColor = "#f39c12";
                    iconEmoji = "⚠";
                    break;
                case ERROR:
                    accentColor = "#e74c3c";
                    iconEmoji = "✕";
                    break;
            }

            VBox container = new VBox(6);
            container.setAlignment(Pos.CENTER_RIGHT);
            container.setPadding(new Insets(16, 20, 16, 20));
            container.setMinWidth(380);
            container.setMaxWidth(450);
            container.setStyle(
                "-fx-background-color: linear-gradient(to bottom, #1e293b, #0f172a);" +
                "-fx-background-radius: 12;" +
                "-fx-border-color: " + accentColor + ";" +
                "-fx-border-width: 0 0 0 4;" +
                "-fx-border-radius: 12;" +
                "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.6), 20, 0, 0, 8);"
            );

            javafx.scene.layout.HBox headerRow = new javafx.scene.layout.HBox(10);
            headerRow.setAlignment(Pos.CENTER_RIGHT);
            
            Label iconLabel = new Label(iconEmoji);
            iconLabel.setStyle("-fx-text-fill: " + accentColor + "; -fx-font-size: 20px; -fx-font-weight: bold;");
            
            Label titleLabel = new Label(title);
            titleLabel.setStyle("-fx-text-fill: #f1f5f9; -fx-font-size: 15px; -fx-font-weight: bold;");
            titleLabel.setAlignment(Pos.CENTER_RIGHT);
            
            headerRow.getChildren().addAll(titleLabel, iconLabel);

            Label bodyLabel = new Label(body);
            bodyLabel.setStyle("-fx-text-fill: #cbd5e1; -fx-font-size: 13px; -fx-line-spacing: 2px;");
            bodyLabel.setWrapText(true);
            bodyLabel.setMaxWidth(430);
            bodyLabel.setAlignment(Pos.CENTER_RIGHT);
            
            container.getChildren().addAll(headerRow, bodyLabel);

            Stage stage = new Stage();
            stage.initStyle(StageStyle.TRANSPARENT);
            stage.setAlwaysOnTop(true);
            
            Scene scene = new Scene(container);
            scene.setFill(Color.TRANSPARENT);
            stage.setScene(scene);

            Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
            stage.setX(screenBounds.getMaxX() - 490);
            stage.setY(screenBounds.getMaxY() - 120 - (notificationCount * 110));
            
            notificationCount++;

            TranslateTransition slideIn = new TranslateTransition(Duration.millis(300), container);
            slideIn.setFromX(500);
            slideIn.setToX(0);

            FadeTransition fadeIn = new FadeTransition(Duration.millis(300), container);
            fadeIn.setFromValue(0);
            fadeIn.setToValue(1);

            PauseTransition pause = new PauseTransition(Duration.seconds(6));
            pause.setOnFinished(e -> {
                FadeTransition fadeOut = new FadeTransition(Duration.millis(300), container);
                fadeOut.setFromValue(1);
                fadeOut.setToValue(0);
                fadeOut.setOnFinished(ev -> {
                    stage.close();
                    notificationCount--;
                });
                fadeOut.play();
            });
            
            stage.show();
            slideIn.play();
            fadeIn.play();
            pause.play();
            
        } catch (Exception e) {
            System.err.println("Failed to show notification: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public enum NotificationType {
        INFO,
        SUCCESS,
        WARNING,
        ERROR
    }
}
