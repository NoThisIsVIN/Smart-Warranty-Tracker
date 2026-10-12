package org.example.ui;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * In-app toast notifications stacked in the bottom-right corner of the main window's screen.
 * Toasts are owned by the main window, so they never get their own taskbar entry.
 */
public final class Notifier {
    public enum Type { INFO, SUCCESS, WARNING, ERROR }

    private static final double WIDTH = 360;
    private static final double MARGIN = 20;
    private static final double GAP = 10;
    private static final int MAX_VISIBLE = 4;
    private static final Duration LIFETIME = Duration.seconds(5);
    private static final Duration ANIMATION = Duration.millis(220);

    /** Open toasts, oldest first. Only touched on the FX thread. */
    private static final List<Toast> toasts = new ArrayList<>();
    private static volatile Window owner;
    private static volatile ThemeManager themeManager;

    private Notifier() {}

    public static void setOwner(Window window) {
        owner = window;
    }

    public static void setThemeManager(ThemeManager manager) {
        themeManager = manager;
    }

    /** Shows a toast; safe to call from any thread. */
    public static void show(String title, String body, Type type) {
        if (Platform.isFxApplicationThread()) display(title, body, type);
        else Platform.runLater(() -> display(title, body, type));
    }

    private static void display(String title, String body, Type type) {
        List<Toast> open = toasts.stream().filter(t -> !t.closing).toList();
        if (open.size() >= MAX_VISIBLE) dismiss(open.get(0));
        Toast toast = new Toast(title, body, type);
        toasts.add(toast);
        reflow();
        toast.open();
    }

    private static void dismiss(Toast toast) {
        if (toast.closing) return;
        toast.closing = true;
        toast.timer.stop();
        FadeTransition fade = new FadeTransition(ANIMATION, toast.root);
        fade.setToValue(0);
        fade.setOnFinished(e -> {
            toast.stage.close();
            toasts.remove(toast);
            reflow();
        });
        fade.play();
    }

    /** Stacks toasts upwards from the bottom-right corner, newest at the bottom. */
    private static void reflow() {
        Rectangle2D bounds = screenBounds();
        double y = bounds.getMaxY() - MARGIN;
        for (int i = toasts.size() - 1; i >= 0; i--) {
            Stage stage = toasts.get(i).stage;
            y -= stage.getHeight();
            stage.setX(bounds.getMaxX() - MARGIN - stage.getWidth());
            stage.setY(y);
            y -= GAP;
        }
    }

    private static Rectangle2D screenBounds() {
        Window window = owner;
        if (window != null && window.isShowing()) {
            List<Screen> screens = Screen.getScreensForRectangle(
                    window.getX(), window.getY(), Math.max(1, window.getWidth()), Math.max(1, window.getHeight()));
            if (!screens.isEmpty()) return screens.get(0).getVisualBounds();
        }
        return Screen.getPrimary().getVisualBounds();
    }

    private static final class Toast {
        final Stage stage = new Stage(StageStyle.TRANSPARENT);
        final HBox root;
        final PauseTransition timer = new PauseTransition(LIFETIME);
        boolean closing;

        Toast(String title, String body, Type type) {
            Region accent = new Region();
            accent.getStyleClass().add("toast-accent");
            Label titleLabel = new Label(title);
            titleLabel.getStyleClass().add("toast-title");
            Label bodyLabel = new Label(body);
            bodyLabel.getStyleClass().add("toast-body");
            bodyLabel.setWrapText(true);
            VBox text = new VBox(2, titleLabel, bodyLabel);
            HBox.setHgrow(text, Priority.ALWAYS);

            root = new HBox(12, accent, text);
            root.getStyleClass().addAll("toast", "toast-" + type.name().toLowerCase(Locale.ROOT));
            root.setOpacity(0);
            root.setOnMouseClicked(e -> dismiss(this));
            root.setOnMouseEntered(e -> timer.pause());
            root.setOnMouseExited(e -> { if (!closing) timer.play(); });
            timer.setOnFinished(e -> dismiss(this));

            Scene scene = new Scene(root);
            scene.setFill(Color.TRANSPARENT);
            ThemeManager manager = themeManager;
            if (manager != null) manager.apply(scene);
            root.applyCss();

            Window window = owner;
            if (window != null && window.isShowing()) stage.initOwner(window);
            stage.setAlwaysOnTop(true);
            stage.setScene(scene);
            stage.setWidth(WIDTH);
            stage.setHeight(root.prefHeight(WIDTH));
        }

        void open() {
            stage.show();
            FadeTransition fade = new FadeTransition(ANIMATION, root);
            fade.setToValue(1);
            TranslateTransition slide = new TranslateTransition(ANIMATION, root);
            slide.setFromX(24);
            slide.setToX(0);
            new ParallelTransition(fade, slide).play();
            timer.play();
        }
    }
}
