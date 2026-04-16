package org.example.ui;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Region;
import javafx.scene.control.Label;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import java.io.StringWriter;
import java.io.PrintWriter;
import org.example.model.Warranty;
import org.example.recommender.SimpleRecommender;
import org.example.service.WarrantyService;
import org.springframework.stereotype.Component;

import java.util.List;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.stage.FileChooser;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import javafx.util.Duration;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.rendering.PDFRenderer;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import java.io.FileInputStream;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Base64;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.util.Properties;
import javafx.geometry.Pos;

@Component
public class MainController {
    @FXML private TableView<Warranty> table;
    @FXML private javafx.scene.control.ListView<Warranty> cardList;
    @FXML private javafx.scene.layout.FlowPane cardGrid;
    @FXML private StackPane dashboardPane;
    @FXML private StackPane remindersPane;
    @FXML private TextField searchField;
    @FXML private ListView<String> remindersList;
    @FXML private ListView<Warranty> productList;
    @FXML private Button btnDashboard;
    @FXML private Button btnReminders;
    @FXML private TableColumn<Warranty, String> colProduct;
    @FXML private TableColumn<Warranty, String> colModel;
    @FXML private TableColumn<Warranty, String> colRetailer;
    @FXML private TableColumn<Warranty, String> colPurchase;
    @FXML private TableColumn<Warranty, String> colExpiry;
    @FXML private TableColumn<Warranty, String> colDaysLeft;
    @FXML private TableColumn<Warranty, Void> colActions;

    private final WarrantyService service;
    private final SimpleRecommender recommender;

    public MainController(WarrantyService service, SimpleRecommender recommender) {
        this.service = service;
        this.recommender = recommender;
    }

    private void styleDialog(DialogPane pane) {
        pane.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        String theme = java.util.prefs.Preferences.userNodeForPackage(SettingsController.class).get("theme", "Light");
        if ("Dark".equalsIgnoreCase(theme)) {
            pane.getStylesheets().add(getClass().getResource("/styles/dark.css").toExternalForm());
        }

        pane.getButtonTypes().forEach(bt -> {
            javafx.scene.Node btn = pane.lookupButton(bt);
            if (btn != null) {
                ButtonBar.ButtonData data = bt.getButtonData();
                if (data == ButtonBar.ButtonData.OK_DONE || data == ButtonBar.ButtonData.YES || 
                    data == ButtonBar.ButtonData.APPLY || data == ButtonBar.ButtonData.FINISH) {
                    btn.getStyleClass().add("primary-btn");
                } else if (data == ButtonBar.ButtonData.CANCEL_CLOSE || data == ButtonBar.ButtonData.NO) {
                    btn.getStyleClass().add("outline-btn");
                }
            }
        });
    }

    @FXML
    public void initialize() {

        refreshTable();

        if (productList != null) {

            try { productList.getStyleClass().add("nav-list"); } catch (Exception ignore) {}
            productList.setFocusTraversable(false);
            productList.setCellFactory(lv -> new ListCell<>() {
                @Override
                protected void updateItem(Warranty item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                    } else {
                        String text = item.getProductName();
                        if (item.getModelName() != null && !item.getModelName().isBlank()) {
                            text += " " + item.getModelName();
                        }
                        setText(text);
                        getStyleClass().add("nav-cell");
                    }
                }
            });
            productList.setOnMouseClicked(evt -> {
                if (evt.getClickCount() == 2) {
                    Warranty sel = productList.getSelectionModel().getSelectedItem();
                    if (sel != null) openDetail(sel);
                }
            });
        }

        Timeline t = new Timeline(new KeyFrame(Duration.seconds(0), e -> refreshReminders()),
            new KeyFrame(Duration.seconds(15)));
        t.setCycleCount(Timeline.INDEFINITE);
        t.play();

        Timeline tableRefresher = new Timeline(new KeyFrame(Duration.seconds(0), e -> refreshTable()),
            new KeyFrame(Duration.seconds(60)));
        tableRefresher.setCycleCount(Timeline.INDEFINITE);
        tableRefresher.play();
    }

    private void openDetail(Warranty w) {
        try {

            Warranty freshWarranty = w;
            if (w.getId() != null) {
                freshWarranty = service.findById(w.getId()).orElse(w);
                System.out.println("=== OPENING DETAIL VIEW ===");
                System.out.println("Warranty ID: " + freshWarranty.getId());
                System.out.println("Product: " + freshWarranty.getProductName());
                System.out.println("Receipt URL: " + freshWarranty.getReceiptImageUrl());
                System.out.println("==================");
            }
            
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(getClass().getResource("/fxml/detail.fxml"));
            Parent root = loader.load();
            DetailController ctrl = loader.getController();
            if (ctrl == null) {
                Alert a = new Alert(Alert.AlertType.ERROR, "Unable to load detail controller.");
                a.showAndWait();
                return;
            }

            ctrl.setWarrantyService(service);

            try {
                ctrl.getClass().getMethod("setRecommender", org.example.recommender.SimpleRecommender.class)
                    .invoke(ctrl, recommender);
            } catch (Exception ignore) {}
            ctrl.setWarranty(freshWarranty);
            Stage s = new Stage();
            s.setTitle("Warranty Details");
            Scene scene = new Scene(root);

            scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
            String theme = java.util.prefs.Preferences.userNodeForPackage(SettingsController.class).get("theme", "Light");
            if ("Dark".equalsIgnoreCase(theme)) {
                scene.getStylesheets().add(getClass().getResource("/styles/dark.css").toExternalForm());
            }
            s.setScene(scene);

            if (cardGrid != null && cardGrid.getScene() != null && cardGrid.getScene().getWindow() != null) {
                s.initOwner(cardGrid.getScene().getWindow());
            }

            try { s.sizeToScene(); } catch (Throwable ignore) {}
            s.setMinWidth(800);
            s.setMinHeight(500);
            s.setWidth(940);
            s.setHeight(600);
            try { s.centerOnScreen(); } catch (Throwable ignore) {}
            s.showAndWait();

            refreshTable();
        } catch (Exception e) {
            e.printStackTrace();
            javafx.application.Platform.runLater(() -> {
                Alert a = new Alert(Alert.AlertType.ERROR, "Error opening detail view: " + e.getMessage(), ButtonType.OK);
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                TextArea ta = new TextArea(sw.toString());
                ta.setEditable(false);
                ta.setWrapText(true);
                ta.setMaxWidth(600);
                ta.setMaxHeight(400);
                a.getDialogPane().setExpandableContent(ta);
                a.showAndWait();
            });
        }
    }

    private void refreshReminders() {
        List<Warranty> all = service.listAll();
        List<String> soon = all.stream()
                .filter(w -> w.getDaysLeft() >= 0 && w.getDaysLeft() <= 30)
                .sorted((a,b) -> Long.compare(a.getDaysLeft(), b.getDaysLeft()))
                .map(w -> w.getProductName() + " " + w.getModelName() + " — expires in " + w.getDaysLeft() + " days")
                .toList();
        javafx.application.Platform.runLater(() -> {
            remindersList.getItems().setAll(soon);
        });
    }

    @FXML
    public void onShowDashboard() {
        dashboardPane.setVisible(true); dashboardPane.setManaged(true);
        remindersPane.setVisible(false); remindersPane.setManaged(false);
    setActiveNav(btnDashboard);
    }

    @FXML
    public void onShowReminders() {
        dashboardPane.setVisible(false); dashboardPane.setManaged(false);
    remindersPane.setVisible(true); remindersPane.setManaged(true);
    setActiveNav(btnReminders);
    }

    private void setActiveNav(Button active) {
        Button[] arr = new Button[]{btnDashboard, btnReminders};
        for (Button b : arr) {
            if (b == null) continue;
            b.getStyleClass().remove("active");
        }
        if (active != null) active.getStyleClass().add("active");
    }

    @FXML
    public void onSearch() {
        String q = searchField.getText();
        if (q == null || q.isBlank()) {
            refreshTable();
            return;
        }
        List<Warranty> all = service.listAll();
        List<Warranty> filtered = all.stream().filter(w ->
                w.getProductName().toLowerCase().contains(q.toLowerCase()) ||
                        (w.getModelName() != null && w.getModelName().toLowerCase().contains(q.toLowerCase()))
        ).toList();
        renderCardsInGrid(filtered);
    }

    @SuppressWarnings("unused")
    private String resolveTopGoogleResult(String query) {
        try {

            java.util.Properties p = new java.util.Properties();
            boolean loaded = false;
            try {
                java.nio.file.Path external = java.nio.file.Paths.get("data", "config", "image-fetch-config.properties");
                if (java.nio.file.Files.exists(external)) {
                    try (java.io.InputStream in = java.nio.file.Files.newInputStream(external)) { p.load(in); loaded = true; }
                }
            } catch (Exception ignore) {}
            if (!loaded) {
                try (java.io.InputStream in = getClass().getResourceAsStream("/image-fetch-config.properties")) {
                    if (in != null) p.load(in);
                } catch (Exception ignore) {}
            }

            String apiKey = p.getProperty("api.key");
            String cx = p.getProperty("search.engine.id");
            String endpoint = p.getProperty("api.endpoint", "https://www.googleapis.com/customsearch/v1");
            if (apiKey == null || apiKey.isBlank() || cx == null || cx.isBlank()) return null;

            java.net.URI uri = new java.net.URI(endpoint + "?q=" + java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8) +
                    "&key=" + java.net.URLEncoder.encode(apiKey, java.nio.charset.StandardCharsets.UTF_8) +
                    "&cx=" + java.net.URLEncoder.encode(cx, java.nio.charset.StandardCharsets.UTF_8) +
                    "&num=1");
            HttpRequest req = HttpRequest.newBuilder(uri).GET().build();
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return null;
            org.json.JSONObject json = new org.json.JSONObject(resp.body());
            if (!json.has("items") || json.getJSONArray("items").isEmpty()) return null;
            return json.getJSONArray("items").getJSONObject(0).optString("link", null);
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unused")
    private boolean isGoogleSearchUrl(String url) {
        try {
            java.net.URI uri = new java.net.URI(url);
            String host = uri.getHost();
            return host != null && host.contains("google.") && 
                   (uri.getPath() != null && uri.getPath().startsWith("/search"));
        } catch (Exception e) { return false; }
    }

    @SuppressWarnings("unused")
    private String extractGoogleQuery(String url) {
        try {
            java.net.URI uri = new java.net.URI(url);
            String query = uri.getQuery();
            if (query == null) return null;
            for (String part : query.split("&")) {
                int eq = part.indexOf('=');
                if (eq > 0) {
                    String key = part.substring(0, eq);
                    String val = part.substring(eq + 1);
                    if ("q".equals(key)) {
                        return java.net.URLDecoder.decode(val, java.nio.charset.StandardCharsets.UTF_8);
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    @SuppressWarnings("unused")
    private String resolveTopDuckDuckGoResult(String query) {
        try {
            String q = java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);
            java.net.URI uri = new java.net.URI("https://api.duckduckgo.com/?q=" + q + "&format=json&no_html=1&no_redirect=1");
            HttpRequest req = HttpRequest.newBuilder(uri).GET().header("User-Agent", "WarrantyTracker/1.0").build();
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return null;
            org.json.JSONObject json = new org.json.JSONObject(resp.body());
            String absUrl = json.optString("AbstractURL", null);
            if (absUrl != null && !absUrl.isBlank()) return absUrl;

            org.json.JSONArray rt = json.optJSONArray("RelatedTopics");
            if (rt != null) {
                for (int i = 0; i < rt.length(); i++) {
                    org.json.JSONObject item = rt.optJSONObject(i);
                    if (item == null) continue;

                    if (item.has("FirstURL")) {
                        String u = item.optString("FirstURL", null);
                        if (u != null && !u.isBlank()) return u;
                    }
                    org.json.JSONArray nested = item.optJSONArray("Topics");
                    if (nested != null) {
                        for (int j = 0; j < nested.length(); j++) {
                            org.json.JSONObject sub = nested.optJSONObject(j);
                            if (sub == null) continue;
                            String u2 = sub.optString("FirstURL", null);
                            if (u2 != null && !u2.isBlank()) return u2;
                        }
                    }
                }
            }
        } catch (Exception e) { return null; }
        return null;
    }

    @FXML
    public void onOpenSettings() {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/settings.fxml"));
            javafx.scene.Parent root = loader.load();
            javafx.stage.Stage s = new javafx.stage.Stage();
            s.setTitle("Settings");
            javafx.scene.Scene scene = new javafx.scene.Scene(root);

            scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
            String theme = java.util.prefs.Preferences.userNodeForPackage(SettingsController.class).get("theme", "Light");
            if ("Dark".equalsIgnoreCase(theme)) {
                scene.getStylesheets().add(getClass().getResource("/styles/dark.css").toExternalForm());
            }
            s.setScene(scene);
            s.initOwner(cardGrid.getScene().getWindow());

            try { s.centerOnScreen(); } catch (Throwable ignore) {}
            s.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
            Alert a = new Alert(Alert.AlertType.ERROR, "Unable to open settings: " + e.getMessage(), ButtonType.OK);
            a.showAndWait();
        }
    }

    private void refreshTable() {
        List<Warranty> list = service.listAllWithImageFetch();
        if (cardGrid != null) {
            renderCardsInGrid(list);
        }

        if (cardList != null) {
            cardList.setItems(FXCollections.observableArrayList(list));
        }
        if (productList != null) {
            productList.setItems(FXCollections.observableArrayList(list));
        }
    }

    private void renderCardsInGrid(List<Warranty> warranties) {
        cardGrid.getChildren().clear();
        for (Warranty w : warranties) {
            VBox card = new VBox(10);
            card.getStyleClass().add("warranty-card");
            card.setAlignment(Pos.TOP_LEFT);

            javafx.scene.layout.StackPane media = new javafx.scene.layout.StackPane();
            media.setPrefSize(220, 140);
            media.setStyle("-fx-background-color: rgba(0,0,0,0.25); -fx-background-radius: 12; -fx-border-radius: 12;");
            javafx.scene.image.ImageView iv = new javafx.scene.image.ImageView();
            iv.setPreserveRatio(true); iv.setFitWidth(220); iv.setFitHeight(140);
            String imgUrl = w.getImageUrl();
            try {
                if (imgUrl != null && !imgUrl.isBlank()) {
                    javafx.scene.image.Image img = new javafx.scene.image.Image(imgUrl, 220, 140, true, true, true);
                    if (!img.isError()) iv.setImage(img);
                }
            } catch (Exception ignore) {}
            if (iv.getImage() == null) {
                Label ph = new Label("🖼"); ph.setStyle("-fx-text-fill: rgba(255,255,255,0.5); -fx-font-size: 42px;");
                media.getChildren().add(ph);
            } else {
                media.getChildren().add(iv);
            }

            Label title = new Label(w.getProductName());
            title.getStyleClass().add("card-title");
            Label brand = new Label(w.getRetailer() == null ? "" : w.getRetailer());
            brand.getStyleClass().add("muted-label");

            HBox statusRow = new HBox(8);
            javafx.scene.control.Label badge = new javafx.scene.control.Label();
            long daysLeft = w.getDaysLeft();
            if (daysLeft < 0) { badge.setText("Expired"); badge.getStyleClass().addAll("status-badge", "expired"); }
            else { badge.setText(daysLeft + " days left"); badge.getStyleClass().addAll("status-badge", "active"); }
            statusRow.getChildren().add(badge);

            HBox usageRow = new HBox(8); usageRow.setAlignment(Pos.CENTER_LEFT);
            Label usageLeft = new Label(daysLeft < 0 ? "Expired" : "Active"); usageLeft.getStyleClass().add("muted-label");
            Label usageRight = new Label(); usageRight.getStyleClass().add("muted-label");
            try {
                java.time.LocalDate start = w.getPurchaseDate();
                java.time.LocalDate end = w.getExpiryDate();
                long totalDays = java.time.temporal.ChronoUnit.DAYS.between(start, end);
                long elapsed = java.time.temporal.ChronoUnit.DAYS.between(start, java.time.LocalDate.now());
                double pct = totalDays > 0 ? Math.max(0.0, Math.min(1.0, (double) elapsed / (double) totalDays)) : 0.0;
                usageRight.setText(String.format("%.0f%%", pct * 100.0));
                javafx.scene.control.ProgressBar pb = new javafx.scene.control.ProgressBar(pct);
                pb.setPrefWidth(300);
                if (daysLeft < 0) pb.getStyleClass().add("expired-progress");
                VBox progressBox = new VBox(6, usageRow, pb);

                HBox dates = new HBox(8);
                Label startL = new Label(start == null ? "" : start.toString()); startL.getStyleClass().add("muted-label");
                Region spacer = new Region(); HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
                Label endL = new Label(end == null ? "" : end.toString()); endL.getStyleClass().add("muted-label");
                dates.getChildren().addAll(startL, spacer, endL);

                usageRow.getChildren().addAll(usageLeft, new Region());
                card.getChildren().addAll(media, title, brand, statusRow, progressBox, dates);
            } catch (Exception ex) {

                card.getChildren().addAll(media, title, brand, statusRow);
            }

            card.setOnMouseClicked(evt -> openDetail(w));
            cardGrid.getChildren().add(card);
        }
    }

    @FXML
    public void onAddProduct() {

        Alert choice = new Alert(Alert.AlertType.NONE);
        choice.setTitle("Add Product");
        choice.setHeaderText("How would you like to add the product?");
        ButtonType uploadBtn = new ButtonType("Upload Image", ButtonBar.ButtonData.LEFT);
        ButtonType manualBtn = new ButtonType("Enter Manually", ButtonBar.ButtonData.RIGHT);
        ButtonType cancelBtn = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        choice.getButtonTypes().setAll(uploadBtn, manualBtn, cancelBtn);

        DialogPane pane = choice.getDialogPane();
        styleDialog(pane);
        pane.lookupButton(uploadBtn).getStyleClass().add("primary-btn");
        pane.lookupButton(manualBtn).getStyleClass().add("primary-btn");
        pane.lookupButton(cancelBtn).getStyleClass().add("outline-btn");
        
        java.util.Optional<ButtonType> picked = choice.showAndWait();
        if (picked.isEmpty() || picked.get() == cancelBtn) return;
        if (picked.get() == manualBtn) {

            showManualAddDialog();
        } else if (picked.get() == uploadBtn) {
            handleUploadImage();
        }
}

    private void showManualAddDialog() {
        Dialog<Warranty> dlg = new Dialog<>();
        dlg.setTitle("Add Product (Manual)");

        Label nameL = new Label("Product:");
        TextField nameF = new TextField();
        Label modelL = new Label("Model Name:");
        TextField modelF = new TextField();
        Label retailerL = new Label("Retailer:");
        TextField retailerF = new TextField();
        Label dateL = new Label("Purchase Date (YYYY-MM-DD):");
        TextField dateF = new TextField();
        Label monthsL = new Label("Warranty Months:");
        TextField monthsF = new TextField();
        Label receiptL = new Label("Upload Receipt:");
        TextField receiptF = new TextField();
        javafx.scene.control.Button uploadReceiptBtn = new javafx.scene.control.Button("Upload...");
        uploadReceiptBtn.setOnAction(evt -> {
            try {
                FileChooser fc = new FileChooser();
                fc.setTitle("Upload Receipt Image or Document");
                fc.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif"),
                    new FileChooser.ExtensionFilter("PDF Files", "*.pdf"),
                    new FileChooser.ExtensionFilter("Word Documents", "*.docx", "*.doc"),
                    new FileChooser.ExtensionFilter("All Files", "*")
                );
                File sel = fc.showOpenDialog(cardGrid.getScene() == null ? null : cardGrid.getScene().getWindow());
                if (sel != null) {
                    Path destDir = Paths.get("data", "receipts");
                    Files.createDirectories(destDir);
                    String ext = "";
                    String name = sel.getName();
                    int dot = name.lastIndexOf('.');
                    if (dot >= 0) ext = name.substring(dot);
                    Path dest = destDir.resolve(UUID.randomUUID().toString() + ext);
                    Files.copy(sel.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);
                    receiptF.setText(dest.toUri().toString());
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                javafx.application.Platform.runLater(() -> {
                    Alert a = new Alert(Alert.AlertType.ERROR, "Unable to copy receipt: " + ex.getMessage(), ButtonType.OK);
                    styleDialog(a.getDialogPane());
                    a.showAndWait();
                });
            }
        });

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(nameL, 0, 0); grid.add(nameF, 1, 0);
        grid.add(modelL, 0, 1); grid.add(modelF, 1, 1);
        grid.add(retailerL, 0, 2); grid.add(retailerF, 1, 2);
        grid.add(dateL, 0, 3); grid.add(dateF, 1, 3);
        grid.add(monthsL, 0, 4); grid.add(monthsF, 1, 4);
        grid.add(receiptL, 0, 5); grid.add(receiptF, 1, 5); grid.add(uploadReceiptBtn, 2, 5);

        dlg.getDialogPane().setContent(grid);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        styleDialog(dlg.getDialogPane());
        dlg.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    java.time.LocalDate pd = java.time.LocalDate.parse(dateF.getText());
                    int months = Integer.parseInt(monthsF.getText());
                    Warranty w = new Warranty(nameF.getText(), modelF.getText(), retailerF.getText(), pd, months);
                    String receipt = receiptF.getText();
                    if (receipt != null && !receipt.isBlank()) {
                        String trimmed = receipt.trim();
                        w.setReceiptImageUrl(trimmed);

                    }
                    return w;
                } catch (Exception e) {
                    Alert a = new Alert(Alert.AlertType.ERROR, "Invalid input: " + e.getMessage(), ButtonType.OK);
                    styleDialog(a.getDialogPane());
                    a.showAndWait();
                }
            }
            return null;
        });

        dlg.showAndWait().ifPresent(w -> {

            Warranty saved = service.addWarranty(w);

            System.out.println("=== SAVED WARRANTY (MANUAL) ===");
            System.out.println("ID: " + saved.getId());
            System.out.println("Product: " + saved.getProductName());
            System.out.println("Receipt URL: " + saved.getReceiptImageUrl());
            System.out.println("==================");
            
            try {

                Warranty updated = service.ensureProductImage(saved, false);

                if (updated != null && saved.getReceiptImageUrl() != null) {
                    updated.setReceiptImageUrl(saved.getReceiptImageUrl());
                    saved = service.addWarranty(updated);
                } else if (updated != null) {
                    saved = updated;
                }

                String rec = saved.getReceiptImageUrl();
                if ((saved.getImageUrl() == null || saved.getImageUrl().isBlank()) && rec != null && rec.toLowerCase().endsWith(".pdf")) {
                    try {
                        File pdfFile = Paths.get(new java.net.URI(rec)).toFile();
                        String thumbUri = generatePdfThumbnail(pdfFile);
                        if (thumbUri != null) {
                            saved.setImageUrl(thumbUri);
                            service.addWarranty(saved);
                        }
                    } catch (Exception ex) {
                        System.err.println("Failed to generate PDF thumbnail fallback: " + ex.getMessage());
                    }
                }
            } catch (Exception ignore) {
                ignore.printStackTrace();
            }

            org.example.MainApp.sendScreenNotification(
                "Warranty Added", 
                saved.getProductName() + " has been added to your tracker",
                org.example.ui.WindowsNotifier.NotificationType.SUCCESS
            );
            
            refreshTable();
        });
    }

    private void handleUploadImage() {
        try {
            FileChooser fc = new FileChooser();
            fc.setTitle("Upload Receipt / Product Document");
            fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif"),
                new FileChooser.ExtensionFilter("PDF Files", "*.pdf"),
                new FileChooser.ExtensionFilter("Word Documents", "*.docx", "*.doc"),
                new FileChooser.ExtensionFilter("All Files", "*")
            );
            File sel = fc.showOpenDialog(cardGrid.getScene() == null ? null : cardGrid.getScene().getWindow());
            if (sel == null) return;
            Path destDir = Paths.get("data", "receipts");
            Files.createDirectories(destDir);
            String ext = "";
            String name = sel.getName();
            int dot = name.lastIndexOf('.');
            if (dot >= 0) ext = name.substring(dot);
            Path dest = destDir.resolve(UUID.randomUUID().toString() + ext);
            Files.copy(sel.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);

            showImageReviewDialog(dest.toFile());
        } catch (Exception ex) {
            ex.printStackTrace();
            javafx.application.Platform.runLater(() -> {
                Alert a = new Alert(Alert.AlertType.ERROR, "Unable to copy document: " + ex.getMessage(), ButtonType.OK);
                a.showAndWait();
            });
        }
    }

    private void showImageReviewDialog(File imageFile) {
        try {
            Dialog<Warranty> dlg = new Dialog<>();
            dlg.setTitle("Review Extracted Data");

            Label nameL = new Label("Product:");
            TextField nameF = new TextField();
            Label modelL = new Label("Model Name:");
            TextField modelF = new TextField();
            Label retailerL = new Label("Retailer:");
            TextField retailerF = new TextField();
            Label dateL = new Label("Purchase Date (YYYY-MM-DD):");
            TextField dateF = new TextField();
            Label monthsL = new Label("Warranty Months:");
            TextField monthsF = new TextField();

            String filename = imageFile.getName().toLowerCase();
            String extractedText = null;
            boolean isPdf = filename.endsWith(".pdf");
            boolean isWord = filename.endsWith(".docx") || filename.endsWith(".doc");
            
            if (isPdf) {
                extractedText = extractTextFromPdf(imageFile);
            } else if (isWord) {
                extractedText = extractTextFromWord(imageFile);
            } else {

                String gvKey = getGoogleVisionApiKey();
                if (gvKey != null && !gvKey.isBlank()) {
                    extractedText = extractTextFromImageWithGoogleVision(imageFile, gvKey);
                    if (extractedText == null || extractedText.isBlank()) {
                        extractedText = extractTextFromImage(imageFile);
                    }
                } else {
                    extractedText = extractTextFromImage(imageFile);
                }
            }

            System.out.println("=== EXTRACTED TEXT FROM IMAGE ===");
            System.out.println(extractedText != null ? extractedText : "NULL");
            System.out.println("=== END EXTRACTED TEXT ===");

            if (extractedText != null && !extractedText.isBlank()) {
                parseWarrantyData(extractedText, nameF, modelF, retailerF, dateF, monthsF);
            } else {

                String base = imageFile.getName();
                int dot = base.lastIndexOf('.');
                if (dot > 0) base = base.substring(0, dot);
                String[] parts = base.split("[_\\- ]");
                if (parts.length >= 1) nameF.setText(parts[0]);
                if (parts.length >= 2) modelF.setText(parts[1]);
                monthsF.setText("12");
            }

            VBox previewBox = new VBox(5);
            if (isPdf || isWord) {

                Label docIcon = new Label("📄");
                docIcon.setStyle("-fx-font-size: 48px;");
                Label docName = new Label(imageFile.getName());
                docName.setWrapText(true);
                docName.setMaxWidth(300);
                TextArea textPreview = new TextArea(extractedText != null ? extractedText : "No text extracted");
                textPreview.setEditable(false);
                textPreview.setPrefRowCount(20);
                textPreview.setPrefWidth(300);
                textPreview.setWrapText(true);
                previewBox.getChildren().addAll(docIcon, docName, new Label("Extracted Text:"), textPreview);
            } else {

                javafx.scene.image.ImageView iv = new javafx.scene.image.ImageView();
                try {
                    javafx.scene.image.Image img = new javafx.scene.image.Image(imageFile.toURI().toString(), 260, 380, true, true, true);
                    iv.setImage(img);
                    iv.setFitWidth(260);
                    iv.setFitHeight(380);
                    iv.setPreserveRatio(true);
                    previewBox.getChildren().add(iv);
                } catch (Exception e) {
                    Label placeholder = new Label("Image preview unavailable");
                    previewBox.getChildren().add(placeholder);
                }
            }

            final String extractedForEnrich = extractedText;

            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            grid.add(previewBox, 0, 0, 1, 6);
            grid.add(nameL, 1, 0); grid.add(nameF, 2, 0);
            grid.add(modelL, 1, 1); grid.add(modelF, 2, 1);
            grid.add(retailerL, 1, 2); grid.add(retailerF, 2, 2);
            grid.add(dateL, 1, 3); grid.add(dateF, 2, 3);
            grid.add(monthsL, 1, 4); grid.add(monthsF, 2, 4);

            boolean needsEnrich =
                    (nameF.getText() == null || nameF.getText().isBlank()) ||
                    (modelF.getText() == null || modelF.getText().isBlank()) ||
                    (retailerF.getText() == null || retailerF.getText().isBlank()) ||
                    (dateF.getText() == null || dateF.getText().isBlank());
            if (needsEnrich) {
                String baseQuery = nameF.getText();
                if (baseQuery == null || baseQuery.isBlank()) {
                    String base = imageFile.getName();
                    int ix = base.lastIndexOf('.');
                    if (ix > 0) base = base.substring(0, ix);
                    baseQuery = base.replace('_', ' ').replace('-', ' ');
                }
                autoFillOnline(baseQuery, retailerF, nameF, modelF, dateF, imageFile, extractedForEnrich);
            }

            dlg.getDialogPane().setContent(grid);
            dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            dlg.setResultConverter(btn -> {
                if (btn == ButtonType.OK) {
                    try {
                        java.time.LocalDate pd = java.time.LocalDate.parse(dateF.getText());
                        int months = Integer.parseInt(monthsF.getText());
                        Warranty w = new Warranty(nameF.getText(), modelF.getText(), retailerF.getText(), pd, months);
                        String fileUri = imageFile.toURI().toString();
                        w.setReceiptImageUrl(fileUri);

                        return w;
                    } catch (Exception e) {
                        Alert a = new Alert(Alert.AlertType.ERROR, "Invalid input: " + e.getMessage(), ButtonType.OK);
                        a.showAndWait();
                    }
                }
                return null;
            });

            final boolean finalIsPdf = isPdf;
            dlg.showAndWait().ifPresent(w -> {

                Warranty saved = service.addWarranty(w);

                System.out.println("=== SAVED WARRANTY ===");
                System.out.println("ID: " + saved.getId());
                System.out.println("Product: " + saved.getProductName());
                System.out.println("Receipt URL: " + saved.getReceiptImageUrl());
                System.out.println("==================");

                try {
                    Warranty updated = service.ensureProductImage(saved, false);

                    if (updated != null && saved.getReceiptImageUrl() != null) {
                        updated.setReceiptImageUrl(saved.getReceiptImageUrl());
                        saved = service.addWarranty(updated);
                    } else if (updated != null) {
                        saved = updated;
                    }

                    if ((saved.getImageUrl() == null || saved.getImageUrl().isBlank()) && finalIsPdf) {
                        try {
                            File pdfFile = Paths.get(new java.net.URI(saved.getReceiptImageUrl())).toFile();
                            String thumbUri = generatePdfThumbnail(pdfFile);
                            if (thumbUri != null) {
                                saved.setImageUrl(thumbUri);
                                service.addWarranty(saved);
                            }
                        } catch (Exception ex) {
                            System.err.println("Failed to generate PDF thumbnail fallback: " + ex.getMessage());
                        }
                    }
                } catch (Exception ex) {
                    System.err.println("Error fetching product image: " + ex.getMessage());
                    ex.printStackTrace();
                }

                org.example.MainApp.sendScreenNotification(
                    "Warranty Added", 
                    saved.getProductName() + " has been added to your tracker",
                    org.example.ui.WindowsNotifier.NotificationType.SUCCESS
                );
                
                refreshTable();
            });
        } catch (Exception e) {
            e.printStackTrace();
            Alert a = new Alert(Alert.AlertType.ERROR, "Unable to show review dialog: " + e.getMessage(), ButtonType.OK);
            a.showAndWait();
        }
    }

    private String extractTextFromPdf(File pdfFile) {
        try (PDDocument document = PDDocument.load(pdfFile)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setStartPage(1);
            stripper.setEndPage(document.getNumberOfPages());
            String text = stripper.getText(document);
            System.out.println("=== EXTRACTED PDF TEXT ===");
            System.out.println(text);
            System.out.println("=== END EXTRACTED TEXT ===");
            return text;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void autoFillOnline(String baseQuery,
                                TextField retailerF,
                                TextField nameF,
                                TextField modelF,
                                TextField dateF,
                                File sourceFile,
                                String extractedText) {

        new Thread(() -> {
            String canonical = null;
            try { canonical = wikipediaCanonicalTitle(baseQuery); } catch (Exception ignore) {}
            String modelCode = null;
            try { modelCode = findModelCodeWithDuckDuckGo(baseQuery); } catch (Exception ignore) {}

            String retailerGuess = null;
            try {
                String text = (extractedText == null ? "" : extractedText);
                String[] brands = {"Samsung","Apple","Dell","HP","Lenovo","Sony","LG","Amazon","Flipkart","Best Buy","Walmart","Target"};
                for (String b : brands) { if (text.contains(b) || (canonical != null && canonical.contains(b)) || baseQuery.contains(b)) { retailerGuess = b; break; } }
            } catch (Exception ignore) {}

            String fileDate = null;
            try {
                java.nio.file.attribute.BasicFileAttributes attrs = java.nio.file.Files.readAttributes(sourceFile.toPath(), java.nio.file.attribute.BasicFileAttributes.class);
                java.time.LocalDateTime ldt = java.time.LocalDateTime.ofInstant(attrs.lastModifiedTime().toInstant(), java.time.ZoneId.systemDefault());
                fileDate = ldt.toLocalDate().toString();
            } catch (Exception ignore) {}

            final String fCanonical = canonical;
            final String fModel = modelCode;
            final String fRetailer = retailerGuess;
            final String fDate = fileDate;
            javafx.application.Platform.runLater(() -> {
                if ((nameF.getText() == null || nameF.getText().isBlank()) && fCanonical != null) nameF.setText(fCanonical);
                else if (fCanonical != null && nameF.getText().length() < fCanonical.length() && fCanonical.toLowerCase().contains(nameF.getText().toLowerCase())) {

                    nameF.setText(fCanonical);
                }
                if ((modelF.getText() == null || modelF.getText().isBlank()) && fModel != null) modelF.setText(fModel);
                if ((retailerF.getText() == null || retailerF.getText().isBlank()) && fRetailer != null) retailerF.setText(fRetailer);
                if ((dateF.getText() == null || dateF.getText().isBlank()) && fDate != null) dateF.setText(fDate);
            });
        }).start();
    }

    private String wikipediaCanonicalTitle(String query) {
        try {
            String q = java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);
            URI searchUri = new URI("https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=" + q + "&format=json&srlimit=1");
            HttpRequest req = HttpRequest.newBuilder(searchUri).GET().header("User-Agent", "WarrantyTracker/1.0").build();
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return null;
            org.json.JSONObject json = new org.json.JSONObject(resp.body());
            if (!json.has("query")) return null;
            org.json.JSONArray arr = json.getJSONObject("query").optJSONArray("search");
            if (arr == null || arr.isEmpty()) return null;
            String title = arr.getJSONObject(0).optString("title", null);
            return title;
        } catch (Exception e) { return null; }
    }

    private String findModelCodeWithDuckDuckGo(String product) {
        try {
            String q = java.net.URLEncoder.encode(product + " model number", java.nio.charset.StandardCharsets.UTF_8);
            URI uri = new URI("https://api.duckduckgo.com/?q=" + q + "&format=json&no_redirect=1&no_html=1");
            HttpRequest req = HttpRequest.newBuilder(uri).GET().header("User-Agent", "WarrantyTracker/1.0").build();
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return null;
            org.json.JSONObject json = new org.json.JSONObject(resp.body());

            String abs = json.optString("AbstractText", json.optString("Abstract", ""));
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("SM-[A-Za-z0-9\\-/]{4,}").matcher(abs);
            if (m.find()) return m.group(0);

            org.json.JSONArray rt = json.optJSONArray("RelatedTopics");
            if (rt != null) {
                for (int i = 0; i < rt.length(); i++) {
                    org.json.JSONObject item = rt.optJSONObject(i);
                    if (item == null) continue;
                    String text = item.optString("Text", "");
                    java.util.regex.Matcher m2 = java.util.regex.Pattern.compile("SM-[A-Za-z0-9\\-/]{4,}").matcher(text);
                    if (m2.find()) return m2.group(0);
                }
            }
        } catch (Exception e) { return null; }
        return null;
    }

    private String extractTextFromImageWithGoogleVision(File imgFile, String apiKey) {
        try {
            byte[] bytes = java.nio.file.Files.readAllBytes(imgFile.toPath());
            String base64 = Base64.getEncoder().encodeToString(bytes);

            org.json.JSONObject image = new org.json.JSONObject();
            image.put("content", base64);

            org.json.JSONObject feature = new org.json.JSONObject();
            feature.put("type", "TEXT_DETECTION");

            org.json.JSONObject imageContext = new org.json.JSONObject();
            imageContext.put("languageHints", new org.json.JSONArray().put("en"));

            org.json.JSONObject requestObj = new org.json.JSONObject();
            requestObj.put("image", image);
            requestObj.put("features", new org.json.JSONArray().put(feature));
            requestObj.put("imageContext", imageContext);

            org.json.JSONObject payload = new org.json.JSONObject();
            payload.put("requests", new org.json.JSONArray().put(requestObj));

            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://vision.googleapis.com/v1/images:annotate?key=" + java.net.URLEncoder.encode(apiKey, StandardCharsets.UTF_8)))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                System.err.println("Google Vision OCR HTTP status: " + resp.statusCode());
                System.err.println("Google Vision API not enabled or invalid key. Falling back to OCR.space");
                return null;
            }
            org.json.JSONObject json = new org.json.JSONObject(resp.body());
            org.json.JSONArray responses = json.optJSONArray("responses");
            if (responses != null && responses.length() > 0) {
                org.json.JSONObject r = responses.getJSONObject(0);
                if (r.has("fullTextAnnotation")) {
                    String text = r.getJSONObject("fullTextAnnotation").optString("text", null);
                    if (text != null && !text.isBlank()) return text;
                }
                org.json.JSONArray ta = r.optJSONArray("textAnnotations");
                if (ta != null && ta.length() > 0) {
                    String text = ta.getJSONObject(0).optString("description", null);
                    if (text != null && !text.isBlank()) return text;
                }
            }
        } catch (Exception e) {
            System.err.println("Google Vision OCR error: " + e.getMessage());
        }
        return null;
    }

    private String extractTextFromImage(File imgFile) {
        try {
            String apiKey = getOcrApiKey();
            byte[] bytes = java.nio.file.Files.readAllBytes(imgFile.toPath());
            String mime = "image/jpeg";
            String name = imgFile.getName().toLowerCase();
            if (name.endsWith(".png")) mime = "image/png";
            else if (name.endsWith(".gif")) mime = "image/gif";
            String base64 = Base64.getEncoder().encodeToString(bytes);
            String dataUri = "data:" + mime + ";base64," + base64;

            String form = "apikey=" + java.net.URLEncoder.encode(apiKey, StandardCharsets.UTF_8) +
                          "&language=eng&isTable=true&scale=true&OCREngine=2&detectOrientation=true" +
                          "&base64Image=" + java.net.URLEncoder.encode(dataUri, StandardCharsets.UTF_8);

            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.ocr.space/parse/image"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return null;
            org.json.JSONObject json = new org.json.JSONObject(resp.body());
            if (json.has("ParsedResults") && json.getJSONArray("ParsedResults").length() > 0) {
                String text = json.getJSONArray("ParsedResults").getJSONObject(0).getString("ParsedText");
                return text;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private String getGoogleVisionApiKey() {

        try {
            java.nio.file.Path external = java.nio.file.Paths.get("data", "config", "image-fetch-config.properties");
            if (java.nio.file.Files.exists(external)) {
                try (java.io.InputStream in = java.nio.file.Files.newInputStream(external)) {
                    Properties p = new Properties();
                    p.load(in);
                    String key = p.getProperty("google.vision.api.key");
                    if (key != null && !key.isBlank()) return key.trim();
                }
            }
        } catch (Exception ignore) {}
        try (InputStream in = getClass().getResourceAsStream("/image-fetch-config.properties")) {
            if (in != null) {
                Properties p = new Properties();
                p.load(in);
                String key = p.getProperty("google.vision.api.key");
                if (key != null && !key.isBlank()) return key.trim();
            }
        } catch (Exception ignore) {}
        return null;
    }

    private String getOcrApiKey() {

        try {
            java.nio.file.Path external = java.nio.file.Paths.get("data", "config", "image-fetch-config.properties");
            if (java.nio.file.Files.exists(external)) {
                try (java.io.InputStream in = java.nio.file.Files.newInputStream(external)) {
                    Properties p = new Properties();
                    p.load(in);
                    String key = p.getProperty("ocr.api.key");
                    if (key != null && !key.isBlank()) return key.trim();
                }
            }
        } catch (Exception ignore) {}
        try (InputStream in = getClass().getResourceAsStream("/image-fetch-config.properties")) {
            if (in != null) {
                Properties p = new Properties(); p.load(in);
                String key = p.getProperty("ocr.api.key");
                if (key != null && !key.isBlank()) return key.trim();
            }
        } catch (Exception ignore) {}
        return "helloworld";
    }

    private String generatePdfThumbnail(File pdfFile) {
        PDDocument document = null;
        try {
            document = PDDocument.load(pdfFile);
            PDFRenderer renderer = new PDFRenderer(document);

            BufferedImage image = renderer.renderImageWithDPI(0, 150);
            Path imgDir = Paths.get("data", "images");
            Files.createDirectories(imgDir);
            Path out = imgDir.resolve(UUID.randomUUID().toString() + ".png");
            ImageIO.write(image, "png", out.toFile());
            return out.toUri().toString();
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        } finally {
            try { if (document != null) document.close(); } catch (Exception ignore) {}
        }
    }

    private String extractTextFromWord(File wordFile) {
        try (FileInputStream fis = new FileInputStream(wordFile);
             XWPFDocument document = new XWPFDocument(fis);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void parseWarrantyData(String text, TextField nameF, TextField modelF, 
                                   TextField retailerF, TextField dateF, TextField monthsF) {
        if (text == null || text.isBlank()) {
            monthsF.setText("12");
            return;
        }
        
        System.out.println("=== PARSING WARRANTY DATA ===");
        System.out.println("Raw text length: " + text.length());
        System.out.println("First 800 chars: " + text.substring(0, Math.min(800, text.length())));

        String[] knownBrands = {"Samsung", "Apple", "Dell", "HP", "Lenovo", "Sony", "LG", "Amazon", "Flipkart", "Best Buy", "Walmart", "Target", "OnePlus", "Xiaomi", "Oppo", "Vivo", "Realme", "Nokia", "Motorola"};
        for (String brand : knownBrands) {
            Pattern brandPattern = Pattern.compile("\\b" + Pattern.quote(brand) + "\\b", Pattern.CASE_INSENSITIVE);
            if (brandPattern.matcher(text).find()) {
                retailerF.setText(brand);
                System.out.println("Found retailer/brand: " + brand);
                break;
            }
        }

        Pattern[] galaxyPatterns = {
            Pattern.compile("Galaxy[\\s\\n]*S\\s*23", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Galaxy[\\s\\n]*S\\s*2\\s*3", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Galaxy[\\s\\n]*(S\\d{1,2})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Galaxy[\\s\\n]*(A\\d{1,2})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Galaxy[\\s\\n]*(M\\d{1,2})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Galaxy[\\s\\n]*(F\\d{1,2})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Galaxy[\\s\\n]*(Z\\s*(?:Fold|Flip)\\s*\\d*)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Galaxy[\\s\\n]*(Note\\s*\\d+)", Pattern.CASE_INSENSITIVE)
        };
        
    boolean foundGalaxy = false;
        for (Pattern galaxyPattern : galaxyPatterns) {
            Matcher galaxyMatcher = galaxyPattern.matcher(text);
            if (galaxyMatcher.find()) {
                String fullMatch = galaxyMatcher.group(0).trim().replaceAll("\\s+", " ");
                nameF.setText(fullMatch);
                System.out.println("Found Samsung Galaxy product: " + fullMatch);

                if (galaxyMatcher.groupCount() >= 1 && modelF.getText().isEmpty()) {
                    String galaxyModel = galaxyMatcher.group(1).trim().replaceAll("\\s+", "");
                    modelF.setText(galaxyModel);
                    System.out.println("Extracted Galaxy model: " + galaxyModel);
                }
                foundGalaxy = true;
                break;
            }
        }
        if (foundGalaxy) {
            System.out.println("Samsung Galaxy pattern detected.");
        }

        if (nameF.getText().isEmpty()) {
            Pattern productLinePattern = Pattern.compile("(?i)(iPhone|iPad|MacBook|iMac|AirPods|Apple Watch|OnePlus|Pixel|Redmi|Mi\\s*\\d+|ThinkPad|Inspiron|Pavilion|XPS|Surface)\\s*([A-Za-z0-9\\s]+)?", Pattern.CASE_INSENSITIVE);
            Matcher productLineMatcher = productLinePattern.matcher(text);
            if (productLineMatcher.find()) {
                String product = productLineMatcher.group(0).trim();
                if (product.length() > 50) {
                    product = product.substring(0, 50);
                }
                nameF.setText(product);
                System.out.println("Found product (line): " + product);
            }
        }

        if (nameF.getText().isEmpty()) {
            Pattern productPattern = Pattern.compile("(?i)(?:product|item|description)\\s*:?\\s*([A-Za-z][A-Za-z0-9\\s\\-,]+?)(?:\\n|\\r|\\||$)", Pattern.CASE_INSENSITIVE);
            Matcher productMatcher = productPattern.matcher(text);
            if (productMatcher.find()) {
                String product = productMatcher.group(1).trim();
                if (product.length() > 80) product = product.substring(0, 80);

                if (!product.matches("^[A-Z0-9]{6,}$")) {
                    nameF.setText(product);
                    System.out.println("Found product (label): " + product);
                }
            }
        }

        if (nameF.getText().isEmpty() && retailerF.getText().equalsIgnoreCase("Samsung")) {

            if (text.contains("QTY") || text.contains("PRICE") || text.contains("Mobile") || text.contains("Phone") || text.contains("SM-")) {
                nameF.setText("Samsung Mobile Phone");
                System.out.println("Set generic Samsung mobile product");
            }
        }

        Pattern[] modelPatterns = {

            Pattern.compile("\\b(SM-[A-Z]\\d{3}[A-Z0-9]{0,10})\\b", Pattern.CASE_INSENSITIVE),

            Pattern.compile("\\b([SAMFsamf]\\d{2,3}[A-Za-z]*)\\b"),

            Pattern.compile("(?i)(?:model|SKU|part\\s*(?:no|number|#))\\s*:?\\s*([A-Z0-9][A-Z0-9\\-]{2,20})", Pattern.CASE_INSENSITIVE),

            Pattern.compile("\\b([A-Z]{2,}[0-9]{3,}[A-Z0-9\\-]*)\\b"),

            Pattern.compile("(?i)(M[0-9]|A[0-9]{4})[A-Z0-9]*")
        };
        
        for (Pattern modelPattern : modelPatterns) {
            Matcher modelMatcher = modelPattern.matcher(text);
            if (modelMatcher.find()) {
                String model = modelMatcher.group(1).trim();

                if (model.length() >= 2 && model.length() <= 30 && !model.matches("[0-9a-f]{8,}")) {

                    if (model.toUpperCase().startsWith("SM-") || model.matches("^[SAMF]\\d{2}.*")) {
                        modelF.setText(model);
                        System.out.println("Found model: " + model);
                        break;
                    } else if (modelF.getText().isEmpty()) {
                        modelF.setText(model);
                        System.out.println("Found model: " + model);
                        break;
                    }
                }
            }
        }

        Pattern[] datePatterns = {
            Pattern.compile("(?i)(?:invoice\\s*date|purchase\\s*date|delivery|date|bought|ordered)\\s*:?\\s*(?:By\\s+)?(?:Mon|Tue|Wed|Thu|Fri|Sat|Sun)?[,\\s]*(\\w{3}[\\s,]+\\d{1,2}[\\s,]+\\d{4})"),
            Pattern.compile("(?i)(?:invoice\\s*date|purchase\\s*date|date|bought|ordered)\\s*:?\\s*(\\d{2}\\.\\d{2}\\.\\d{4})"),
            Pattern.compile("(?i)(?:invoice\\s*date|purchase\\s*date|date|bought|ordered)\\s*:?\\s*(\\d{4}[-/]\\d{2}[-/]\\d{2})"),
            Pattern.compile("(?i)(?:invoice\\s*date|purchase\\s*date|date|bought|ordered)\\s*:?\\s*(\\d{2}[-/]\\d{2}[-/]\\d{4})"),
            Pattern.compile("(\\d{2}\\.\\d{2}\\.\\d{4})"),
            Pattern.compile("(\\d{4}[-/]\\d{2}[-/]\\d{2})"),
            Pattern.compile("(\\d{2}[-/]\\d{2}[-/]\\d{4})")
        };
        
        for (Pattern datePattern : datePatterns) {
            Matcher dateMatcher = datePattern.matcher(text);
            if (dateMatcher.find()) {
                String dateStr = dateMatcher.group(1).trim();
                System.out.println("Found date string: " + dateStr);
                try {
                    String normalizedDate = null;
                    if (dateStr.matches("\\d{2}\\.\\d{2}\\.\\d{4}")) {

                        String[] parts = dateStr.split("\\.");
                        normalizedDate = parts[2] + "-" + parts[1] + "-" + parts[0];
                    } else if (dateStr.matches("\\d{4}[-/]\\d{2}[-/]\\d{2}")) {

                        normalizedDate = dateStr.replace("/", "-");
                    } else if (dateStr.matches("\\d{2}[-/]\\d{2}[-/]\\d{4}")) {

                        String[] parts = dateStr.split("[-/]");
                        normalizedDate = parts[2] + "-" + parts[1] + "-" + parts[0];
                    } else if (dateStr.matches("\\w{3}[\\s,]+\\d{1,2}[\\s,]+\\d{4}")) {

                        try {
                            java.time.format.DateTimeFormatter parser = java.time.format.DateTimeFormatter.ofPattern("[EEE, ]MMM d, yyyy", java.util.Locale.ENGLISH);
                            java.time.LocalDate date = java.time.LocalDate.parse(dateStr, parser);
                            normalizedDate = date.toString();
                        } catch (Exception inner) {
                            System.out.println("Failed to parse text date: " + inner.getMessage());
                        }
                    }
                    
                    if (normalizedDate != null) {
                        dateF.setText(normalizedDate);
                        System.out.println("Normalized date: " + normalizedDate);
                        break;
                    }
                } catch (Exception e) {
                    System.out.println("Date parsing error: " + e.getMessage());
                }
            }
        }

        Pattern warrantyPattern = Pattern.compile("(?i)(?:warranty|guarantee)\\s*(?:period)?\\s*:?\\s*(\\d+)\\s*(month|year)s?", Pattern.CASE_INSENSITIVE);
        Matcher warrantyMatcher = warrantyPattern.matcher(text);
        if (warrantyMatcher.find()) {
            int period = Integer.parseInt(warrantyMatcher.group(1));
            String unit = warrantyMatcher.group(2).toLowerCase();
            if (unit.startsWith("year")) {
                period *= 12;
            }
            monthsF.setText(String.valueOf(period));
            System.out.println("Found warranty: " + period + " months");
        } else {
            monthsF.setText("12");
        }
        
        System.out.println("=== END PARSING ===");
    }
}
