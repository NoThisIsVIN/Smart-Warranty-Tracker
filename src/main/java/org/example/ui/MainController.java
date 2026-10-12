package org.example.ui;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.Duration;
import org.example.config.AppSettings;
import org.example.model.Warranty;
import org.example.model.WarrantyStatus;
import org.example.service.FileStorage;
import org.example.service.ProductImageService;
import org.example.service.WarrantyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Main dashboard: sidebar navigation, summary tiles and the warranty card grid. Created by {@link ViewLoader}
 * through Spring's bean factory, so it is constructor-injected without being a singleton bean.
 */
public class MainController {
    private static final Logger log = LoggerFactory.getLogger(MainController.class);
    /** Re-render periodically so "days left" and statuses roll over while the app stays open. */
    private static final Duration RERENDER_INTERVAL = Duration.minutes(10);

    private enum Filter {
        ALL(null), ACTIVE(WarrantyStatus.ACTIVE), EXPIRING(WarrantyStatus.EXPIRING), EXPIRED(WarrantyStatus.EXPIRED);

        private final WarrantyStatus status;

        Filter(WarrantyStatus status) { this.status = status; }

        boolean matches(WarrantyStatus s) { return status == null || status == s; }
    }

    private enum Sort {
        EXPIRY("Expiry (soonest)", Comparator.comparing(Warranty::getExpiryDate,
                Comparator.nullsLast(Comparator.<LocalDate>naturalOrder()))),
        NAME("Name (A–Z)", Comparator.comparing(Warranty::getDisplayName, String.CASE_INSENSITIVE_ORDER)),
        RECENT("Recently added", Comparator.comparing(Warranty::getId,
                Comparator.nullsLast(Comparator.<Long>reverseOrder())));

        private final String label;
        private final Comparator<Warranty> order;

        Sort(String label, Comparator<Warranty> order) {
            this.label = label;
            this.order = order.thenComparing(Warranty::getDisplayName, String.CASE_INSENSITIVE_ORDER);
        }

        @Override
        public String toString() { return label; }
    }

    private final WarrantyService warrantyService;
    private final ProductImageService imageService;
    private final FileStorage fileStorage;
    private final AppSettings settings;
    private final WarrantyFormDialog formDialog;
    private final DetailView detailView;
    private final SettingsView settingsView;

    /** Decoded card images keyed by file URI, so re-rendering (e.g. while typing a search) doesn't reload them. */
    private final Map<String, Image> imageCache = new HashMap<>();
    /** Everything in the database; the grid is always derived from this list (filter + search + sort). */
    private List<Warranty> warranties = List.of();
    private Filter filter = Filter.ALL;

    @FXML private Button navAll;
    @FXML private Button navExpiring;
    @FXML private Label expiringBadge;
    @FXML private Label pageTitle;
    @FXML private Label pageSubtitle;
    @FXML private TextField searchField;
    @FXML private HBox statRow;
    @FXML private Label totalValue;
    @FXML private Label activeValue;
    @FXML private Label expiringValue;
    @FXML private Label expiredValue;
    @FXML private ToggleGroup filterGroup;
    @FXML private ComboBox<Sort> sortBox;
    @FXML private ScrollPane cardScroll;
    @FXML private FlowPane cardGrid;
    @FXML private VBox emptyState;
    @FXML private Label emptyTitle;
    @FXML private Label emptyText;
    @FXML private Button emptyAddButton;
    @FXML private Button emptyClearButton;

    public MainController(WarrantyService warrantyService, ProductImageService imageService, FileStorage fileStorage,
                          AppSettings settings, WarrantyFormDialog formDialog, DetailView detailView,
                          SettingsView settingsView) {
        this.warrantyService = warrantyService;
        this.imageService = imageService;
        this.fileStorage = fileStorage;
        this.settings = settings;
        this.formDialog = formDialog;
        this.detailView = detailView;
        this.settingsView = settingsView;
    }

    @FXML
    private void initialize() {
        sortBox.getItems().setAll(Sort.values());
        sortBox.setValue(Sort.EXPIRY);
        sortBox.valueProperty().addListener((obs, old, sort) -> render());
        searchField.textProperty().addListener((obs, old, text) -> render());
        filterGroup.selectedToggleProperty().addListener((obs, old, selected) -> {
            if (selected == null) {
                old.setSelected(true); // a segmented control always has one segment selected
                return;
            }
            filter = Filter.valueOf((String) selected.getUserData());
            render();
        });
        formDialog.setOnImageUpdated(this::replaceLater);

        reload();
        imageService.fetchMissingAsync(warranties, this::replaceLater);

        Timeline ticker = new Timeline(new KeyFrame(RERENDER_INTERVAL, e -> render()));
        ticker.setCycleCount(Animation.INDEFINITE);
        ticker.play();
    }

    /** Warranties currently in the "expiring soon" window, for the startup summary toast. */
    public List<Warranty> expiringSoon() {
        LocalDate today = LocalDate.now();
        int reminderDays = settings.getReminderDays();
        return warranties.stream().filter(w -> w.status(today, reminderDays) == WarrantyStatus.EXPIRING).toList();
    }

    @FXML
    private void onAdd() {
        formDialog.showAdd(window()).ifPresent(saved -> reload());
    }

    @FXML
    private void onSettings() {
        settingsView.show(window());
        reload();
    }

    @FXML
    private void onNavAll() {
        selectFilter(Filter.ALL);
    }

    @FXML
    private void onNavExpiring() {
        selectFilter(Filter.EXPIRING);
    }

    @FXML
    private void onTileClicked(MouseEvent event) {
        selectFilter(Filter.valueOf((String) ((Node) event.getSource()).getUserData()));
    }

    @FXML
    private void onClearFilters() {
        searchField.clear();
        selectFilter(Filter.ALL);
    }

    private void open(Warranty w) {
        detailView.show(window(), w);
        reload();
    }

    private void selectFilter(Filter target) {
        for (Toggle toggle : filterGroup.getToggles()) {
            if (target.name().equals(toggle.getUserData())) filterGroup.selectToggle(toggle);
        }
    }

    private void reload() {
        try {
            warranties = List.copyOf(warrantyService.findAll());
        } catch (RuntimeException e) {
            log.error("Could not load warranties", e);
            Dialogs.error(window(), "Couldn't load warranties", e.getMessage());
        }
        render();
    }

    /** Called (from any thread) when a product image was fetched in the background. */
    private void replaceLater(Warranty updated) {
        Platform.runLater(() -> {
            warranties = warranties.stream()
                    .map(w -> Objects.equals(w.getId(), updated.getId()) ? updated : w)
                    .toList();
            render();
        });
    }

    private void render() {
        LocalDate today = LocalDate.now();
        int reminderDays = settings.getReminderDays();
        Map<WarrantyStatus, Long> counts = warranties.stream().collect(Collectors.groupingBy(
                w -> w.status(today, reminderDays), () -> new EnumMap<>(WarrantyStatus.class), Collectors.counting()));
        updateSummary(counts);
        updateSelection();

        String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        List<Node> cards = warranties.stream()
                .filter(w -> filter.matches(w.status(today, reminderDays)))
                .filter(w -> matchesSearch(w, query))
                .sorted(sortBox.getValue().order)
                .map(w -> (Node) new WarrantyCard(w, today, reminderDays, imageFor(w), () -> open(w)))
                .toList();
        cardGrid.getChildren().setAll(cards);
        updateEmptyState(cards.isEmpty());
    }

    private void updateSummary(Map<WarrantyStatus, Long> counts) {
        long expiring = counts.getOrDefault(WarrantyStatus.EXPIRING, 0L);
        totalValue.setText(String.valueOf(warranties.size()));
        activeValue.setText(String.valueOf(counts.getOrDefault(WarrantyStatus.ACTIVE, 0L)));
        expiringValue.setText(String.valueOf(expiring));
        expiredValue.setText(String.valueOf(counts.getOrDefault(WarrantyStatus.EXPIRED, 0L)));
        expiringBadge.setText(String.valueOf(expiring));
        navExpiring.setGraphic(expiring > 0 ? expiringBadge : null);

        String subtitle = warranties.size() == 1 ? "1 warranty" : warranties.size() + " warranties";
        pageSubtitle.setText(expiring > 0 ? subtitle + " · " + expiring + " expiring soon" : subtitle);
    }

    private void updateSelection() {
        boolean expiringView = filter == Filter.EXPIRING;
        pageTitle.setText(expiringView ? "Expiring soon" : "All warranties");
        setStyleClass(navAll, "active", !expiringView);
        setStyleClass(navExpiring, "active", expiringView);
        statRow.getChildren().forEach(tile -> setStyleClass(tile, "selected", filter.name().equals(tile.getUserData())));
    }

    private void updateEmptyState(boolean noCards) {
        boolean noData = warranties.isEmpty();
        setShown(cardScroll, !noCards);
        setShown(emptyState, noCards);
        setShown(emptyAddButton, noData);
        setShown(emptyClearButton, !noData);
        emptyTitle.setText(noData ? "No warranties yet" : "No matches");
        emptyText.setText(noData
                ? "Add your first purchase to keep its receipt and expiry date in one place."
                : "Nothing matches the current search and filter.");
    }

    private Image imageFor(Warranty w) {
        String stored = w.getImageUrl();
        if (stored == null || stored.isBlank()) return null;
        return fileStorage.resolve(stored)
                .map(path -> imageCache.computeIfAbsent(path.toUri().toString(), uri ->
                        new Image(uri, WarrantyCard.WIDTH, WarrantyCard.MEDIA_HEIGHT, true, true, true)))
                .orElse(null);
    }

    private static boolean matchesSearch(Warranty w, String query) {
        if (query.isEmpty()) return true;
        return Stream.of(w.getProductName(), w.getModelName(), w.getRetailer())
                .filter(Objects::nonNull)
                .anyMatch(field -> field.toLowerCase(Locale.ROOT).contains(query));
    }

    private static void setStyleClass(Node node, String styleClass, boolean on) {
        node.getStyleClass().remove(styleClass);
        if (on) node.getStyleClass().add(styleClass);
    }

    private static void setShown(Node node, boolean shown) {
        node.setVisible(shown);
        node.setManaged(shown);
    }

    private Window window() {
        Scene scene = cardGrid.getScene();
        return scene == null ? null : scene.getWindow();
    }
}
