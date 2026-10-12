package org.example.ui;

import javafx.stage.Stage;
import javafx.stage.Window;
import org.example.model.Warranty;
import org.springframework.stereotype.Component;

/** Opens the modal warranty detail window. */
@Component
public class DetailView {
    private final ViewLoader viewLoader;

    public DetailView(ViewLoader viewLoader) {
        this.viewLoader = viewLoader;
    }

    /** Blocks until the window closes; the warranty may have been edited or deleted, so callers should refresh. */
    public void show(Window owner, Warranty warranty) {
        ViewLoader.View<DetailController> view = viewLoader.load("/fxml/detail.fxml");
        view.controller().setWarranty(warranty);
        Stage stage = viewLoader.modal("Warranty details", view.root(), owner);
        stage.showAndWait();
    }
}
