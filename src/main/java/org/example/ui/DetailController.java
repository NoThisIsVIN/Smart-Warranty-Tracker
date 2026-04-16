package org.example.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javafx.stage.FileChooser;
import org.example.model.Warranty;
import org.example.recommender.SimpleRecommender;
import org.example.service.WarrantyService;
import javafx.scene.image.ImageView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ListView;
import javafx.scene.control.ListCell;
import javafx.scene.control.Hyperlink;
import org.springframework.beans.factory.annotation.Autowired;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

public class DetailController {
    @FXML private Label lblProduct;
    @FXML private Label lblModel;
    @FXML private Label lblRetailer;
    @FXML private Label lblPurchase;
    @FXML private Label lblExpiry;
    @FXML private Label lblMonths;
    @FXML private Label lblStatus;
    @FXML private Label lblCategory;
    @FXML private ImageView productImage;
    @FXML private ImageView receiptImage;
    @FXML private ProgressBar warrantyProgress;
    @FXML private Label lblPercent;
    @FXML private ListView<String> recommendationsList;

    private Warranty warranty;
    @Autowired
    private WarrantyService warrantyService;
    private SimpleRecommender recommender;

    public void setWarrantyService(WarrantyService service) {
        this.warrantyService = service;
    }

    public void setRecommender(SimpleRecommender r) {
        this.recommender = r;
    }

    public void setWarranty(Warranty w) {
        this.warranty = w;
        if (w != null) {

            lblPurchase.setText(w.getPurchaseDate() == null ? "" : w.getPurchaseDate().toString());
            lblExpiry.setText(w.getExpiryDate() == null ? "" : w.getExpiryDate().toString());
            lblMonths.setText(String.valueOf(w.getWarrantyMonths()));
            if (lblCategory != null) {
                lblCategory.setText(w.getRetailer() == null ? "" : w.getRetailer());
            }

            if (lblStatus != null) {
                try {
                    long daysLeft = w.getDaysLeft();
                    if (daysLeft < 0) {
                        lblStatus.setText("expired");
                        lblStatus.getStyleClass().removeAll("status-label");
                        lblStatus.getStyleClass().addAll("status-label", "badge-expired");
                    } else {
                        lblStatus.setText(daysLeft + " days left");
                        lblStatus.getStyleClass().removeAll("status-label");
                        lblStatus.getStyleClass().addAll("status-label", "badge-active");
                    }
                } catch (Exception ex) {
                    lblStatus.setText("");
                }
            }

            try {
                javafx.scene.image.Image img = null;
                if (w.getImageUrl() != null && !w.getImageUrl().isBlank()) {
                    try {
                        img = new javafx.scene.image.Image(w.getImageUrl(), false);
                        if (img.isError()) img = null;
                    } catch (Exception inner) {
                        img = null;
                    }
                }
                if (productImage != null) {
                    if (img != null) {
                        javafx.scene.image.Image resized = new javafx.scene.image.Image(img.getUrl(), 300, 240, true, true, true);
                        productImage.setImage(resized);
                    } else {
                        try {
                            productImage.setImage(new javafx.scene.image.Image(getClass().getResourceAsStream("/images/placeholder.png")));
                        } catch (Exception ex2) {
                            productImage.setImage(null);
                        }
                    }
                }

                javafx.scene.image.Image rimg = null;
                if (w.getReceiptImageUrl() != null && !w.getReceiptImageUrl().isBlank()) {
                    try {
                        rimg = new javafx.scene.image.Image(w.getReceiptImageUrl(), false);
                        if (rimg.isError()) rimg = null;
                    } catch (Exception inner) { rimg = null; }
                }
                if (receiptImage != null) {
                    if (rimg != null) {
                        javafx.scene.image.Image rres = new javafx.scene.image.Image(rimg.getUrl(), 300, 240, true, true, true);
                        receiptImage.setImage(rres);
                    } else {
                        try { receiptImage.setImage(new javafx.scene.image.Image(getClass().getResourceAsStream("/images/receipt-placeholder.png"))); } catch (Exception ex3) { receiptImage.setImage(null); }
                    }
                }
            } catch (Exception ex) {
                if (productImage != null) productImage.setImage(null);
                if (receiptImage != null) receiptImage.setImage(null);
            }

            try {
                java.time.LocalDate start = w.getPurchaseDate();
                java.time.LocalDate end = w.getExpiryDate();
                long totalDays = java.time.temporal.ChronoUnit.DAYS.between(start, end);
                long elapsed = java.time.temporal.ChronoUnit.DAYS.between(start, java.time.LocalDate.now());
                double pct = 0.0;
                if (totalDays > 0) pct = Math.max(0.0, Math.min(1.0, (double) elapsed / (double) totalDays));
                if (warrantyProgress != null) {
                    warrantyProgress.setProgress(pct);
                }
                if (lblPercent != null) {
                    lblPercent.setText(String.format("%.0f%% used", pct * 100.0));
                }
            } catch (Exception ex) {
                if (warrantyProgress != null) warrantyProgress.setProgress(0);
                if (lblPercent != null) lblPercent.setText("");
            }
        }
    }

    @FXML
    public void onGetRecommendations() {
        if (warranty == null) {
            Alert a = new Alert(Alert.AlertType.WARNING, "No warranty loaded.", ButtonType.OK);
            a.showAndWait();
            return;
        }
        if (recommender == null) {
            Alert a = new Alert(Alert.AlertType.ERROR, "Recommender unavailable.", ButtonType.OK);
            a.showAndWait();
            return;
        }
        java.util.List<String> recs = recommender.recommendByProductAndModel(warranty.getProductName(), warranty.getModelName());
        recommendationsList.getItems().clear();
        if (recs == null || recs.isEmpty()) {
            recommendationsList.getItems().add("No recommendations found for this product.");
            recommendationsList.setCellFactory(null);
            return;
        }
        recommendationsList.getItems().setAll(recs);
        recommendationsList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                String label = item;
                String url = null;
                if (item.contains(" — ")) {
                    String[] parts = item.split(" — ", 2);
                    label = parts[0]; url = parts[1];
                } else if (item.contains(" - ")) {
                    String[] parts = item.split(" - ", 2);
                    label = parts[0]; url = parts[1];
                } else {
                    int idx = item.indexOf("http");
                    if (idx >= 0) { label = item.substring(0, idx).trim(); url = item.substring(idx).trim(); }
                }
                final String renderedLabel = label + (url == null ? "" : " (link)");
                final String capturedUrl = url;
                Hyperlink h = new Hyperlink(renderedLabel);
                h.setOnAction(evt -> {
                    String finalUrl = (capturedUrl != null && !capturedUrl.isBlank())
                            ? capturedUrl
                            : ("https://www.google.com/search?q=" +
                               java.net.URLEncoder.encode(warranty.getProductName() + " " + warranty.getModelName(), java.nio.charset.StandardCharsets.UTF_8));
                    try {
                        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                            Desktop.getDesktop().browse(new URI(finalUrl));
                        } else {
                            String os = System.getProperty("os.name").toLowerCase();
                            if (os.contains("win")) {
                                Runtime.getRuntime().exec(new String[]{"rundll32", "url.dll,FileProtocolHandler", finalUrl});
                            } else {
                                Runtime.getRuntime().exec(new String[]{"xdg-open", finalUrl});
                            }
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        javafx.application.Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, "Unable to open link: " + ex.getMessage(), ButtonType.OK).showAndWait());
                    }
                });
                setGraphic(h);
                setText(null);
            }
        });
    }

    @FXML
    public void onClose() {
        Stage s = (Stage) lblProduct.getScene().getWindow();
        s.close();
    }

    @FXML
    public void onDelete() {
        if (warranty != null) {
            warrantyService.deleteWarranty(warranty.getId());
            Stage s = (Stage) lblProduct.getScene().getWindow();
            s.close();
        }
    }

    @FXML
    public void onCheckReceipt() {

        if (warranty != null && warranty.getReceiptImageUrl() != null && !warranty.getReceiptImageUrl().isBlank()) {
            try {
                javafx.scene.image.Image receiptImage = new javafx.scene.image.Image(warranty.getReceiptImageUrl());
                if (receiptImage.isError()) {
                    Alert a = new Alert(Alert.AlertType.ERROR, "Unable to load receipt image.", ButtonType.OK);
                    a.showAndWait();
                    return;
                }

                Stage receiptStage = new Stage();
                receiptStage.setTitle("Receipt Image");

                javafx.scene.image.ImageView imageView = new javafx.scene.image.ImageView(receiptImage);
                imageView.setPreserveRatio(true);
                imageView.setFitWidth(600);

                javafx.scene.layout.StackPane root = new javafx.scene.layout.StackPane(imageView);
                root.setStyle("-fx-background-color: #f0f0f0; -fx-padding: 10;");

                receiptStage.setScene(new javafx.scene.Scene(root, 620, 800));
                receiptStage.showAndWait();
            } catch (Exception ex) {
                ex.printStackTrace();
                Alert a = new Alert(Alert.AlertType.ERROR, "Error displaying receipt: " + ex.getMessage(), ButtonType.OK);
                a.showAndWait();
            }
        } else {
            Alert a = new Alert(Alert.AlertType.WARNING, "No receipt image available for this warranty.", ButtonType.OK);
            a.showAndWait();
        }
    }

    @FXML
    public void onViewProduct() {
        if (warranty != null && warranty.getImageUrl() != null && !warranty.getImageUrl().isBlank()) {
            String src = warranty.getImageUrl();
            String normalized = src;
            try {

                if (!normalized.startsWith("file:") && !normalized.startsWith("http://") && !normalized.startsWith("https://")) {
                    Path p = Paths.get(normalized);
                    if (Files.exists(p)) normalized = p.toUri().toString();
                }
            } catch (Exception ex) {

            }
            try {
                javafx.scene.image.Image img = new javafx.scene.image.Image(normalized);
                if (img.isError()) {

                    tryOpenExternal(normalized);
                    return;
                }
                Stage st = new Stage(); st.setTitle("Product Image");
                javafx.scene.image.ImageView iv = new javafx.scene.image.ImageView(img); iv.setPreserveRatio(true); iv.setFitWidth(800);
                javafx.scene.layout.StackPane root = new javafx.scene.layout.StackPane(iv); root.setStyle("-fx-background-color:#fff; -fx-padding:10;");
                st.setScene(new javafx.scene.Scene(root, 820, 900)); st.showAndWait();
            } catch (Exception e) {
                tryOpenExternal(normalized);
            }
        } else { new Alert(Alert.AlertType.WARNING, "No product image available.", ButtonType.OK).showAndWait(); }
    }

    @FXML
    public void onViewReceipt() {
        System.out.println("=== ON VIEW RECEIPT ===");
        System.out.println("Warranty: " + (warranty != null ? warranty.getProductName() : "null"));
        System.out.println("Receipt URL: " + (warranty != null ? warranty.getReceiptImageUrl() : "null"));
        System.out.println("==================");
        
        if (warranty != null && warranty.getReceiptImageUrl() != null && !warranty.getReceiptImageUrl().isBlank()) {
            String src = warranty.getReceiptImageUrl();

            if (src.toLowerCase().endsWith(".pdf")) {
                System.out.println("PDF detected, opening with external viewer");
                tryOpenExternal(src);
                return;
            }

            String normalized = src;
            try {
                if (!normalized.startsWith("file:") && !normalized.startsWith("http://") && !normalized.startsWith("https://")) {
                    Path p = Paths.get(normalized);
                    if (Files.exists(p)) normalized = p.toUri().toString();
                }
            } catch (Exception ex) {

            }
            try {
                javafx.scene.image.Image img = new javafx.scene.image.Image(normalized);
                if (img.isError()) {

                    System.out.println("Image error, trying external viewer");
                    tryOpenExternal(normalized);
                    return;
                }
                Stage st = new Stage(); st.setTitle("Receipt Image");
                javafx.scene.image.ImageView iv = new javafx.scene.image.ImageView(img); iv.setPreserveRatio(true); iv.setFitWidth(800);
                javafx.scene.layout.StackPane root = new javafx.scene.layout.StackPane(iv); root.setStyle("-fx-background-color:#fff; -fx-padding:10;");
                st.setScene(new javafx.scene.Scene(root, 820, 900)); st.showAndWait();
            } catch (Exception e) {
                System.err.println("Error viewing receipt: " + e.getMessage());
                e.printStackTrace();
                tryOpenExternal(normalized);
            }
        } else { 
            System.out.println("No receipt available - showing warning");
            new Alert(Alert.AlertType.WARNING, "No receipt image available.", ButtonType.OK).showAndWait(); 
        }
    }

    @FXML
    public void onShowOrAttachReceipt() {
        if (warranty == null) {
            new Alert(javafx.scene.control.Alert.AlertType.WARNING, "No warranty selected.", javafx.scene.control.ButtonType.OK).showAndWait();
            return;
        }
        String receipt = warranty.getReceiptImageUrl();
        if (receipt == null || receipt.isBlank()) {

            FileChooser fc = new FileChooser();
            fc.setTitle("Attach Receipt Image");
            fc.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif"),
                    new FileChooser.ExtensionFilter("All Files", "*")
            );
            File sel = fc.showOpenDialog(lblProduct.getScene() == null ? null : (Stage) lblProduct.getScene().getWindow());
            if (sel != null && sel.exists()) {
                try {
                    java.nio.file.Path destDir = java.nio.file.Paths.get("data", "receipts");
                    java.nio.file.Files.createDirectories(destDir);
                    String name = sel.getName(); int dot = name.lastIndexOf('.'); String ext = dot>=0?name.substring(dot):"";
                    java.nio.file.Path dest = destDir.resolve(java.util.UUID.randomUUID().toString() + ext);
                    java.nio.file.Files.copy(sel.toPath(), dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    String uri = dest.toUri().toString();
                    warranty.setReceiptImageUrl(uri);

                    if (warrantyService != null) {
                        warrantyService.addWarranty(warranty);
                    }

                    onViewReceipt();
                } catch (Exception ex) {
                    ex.printStackTrace();
                    new Alert(javafx.scene.control.Alert.AlertType.ERROR, "Unable to attach receipt: " + ex.getMessage(), javafx.scene.control.ButtonType.OK).showAndWait();
                }
            }
        } else {
            onViewReceipt();
        }
    }

    private void tryOpenExternal(String uriOrPath) {
        try {
            System.out.println("tryOpenExternal called with: " + uriOrPath);

            URI u = null;
            File f = null;
            
            if (uriOrPath.startsWith("file:")) {

                String fixedUri = uriOrPath;
                if (uriOrPath.startsWith("file:/") && !uriOrPath.startsWith("file:///")) {

                    fixedUri = "file:///" + uriOrPath.substring(6);
                }
                System.out.println("Fixed URI: " + fixedUri);
                u = new URI(fixedUri);
                f = new File(u);
            } else if (uriOrPath.startsWith("http://") || uriOrPath.startsWith("https://")) {
                u = new URI(uriOrPath);
            } else {

                Path p = Paths.get(uriOrPath);
                if (Files.exists(p)) {
                    f = p.toFile();
                }
            }
            
            System.out.println("File object: " + (f != null ? f.getAbsolutePath() : "null"));
            System.out.println("File exists: " + (f != null && f.exists()));
            
            if (f != null && f.exists()) {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    Desktop.getDesktop().open(f);
                    System.out.println("Opened file with Desktop.open()");
                    return;
                } else {

                    String os = System.getProperty("os.name").toLowerCase();
                    if (os.contains("win")) {
                        Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", "", f.getAbsolutePath()});
                        System.out.println("Opened file with Windows start command");
                        return;
                    }
                }
            }
            
            final String errorPath = (f != null ? f.getAbsolutePath() : uriOrPath);
            javafx.application.Platform.runLater(() -> {
                Alert a = new Alert(Alert.AlertType.ERROR, "Unable to display the image. Path/URI: " + errorPath, ButtonType.OK);
                a.showAndWait();
            });
        } catch (URISyntaxException | IOException ex) {
            ex.printStackTrace();
            javafx.application.Platform.runLater(() -> {
                Alert a = new Alert(Alert.AlertType.ERROR, "Error opening image: " + ex.getMessage(), ButtonType.OK);
                a.showAndWait();
            });
        }
    }
}
