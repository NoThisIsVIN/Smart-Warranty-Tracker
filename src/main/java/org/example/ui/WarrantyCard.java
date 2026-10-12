package org.example.ui;

import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.example.model.Warranty;
import org.example.model.WarrantyStatus;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/** One warranty tile in the main grid. Clicking it, or pressing Enter/Space while focused, runs {@code onOpen}. */
public final class WarrantyCard extends VBox {
    static final double WIDTH = 280;
    static final double MEDIA_HEIGHT = 160;

    /**
     * @param image product image (possibly still loading in the background), or null to show the product's initials
     */
    public WarrantyCard(Warranty w, LocalDate today, int reminderDays, Image image, Runnable onOpen) {
        WarrantyStatus status = w.status(today, reminderDays);
        String statusClass = "status-" + status.name().toLowerCase(Locale.ROOT);
        String daysLeft = Formats.daysLeft(w, today);

        getStyleClass().add("warranty-card");
        setMinWidth(WIDTH);
        setPrefWidth(WIDTH);
        setMaxWidth(WIDTH);
        setCursor(Cursor.HAND);
        setFocusTraversable(true);
        setAccessibleRole(AccessibleRole.BUTTON);
        setAccessibleText(w.getDisplayName() + ", " + statusText(status) + ", " + daysLeft);
        getChildren().addAll(media(w, image), body(w, today, statusClass, statusText(status), daysLeft));

        setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) onOpen.run();
        });
        setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE) {
                e.consume();
                onOpen.run();
            }
        });
    }

    private static StackPane media(Warranty w, Image image) {
        StackPane media = new StackPane();
        media.getStyleClass().add("card-media");
        media.setMinSize(WIDTH, MEDIA_HEIGHT);
        media.setPrefSize(WIDTH, MEDIA_HEIGHT);
        media.setMaxSize(WIDTH, MEDIA_HEIGHT);

        Label placeholder = new Label(initials(w.getDisplayName()));
        placeholder.getStyleClass().add("card-media-placeholder");
        if (image == null) {
            media.getChildren().add(placeholder);
        } else if (isSettled(image)) {
            showImage(media, image, placeholder);
        } else {
            // Background loading: swap the image in once it finishes, then detach so cached images don't retain old cards.
            InvalidationListener onDone = new InvalidationListener() {
                @Override
                public void invalidated(Observable observable) {
                    if (!isSettled(image)) return;
                    image.progressProperty().removeListener(this);
                    image.errorProperty().removeListener(this);
                    showImage(media, image, placeholder);
                }
            };
            image.progressProperty().addListener(onDone);
            image.errorProperty().addListener(onDone);
        }
        return media;
    }

    private static boolean isSettled(Image image) {
        return image.isError() || image.getProgress() >= 1;
    }

    private static void showImage(StackPane media, Image image, Label placeholder) {
        media.getChildren().setAll(image.isError() ? placeholder : new ImageView(image));
    }

    private static VBox body(Warranty w, LocalDate today, String statusClass, String statusText, String daysLeft) {
        Label title = new Label(w.getDisplayName());
        title.getStyleClass().add("card-title");
        title.setMaxWidth(Double.MAX_VALUE);

        Label subtitle = new Label(Formats.orDash(w.getRetailer()));
        subtitle.getStyleClass().add("card-subtitle");
        subtitle.setMaxWidth(Double.MAX_VALUE);

        Label pill = new Label(statusText);
        pill.getStyleClass().addAll("status-pill", statusClass);
        Label meta = new Label(daysLeft);
        meta.getStyleClass().add("card-meta");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox statusRow = new HBox(8, pill, spacer, meta);
        statusRow.setAlignment(Pos.CENTER_LEFT);

        ProgressBar progress = new ProgressBar(w.elapsedFraction(today));
        progress.getStyleClass().addAll("progress-slim", statusClass);
        progress.setMaxWidth(Double.MAX_VALUE);

        Label footer = new Label(w.getPurchaseDate() == null ? "No purchase date"
                : "Purchased " + Formats.date(w.getPurchaseDate()) + " · Expires " + Formats.date(w.getExpiryDate()));
        footer.getStyleClass().add("card-footer");
        footer.setWrapText(true);

        VBox body = new VBox(8, title, subtitle, statusRow, progress, footer);
        body.getStyleClass().add("card-body");
        return body;
    }

    private static String statusText(WarrantyStatus status) {
        return switch (status) {
            case ACTIVE -> "Active";
            case EXPIRING -> "Expiring";
            case EXPIRED -> "Expired";
        };
    }

    private static String initials(String name) {
        String initials = Arrays.stream(name.trim().split("\\s+"))
                .filter(word -> !word.isEmpty())
                .limit(2)
                .map(word -> word.substring(0, 1))
                .collect(Collectors.joining())
                .toUpperCase(Locale.ROOT);
        return initials.isEmpty() ? "?" : initials;
    }
}
