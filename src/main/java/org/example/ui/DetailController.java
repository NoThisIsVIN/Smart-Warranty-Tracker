package org.example.ui;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.example.config.AppSettings;
import org.example.model.Warranty;
import org.example.model.WarrantyStatus;
import org.example.receipt.ReceiptTextExtractor;
import org.example.recommender.Recommendation;
import org.example.recommender.SimpleRecommender;
import org.example.service.FileStorage;
import org.example.service.WarrantyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Controller for {@code detail.fxml}; created by {@link ViewLoader} with constructor injection. */
public class DetailController {
    private static final Logger log = LoggerFactory.getLogger(DetailController.class);
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(".png", ".jpg", ".jpeg", ".gif", ".bmp", ".webp");
    private static final String STATUS_PREFIX = "status-";

    private final WarrantyService warrantyService;
    private final WarrantyFormDialog formDialog;
    private final SimpleRecommender recommender;
    private final FileStorage storage;
    private final ReceiptTextExtractor extractor;
    private final AppSettings settings;

    @FXML private Label titleLabel;
    @FXML private Label subtitleLabel;
    @FXML private Label statusPill;
    @FXML private StackPane imageWell;
    @FXML private ImageView productImage;
    @FXML private Label imagePlaceholder;
    @FXML private Label modelValue;
    @FXML private Label retailerValue;
    @FXML private Label purchasedValue;
    @FXML private Label durationValue;
    @FXML private Label expiresValue;
    @FXML private ProgressBar progressBar;
    @FXML private Label progressLabel;
    @FXML private ImageView receiptImage;
    @FXML private Label receiptPlaceholder;
    @FXML private Button openReceiptButton;
    @FXML private Button attachReceiptButton;
    @FXML private VBox linkList;

    private Warranty warranty;
    private Task<Image> receiptPreviewTask;

    public DetailController(WarrantyService warrantyService, WarrantyFormDialog formDialog, SimpleRecommender recommender,
                            FileStorage storage, ReceiptTextExtractor extractor, AppSettings settings) {
        this.warrantyService = warrantyService;
        this.formDialog = formDialog;
        this.recommender = recommender;
        this.storage = storage;
        this.extractor = extractor;
        this.settings = settings;
    }

    public void setWarranty(Warranty warranty) {
        this.warranty = warranty;
        showOverview();
        showProductImage();
        showReceipt();
        showLinks();
    }

    private void showOverview() {
        LocalDate today = LocalDate.now();
        WarrantyStatus status = warranty.status(today, settings.getReminderDays());
        boolean dated = warranty.getPurchaseDate() != null;

        titleLabel.setText(warranty.getDisplayName());
        subtitleLabel.setText(Formats.orDash(warranty.getRetailer()));
        setShown(subtitleLabel, warranty.getRetailer() != null && !warranty.getRetailer().isBlank());
        statusPill.setText(status.label());
        setStatusClass(statusPill, status);
        setShown(statusPill, dated);

        modelValue.setText(Formats.orDash(warranty.getModelName()));
        retailerValue.setText(Formats.orDash(warranty.getRetailer()));
        purchasedValue.setText(Formats.date(warranty.getPurchaseDate()));
        durationValue.setText(Formats.duration(warranty.getWarrantyMonths()));
        expiresValue.setText(Formats.date(warranty.getExpiryDate()));

        progressBar.setProgress(warranty.elapsedFraction(today));
        setStatusClass(progressBar, status);
        setShown(progressBar, dated);
        progressLabel.setText(progressText(today, dated));
    }

    private String progressText(LocalDate today, boolean dated) {
        String daysLeft = Formats.daysLeft(warranty, today);
        if (!dated) return daysLeft;
        return daysLeft + " · " + Math.round(warranty.elapsedFraction(today) * 100) + "% elapsed";
    }

    private void showProductImage() {
        Optional<Path> file = storage.resolve(warranty.getImageUrl());
        imagePlaceholder.setText(initials(warranty.getDisplayName()));
        imageWell.setCursor(file.isPresent() ? Cursor.HAND : Cursor.DEFAULT);
        Image image = file.map(p -> new Image(p.toUri().toString(), 640, 480, true, true, true)).orElse(null);
        productImage.setImage(image);
        imagePlaceholder.visibleProperty().unbind();
        if (image == null) imagePlaceholder.setVisible(true);
        else imagePlaceholder.visibleProperty().bind(notLoaded(image));
    }

    private void showReceipt() {
        if (receiptPreviewTask != null) receiptPreviewTask.cancel();
        String stored = warranty.getReceiptImageUrl();
        boolean hasReceipt = stored != null && !stored.isBlank();
        Optional<Path> file = storage.resolve(stored);

        attachReceiptButton.setText(hasReceipt ? "Replace" : "Attach");
        openReceiptButton.setDisable(file.isEmpty());
        receiptImage.setImage(null);
        receiptPlaceholder.visibleProperty().unbind();
        receiptPlaceholder.setVisible(true);
        if (file.isEmpty()) {
            receiptPlaceholder.setText(hasReceipt ? "File missing" : "No receipt");
            return;
        }
        Path path = file.get();
        String ext = FileStorage.extension(path.getFileName().toString());
        receiptPlaceholder.setText(ext.isEmpty() ? "File" : ext.substring(1).toUpperCase(Locale.ROOT));
        if (IMAGE_EXTENSIONS.contains(ext)) {
            Image image = new Image(path.toUri().toString(), 240, 180, true, true, true);
            receiptImage.setImage(image);
            receiptPlaceholder.visibleProperty().bind(notLoaded(image));
        } else if (ext.equals(".pdf")) {
            renderPdfPreview(path);
        }
    }

    private void renderPdfPreview(Path pdf) {
        Task<Image> task = new Task<>() {
            @Override
            protected Image call() {
                return extractor.renderFirstPage(pdf).map(DetailController::toFxImage).orElse(null);
            }
        };
        task.setOnSucceeded(e -> {
            if (task.getValue() == null) return;
            receiptImage.setImage(task.getValue());
            receiptPlaceholder.setVisible(false);
        });
        task.setOnFailed(e -> log.warn("Couldn't render receipt preview", task.getException()));
        receiptPreviewTask = task;
        WarrantyFormDialog.runAsync(task, "receipt-preview");
    }

    private void showLinks() {
        linkList.getChildren().clear();
        List<Recommendation> recommendations = recommender.recommend(warranty);
        for (Recommendation r : recommendations) {
            Hyperlink link = new Hyperlink(r.label());
            link.setOnAction(e -> Browser.open(r.url()));
            linkList.getChildren().add(link);
        }
        if (recommendations.isEmpty()) {
            Label none = new Label("No suggestions for this product.");
            none.getStyleClass().add("muted");
            linkList.getChildren().add(none);
        }
    }

    @FXML
    private void onImageClicked() {
        openStored(warranty.getImageUrl());
    }

    @FXML
    private void onOpenReceipt() {
        openStored(warranty.getReceiptImageUrl());
    }

    @FXML
    private void onAttachReceipt() {
        File file = WarrantyFormDialog.receiptChooser().showOpenDialog(window());
        if (file == null) return;
        Warranty current = warranty;
        Task<Warranty> task = new Task<>() {
            @Override
            protected Warranty call() throws IOException {
                return warrantyService.replaceReceipt(current, file.toPath());
            }
        };
        task.setOnSucceeded(e -> {
            attachReceiptButton.setDisable(false);
            warranty = task.getValue();
            showReceipt();
        });
        task.setOnFailed(e -> {
            attachReceiptButton.setDisable(false);
            log.warn("Couldn't attach receipt {}", file.getName(), task.getException());
            Dialogs.error(window(), "Couldn't attach receipt", task.getException().getMessage());
        });
        attachReceiptButton.setDisable(true);
        WarrantyFormDialog.runAsync(task, "receipt-import");
    }

    @FXML
    private void onEdit() {
        formDialog.showEdit(window(), warranty).ifPresent(this::setWarranty);
    }

    @FXML
    private void onDelete() {
        String message = "This removes " + warranty.getDisplayName() + " and its stored image and receipt.";
        if (!Dialogs.confirm(window(), "Delete warranty?", message, "Delete")) return;
        try {
            warrantyService.delete(warranty);
            onClose();
        } catch (RuntimeException e) {
            log.error("Couldn't delete warranty {}", warranty.getId(), e);
            Dialogs.error(window(), "Couldn't delete warranty", e.getMessage());
        }
    }

    @FXML
    private void onClose() {
        if (receiptPreviewTask != null) receiptPreviewTask.cancel();
        window().hide();
    }

    private void openStored(String stored) {
        storage.resolve(stored).ifPresent(path -> Browser.open(path.toUri().toString()));
    }

    private Window window() {
        return titleLabel.getScene().getWindow();
    }

    private static BooleanBinding notLoaded(Image image) {
        return Bindings.createBooleanBinding(() -> image.getProgress() < 1 || image.isError(),
                image.progressProperty(), image.errorProperty());
    }

    private static Image toFxImage(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int[] argb = source.getRGB(0, 0, width, height, null, 0, width);
        WritableImage image = new WritableImage(width, height);
        image.getPixelWriter().setPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), argb, 0, width);
        return image;
    }

    private static String initials(String name) {
        StringBuilder sb = new StringBuilder();
        for (String word : name.strip().split("\\s+")) {
            if (!word.isEmpty() && sb.length() < 2) sb.appendCodePoint(word.codePointAt(0));
        }
        return sb.isEmpty() ? "?" : sb.toString().toUpperCase(Locale.ROOT);
    }

    private static void setStatusClass(Node node, WarrantyStatus status) {
        node.getStyleClass().removeIf(c -> c.startsWith(STATUS_PREFIX) && !c.equals("status-pill"));
        node.getStyleClass().add(STATUS_PREFIX + status.name().toLowerCase(Locale.ROOT));
    }

    private static void setShown(Node node, boolean shown) {
        node.setVisible(shown);
        node.setManaged(shown);
    }
}
