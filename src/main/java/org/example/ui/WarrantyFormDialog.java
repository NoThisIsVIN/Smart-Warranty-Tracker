package org.example.ui;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Window;
import javafx.util.StringConverter;
import org.example.model.Warranty;
import org.example.receipt.ParsedReceipt;
import org.example.receipt.ReceiptParser;
import org.example.receipt.ReceiptTextExtractor;
import org.example.service.ProductImageService;
import org.example.service.WarrantyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** The add/edit warranty form. When adding, a chosen receipt is read in the background to pre-fill empty fields. */
@Component
public class WarrantyFormDialog {
    private static final Logger log = LoggerFactory.getLogger(WarrantyFormDialog.class);
    private static final List<String> IMAGE_PATTERNS = List.of("*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.webp");
    private static final int DEFAULT_MONTHS = 12;
    private static final int MAX_MONTHS = 600;

    private static final StringConverter<LocalDate> ISO_DATES = new StringConverter<>() {
        @Override
        public String toString(LocalDate date) {
            return date == null ? "" : date.toString();
        }

        @Override
        public LocalDate fromString(String text) {
            if (text == null || text.isBlank()) return null;
            try {
                return LocalDate.parse(text.strip());
            } catch (DateTimeParseException e) {
                return null;
            }
        }
    };

    private final WarrantyService warrantyService;
    private final ProductImageService imageService;
    private final ReceiptTextExtractor extractor;
    private final ThemeManager themeManager;
    private Consumer<Warranty> onImageUpdated = w -> {};

    public WarrantyFormDialog(WarrantyService warrantyService, ProductImageService imageService,
                              ReceiptTextExtractor extractor, ThemeManager themeManager) {
        this.warrantyService = warrantyService;
        this.imageService = imageService;
        this.extractor = extractor;
        this.themeManager = themeManager;
    }

    public Optional<Warranty> showAdd(Window owner) {
        Optional<Warranty> saved = new Form(owner, null).show();
        saved.ifPresent(w -> {
            imageService.fetchMissingAsync(List.of(w), updated -> Platform.runLater(() -> onImageUpdated.accept(updated)));
            Notifier.show("Warranty added", w.getDisplayName(), Notifier.Type.SUCCESS);
        });
        return saved;
    }

    public Optional<Warranty> showEdit(Window owner, Warranty warranty) {
        return new Form(owner, warranty).show();
    }

    /** Hook invoked on the FX thread when a product image found after adding a warranty has been stored. */
    public void setOnImageUpdated(Consumer<Warranty> callback) {
        onImageUpdated = callback == null ? w -> {} : callback;
    }

    static FileChooser receiptChooser() {
        List<String> supported = new ArrayList<>(IMAGE_PATTERNS);
        supported.addAll(List.of("*.pdf", "*.docx"));
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose receipt");
        chooser.getExtensionFilters().addAll(
                new ExtensionFilter("All supported", supported),
                new ExtensionFilter("Images", IMAGE_PATTERNS),
                new ExtensionFilter("PDF", "*.pdf"),
                new ExtensionFilter("Word (.docx)", "*.docx"));
        return chooser;
    }

    static void runAsync(Task<?> task, String threadName) {
        Thread thread = new Thread(task, threadName);
        thread.setDaemon(true);
        thread.start();
    }

    /** Editable integer spinner that tolerates bad input (reverts) and commits when focus leaves it. */
    static void configureIntSpinner(Spinner<Integer> spinner, int min, int max, int value) {
        IntegerSpinnerValueFactory factory = new IntegerSpinnerValueFactory(min, max, Math.max(min, Math.min(max, value)));
        factory.setConverter(new StringConverter<>() {
            @Override
            public String toString(Integer v) {
                return v == null ? "" : v.toString();
            }

            @Override
            public Integer fromString(String text) {
                try {
                    return Integer.parseInt(text == null ? "" : text.strip());
                } catch (NumberFormatException e) {
                    return factory.getValue();
                }
            }
        });
        spinner.setValueFactory(factory);
        spinner.setEditable(true);
        spinner.focusedProperty().addListener((obs, was, focused) -> {
            if (!focused) commitSpinner(spinner);
        });
    }

    static void commitSpinner(Spinner<Integer> spinner) {
        spinner.commitValue();
        spinner.getEditor().setText(String.valueOf(spinner.getValue()));
    }

    private static void setShown(Node node, boolean shown) {
        node.setVisible(shown);
        node.setManaged(shown);
    }

    private static Label styled(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        return label;
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    private static String orEmpty(String text) {
        return text == null ? "" : text;
    }

    private final class Form {
        private final Warranty original;
        private final Dialog<ButtonType> dialog = new Dialog<>();
        private final ButtonType saveType;
        private final TextField name = new TextField();
        private final TextField model = new TextField();
        private final TextField retailer = new TextField();
        private final DatePicker date = new DatePicker();
        private final Spinner<Integer> months = new Spinner<>();
        private final Label durationHint = styled("", "hint");
        private final Label expires = styled("", "field-value");
        private final Label error = styled("", "form-error");
        private final Label receiptName = styled("No file", "muted");
        private final ProgressIndicator readingIndicator = new ProgressIndicator();
        private final HBox readingBox = new HBox(6, readingIndicator, styled("Reading receipt…", "muted"));
        private final Label readFailed = styled("Couldn't read this receipt — fill the details manually.", "hint");
        private final BooleanProperty saving = new SimpleBooleanProperty();
        private Path receipt;
        private Task<ParsedReceipt> readTask;
        private boolean filling;
        private boolean dateEdited;
        private boolean monthsEdited;
        private Warranty saved;

        Form(Window owner, Warranty original) {
            this.original = original;
            this.saveType = new ButtonType(original == null ? "Add warranty" : "Save changes", ButtonData.OK_DONE);
            populate();
            configureDialog(owner);
        }

        Optional<Warranty> show() {
            dialog.showAndWait();
            return Optional.ofNullable(saved);
        }

        private void populate() {
            int initialMonths = original == null ? DEFAULT_MONTHS : original.getWarrantyMonths();
            configureIntSpinner(months, 1, MAX_MONTHS, initialMonths);
            months.setPrefWidth(100);
            date.setConverter(ISO_DATES);
            date.setEditable(true);
            date.setPromptText("YYYY-MM-DD");
            date.setMaxWidth(Double.MAX_VALUE);
            if (original == null) {
                date.setValue(LocalDate.now());
            } else {
                name.setText(orEmpty(original.getProductName()));
                model.setText(orEmpty(original.getModelName()));
                retailer.setText(orEmpty(original.getRetailer()));
                date.setValue(original.getPurchaseDate());
            }
            date.valueProperty().addListener((obs, old, value) -> dateEdited |= !filling);
            months.valueProperty().addListener((obs, old, value) -> monthsEdited |= !filling);
            durationHint.textProperty().bind(Bindings.createStringBinding(
                    () -> "months · " + Formats.duration(months.getValue()), months.valueProperty()));
            expires.textProperty().bind(Bindings.createStringBinding(this::expiryText,
                    date.valueProperty(), months.valueProperty()));
        }

        private String expiryText() {
            LocalDate purchased = date.getValue();
            return Formats.date(purchased == null ? null : purchased.plusMonths(months.getValue()));
        }

        private void configureDialog(Window owner) {
            dialog.initOwner(owner);
            dialog.setTitle(original == null ? "Add warranty" : "Edit warranty");
            DialogPane pane = dialog.getDialogPane();
            pane.setContent(content());
            pane.getButtonTypes().setAll(ButtonType.CANCEL, saveType);

            Button ok = (Button) pane.lookupButton(saveType);
            ok.getStyleClass().add("btn-primary");
            ok.disableProperty().bind(saving.or(incomplete()));
            ok.addEventFilter(ActionEvent.ACTION, this::onSave);
            Button cancel = (Button) pane.lookupButton(ButtonType.CANCEL);
            cancel.getStyleClass().add("btn-secondary");
            cancel.disableProperty().bind(saving);

            dialog.setOnCloseRequest(e -> {
                if (saving.get()) e.consume();
            });
            dialog.setOnHidden(e -> {
                if (readTask != null) readTask.cancel();
            });
            themeManager.apply(pane);
            Platform.runLater(name::requestFocus);
        }

        private BooleanBinding incomplete() {
            return Bindings.createBooleanBinding(
                    () -> name.getText().isBlank() || date.getEditor().getText().isBlank(),
                    name.textProperty(), date.getEditor().textProperty());
        }

        private Node content() {
            GridPane grid = new GridPane();
            grid.getStyleClass().add("form-grid");
            grid.setHgap(12);
            grid.setVgap(10);
            ColumnConstraints fields = new ColumnConstraints();
            fields.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().addAll(new ColumnConstraints(), fields);

            int row = 0;
            addRow(grid, row++, "Product name *", name);
            addRow(grid, row++, "Model", model);
            addRow(grid, row++, "Retailer", retailer);
            addRow(grid, row++, "Purchase date *", date);
            addRow(grid, row++, "Warranty length", new HBox(8, months, durationHint));
            addRow(grid, row++, "Expires", expires);
            if (original == null) addReceiptRows(grid, row);

            error.setWrapText(true);
            setShown(error, false);
            VBox box = new VBox(14, grid, error);
            box.setPrefWidth(480);
            return box;
        }

        private void addRow(GridPane grid, int row, String label, Node field) {
            grid.addRow(row, styled(label, "field-label"), field);
        }

        private void addReceiptRows(GridPane grid, int row) {
            Button choose = new Button("Choose file…");
            choose.getStyleClass().add("btn-secondary");
            choose.setOnAction(e -> chooseReceipt());
            readingIndicator.setPrefSize(16, 16);
            readingBox.setAlignment(Pos.CENTER_LEFT);
            setShown(readingBox, false);
            setShown(readFailed, false);
            readFailed.setWrapText(true);

            HBox receiptRow = new HBox(10, choose, receiptName, readingBox);
            receiptRow.setAlignment(Pos.CENTER_LEFT);
            addRow(grid, row, "Receipt", receiptRow);
            grid.add(readFailed, 1, row + 1);
        }

        private void chooseReceipt() {
            File file = receiptChooser().showOpenDialog(dialog.getDialogPane().getScene().getWindow());
            if (file == null) return;
            receipt = file.toPath();
            receiptName.setText(file.getName());
            readReceipt(receipt);
        }

        private void readReceipt(Path file) {
            if (readTask != null) readTask.cancel();
            Task<ParsedReceipt> task = new Task<>() {
                @Override
                protected ParsedReceipt call() throws IOException {
                    return ReceiptParser.parse(extractor.extractText(file));
                }
            };
            task.setOnSucceeded(e -> finishReading(task.getValue()));
            task.setOnFailed(e -> {
                log.warn("Couldn't read receipt {}", file.getFileName(), task.getException());
                finishReading(null);
            });
            readTask = task;
            setShown(readingBox, true);
            setShown(readFailed, false);
            runAsync(task, "receipt-reader");
        }

        private void finishReading(ParsedReceipt parsed) {
            setShown(readingBox, false);
            setShown(readFailed, parsed == null || !hasAnyField(parsed));
            if (parsed != null) fillUntouched(parsed);
        }

        private boolean hasAnyField(ParsedReceipt p) {
            return p.productName() != null || p.modelName() != null || p.retailer() != null
                    || p.purchaseDate() != null || p.warrantyMonths() != null;
        }

        private void fillUntouched(ParsedReceipt p) {
            filling = true;
            fillIfEmpty(name, p.productName());
            fillIfEmpty(model, p.modelName());
            fillIfEmpty(retailer, p.retailer());
            if (p.purchaseDate() != null && !dateEdited) date.setValue(p.purchaseDate());
            if (p.warrantyMonths() != null && !monthsEdited) months.getValueFactory().setValue(p.warrantyMonths());
            filling = false;
        }

        private void fillIfEmpty(TextField field, String value) {
            if (value != null && field.getText().isBlank()) field.setText(value.strip());
        }

        private void onSave(ActionEvent event) {
            event.consume();
            date.commitValue();
            commitSpinner(months);
            String problem = validate();
            showError(problem);
            if (problem == null) save();
        }

        private String validate() {
            if (name.getText().isBlank()) return "Enter a product name.";
            if (date.getValue() == null) return "Enter the purchase date as YYYY-MM-DD.";
            return null;
        }

        private void save() {
            Warranty draft = toWarranty();
            Path receiptFile = receipt;
            Task<Warranty> task = new Task<>() {
                @Override
                protected Warranty call() throws IOException {
                    return original == null ? warrantyService.create(draft, receiptFile) : warrantyService.update(draft);
                }
            };
            task.setOnSucceeded(e -> {
                saving.set(false);
                saved = task.getValue();
                dialog.setResult(saveType);
            });
            task.setOnFailed(e -> {
                saving.set(false);
                log.warn("Couldn't save warranty", task.getException());
                showError("Couldn't save: " + task.getException().getMessage());
            });
            saving.set(true);
            runAsync(task, "warranty-save");
        }

        /** A fresh entity, so the caller's instance stays untouched if saving fails or is cancelled. */
        private Warranty toWarranty() {
            Warranty w = new Warranty(name.getText().strip(), blankToNull(model.getText()),
                    blankToNull(retailer.getText()), date.getValue(), months.getValue());
            if (original != null) {
                w.setId(original.getId());
                w.setNotified(original.isNotified());
                w.setImageUrl(original.getImageUrl());
                w.setReceiptImageUrl(original.getReceiptImageUrl());
            }
            return w;
        }

        private void showError(String message) {
            error.setText(message);
            setShown(error, message != null);
        }
    }
}
