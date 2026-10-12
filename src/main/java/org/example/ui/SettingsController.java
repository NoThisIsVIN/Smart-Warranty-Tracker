package org.example.ui;

import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;
import org.example.config.ApiKeys;
import org.example.config.AppSettings;
import org.example.config.AppSettings.Theme;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

/** Controller for {@code settings.fxml}; created by {@link ViewLoader} with constructor injection. */
public class SettingsController {
    private static final Logger log = LoggerFactory.getLogger(SettingsController.class);

    private final AppSettings settings;
    private final ApiKeys apiKeys;
    private final ThemeManager themeManager;

    @FXML private Spinner<Integer> reminderSpinner;
    @FXML private ChoiceBox<Theme> themeChoice;
    @FXML private TextField visionKeyField;
    @FXML private TextField ocrKeyField;
    @FXML private TextField searchKeyField;
    @FXML private TextField searchEngineField;
    @FXML private Label errorLabel;

    private Map<String, TextField> keyFields;

    public SettingsController(AppSettings settings, ApiKeys apiKeys, ThemeManager themeManager) {
        this.settings = settings;
        this.apiKeys = apiKeys;
        this.themeManager = themeManager;
    }

    @FXML
    private void initialize() {
        WarrantyFormDialog.configureIntSpinner(reminderSpinner, 1, 365, settings.getReminderDays());
        themeChoice.getItems().setAll(Theme.values());
        themeChoice.setConverter(new StringConverter<>() {
            @Override
            public String toString(Theme theme) {
                return theme == Theme.DARK ? "Dark" : "Light";
            }

            @Override
            public Theme fromString(String text) {
                return "Dark".equalsIgnoreCase(text) ? Theme.DARK : Theme.LIGHT;
            }
        });
        themeChoice.setValue(settings.getTheme());

        keyFields = Map.of(
                ApiKeys.GOOGLE_VISION_KEY, visionKeyField,
                ApiKeys.OCR_SPACE_KEY, ocrKeyField,
                ApiKeys.GOOGLE_SEARCH_KEY, searchKeyField,
                ApiKeys.GOOGLE_SEARCH_ENGINE_ID, searchEngineField);
        keyFields.forEach((key, field) -> field.setText(apiKeys.get(key)));
    }

    @FXML
    private void onSave() {
        WarrantyFormDialog.commitSpinner(reminderSpinner);
        try {
            apiKeys.save(enteredKeys());
        } catch (IOException e) {
            log.warn("Couldn't save API keys", e);
            showError("Couldn't save API keys: " + e.getMessage());
            return;
        }
        settings.setReminderDays(reminderSpinner.getValue());
        themeManager.setTheme(themeChoice.getValue());
        close();
    }

    @FXML
    private void onCancel() {
        close();
    }

    @FXML
    private void onTestNotification() {
        Notifier.show("Test notification", "Notifications are working.", Notifier.Type.INFO);
    }

    private Properties enteredKeys() {
        Properties values = new Properties();
        keyFields.forEach((key, field) -> values.setProperty(key, field.getText().strip()));
        return values;
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void close() {
        errorLabel.getScene().getWindow().hide();
    }
}
