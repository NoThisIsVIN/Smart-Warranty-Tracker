package org.example.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import org.example.model.Warranty;
import org.springframework.stereotype.Component;

@Component
public class DetailDrawerController {
    @FXML private VBox contentArea;
    @FXML private Label lblProduct;
    @FXML private Label lblExpiry;
    @FXML private ProgressBar warrantyProgress;

    private Warranty current;

    public void setWarranty(Warranty w) {
        this.current = w;
        if (w != null) {
            lblProduct.setText(w.getProductName() + " — " + (w.getModelName() == null ? "" : w.getModelName()));
            lblExpiry.setText("Expires: " + w.getExpiryDate());
            try {
                long total = java.time.temporal.ChronoUnit.DAYS.between(w.getPurchaseDate(), w.getExpiryDate());
                long elapsed = java.time.temporal.ChronoUnit.DAYS.between(w.getPurchaseDate(), java.time.LocalDate.now());
                double pct = total > 0 ? Math.max(0.0, Math.min(1.0, (double) elapsed / (double) total)) : 0.0;
                warrantyProgress.setProgress(pct);
            } catch (Exception ex) { warrantyProgress.setProgress(0); }
        }
    }

    @FXML
    public void onClose() {

        contentArea.getScene().getRoot().lookup("#detailDrawer").setVisible(false);
        contentArea.getScene().getRoot().lookup("#detailDrawer").setManaged(false);
    }
}
