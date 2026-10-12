package org.example.ui;

import javafx.stage.Stage;
import javafx.stage.Window;
import org.springframework.stereotype.Component;

/** Opens the modal settings window. */
@Component
public class SettingsView {
    private final ViewLoader viewLoader;

    public SettingsView(ViewLoader viewLoader) {
        this.viewLoader = viewLoader;
    }

    public void show(Window owner) {
        ViewLoader.View<SettingsController> view = viewLoader.load("/fxml/settings.fxml");
        Stage stage = viewLoader.modal("Settings", view.root(), owner);
        stage.setResizable(false);
        stage.showAndWait();
    }
}
