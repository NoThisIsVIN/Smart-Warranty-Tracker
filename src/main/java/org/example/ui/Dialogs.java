package org.example.ui;

import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;

/** Themed alert helpers. Call on the FX thread. */
public final class Dialogs {
    private static volatile ThemeManager themeManager;

    private Dialogs() {}

    public static void setThemeManager(ThemeManager manager) {
        themeManager = manager;
    }

    /** Danger-styled confirmation; Cancel is the default button. Returns true only when {@code confirmLabel} was pressed. */
    public static boolean confirm(Window owner, String title, String message, String confirmLabel) {
        ButtonType confirm = new ButtonType(confirmLabel, ButtonBar.ButtonData.OK_DONE);
        Alert alert = create(Alert.AlertType.CONFIRMATION, owner, title, message, confirm, ButtonType.CANCEL);
        Button confirmButton = (Button) alert.getDialogPane().lookupButton(confirm);
        Button cancelButton = (Button) alert.getDialogPane().lookupButton(ButtonType.CANCEL);
        confirmButton.getStyleClass().add("btn-danger");
        confirmButton.setDefaultButton(false);
        cancelButton.getStyleClass().add("btn-secondary");
        cancelButton.setDefaultButton(true);
        return alert.showAndWait().filter(confirm::equals).isPresent();
    }

    public static void error(Window owner, String title, String message) {
        create(Alert.AlertType.ERROR, owner, title, message, ButtonType.OK).showAndWait();
    }

    private static Alert create(Alert.AlertType type, Window owner, String title, String message, ButtonType... buttons) {
        Alert alert = new Alert(type, message == null ? "" : message, buttons);
        if (owner != null) alert.initOwner(owner);
        alert.setTitle("Warranty Tracker");
        alert.setHeaderText(title);
        ThemeManager manager = themeManager;
        if (manager != null) manager.apply(alert.getDialogPane());
        return alert;
    }
}
