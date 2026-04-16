package org.example.ui;

import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.util.prefs.Preferences;

public class SettingsController {
    @FXML private TextField reminderField;
    @FXML private javafx.scene.control.ChoiceBox<String> themeChoice;
    @FXML private TextField googleVisionKeyField;
    @FXML private TextField ocrSpaceKeyField;

    private final Preferences prefs = Preferences.userNodeForPackage(SettingsController.class);

    @FXML
    public void initialize() {
        reminderField.setText(String.valueOf(prefs.getInt("reminder.days", 30)));
        themeChoice.getItems().addAll("Light", "Dark");
        String theme = prefs.get("theme", "Light");
        themeChoice.getSelectionModel().select(theme.equalsIgnoreCase("Dark") ? "Dark" : "Light");

        java.util.Properties props = new java.util.Properties();
        boolean loaded = false;
        try {
            java.nio.file.Path external = java.nio.file.Paths.get("data", "config", "image-fetch-config.properties");
            if (java.nio.file.Files.exists(external)) {
                try (java.io.InputStream in = java.nio.file.Files.newInputStream(external)) {
                    props.load(in);
                    loaded = true;
                }
            }
        } catch (Exception ignore) {}
        if (!loaded) {
            try (java.io.InputStream in = getClass().getResourceAsStream("/image-fetch-config.properties")) {
                if (in != null) {
                    props.load(in);
                }
            } catch (Exception ignore) {}
        }
        if (googleVisionKeyField != null) {
            String gk = props.getProperty("google.vision.api.key", "");
            googleVisionKeyField.setText(gk);
        }
        if (ocrSpaceKeyField != null) {
            String ok = props.getProperty("ocr.api.key", "");
            ocrSpaceKeyField.setText(ok);
        }
    }

    @FXML
    public void onSave() {
        try {
            prefs.putInt("reminder.days", Integer.parseInt(reminderField.getText()));
        } catch (Exception e) {
            prefs.putInt("reminder.days", 30);
        }
        String selected = themeChoice.getSelectionModel().getSelectedItem();
        if (selected == null) selected = "Light";
        prefs.put("theme", selected);

    System.setProperty("app.theme", selected);

    org.example.ui.ThemeManager.applyTheme(selected);

    try {
        java.nio.file.Path dir = java.nio.file.Paths.get("data", "config");
        java.nio.file.Files.createDirectories(dir);
        java.nio.file.Path cfg = dir.resolve("image-fetch-config.properties");
        java.util.Properties outProps = new java.util.Properties();

        if (java.nio.file.Files.exists(cfg)) {
            try (java.io.InputStream in = java.nio.file.Files.newInputStream(cfg)) {
                outProps.load(in);
            } catch (Exception ignore) {}
        }
        if (googleVisionKeyField != null) {
            outProps.setProperty("google.vision.api.key", googleVisionKeyField.getText() == null ? "" : googleVisionKeyField.getText().trim());
        }
        if (ocrSpaceKeyField != null) {
            outProps.setProperty("ocr.api.key", ocrSpaceKeyField.getText() == null ? "" : ocrSpaceKeyField.getText().trim());
        }

        if (!outProps.containsKey("search.engine.id")) {
            try (java.io.InputStream in = getClass().getResourceAsStream("/image-fetch-config.properties")) {
                if (in != null) {
                    java.util.Properties base = new java.util.Properties();
                    base.load(in);
                    String se = base.getProperty("search.engine.id");
                    if (se != null) outProps.setProperty("search.engine.id", se);
                }
            } catch (Exception ignore) {}
        }
        try (java.io.OutputStream os = java.nio.file.Files.newOutputStream(cfg)) {
            outProps.store(os, "Smart Warranty Tracker API keys");
        }
    } catch (Exception ex) {
        ex.printStackTrace();
    }

    close();
    }

    @FXML
    public void onCancel() { close(); }

    @FXML
    public void onSendTestNotification() {
        try {
            int days = 0;
            try { days = Integer.parseInt(reminderField.getText()); } catch (Exception ignore) {}
            String title = "Smart Warranty Tracker";
            String body = days > 0 ? ("Notifications enabled • Reminder window: " + days + " days") : "Test notification from settings";
            org.example.ui.WindowsNotifier.notify(title, body);
        } catch (Throwable t) {

            javafx.scene.control.Alert a = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.WARNING, "Unable to send notification on this system.");
            a.showAndWait();
        }
    }

    private void close() {
        Stage s = (Stage) reminderField.getScene().getWindow();
        s.close();
    }
}
