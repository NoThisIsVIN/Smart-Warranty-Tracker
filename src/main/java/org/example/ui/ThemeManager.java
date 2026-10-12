package org.example.ui;

import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.DialogPane;
import org.example.config.AppSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/** Applies the app stylesheets and keeps every styled scene in sync with the selected theme. */
@Component
public class ThemeManager {
    private static final Logger log = LoggerFactory.getLogger(ThemeManager.class);

    private final AppSettings settings;
    private final String baseSheet = stylesheet("/styles/app.css");
    private final String darkSheet = stylesheet("/styles/dark.css");
    private final Set<Scene> scenes = Collections.newSetFromMap(new WeakHashMap<>());
    private final Set<DialogPane> panes = Collections.newSetFromMap(new WeakHashMap<>());

    public ThemeManager(AppSettings settings) {
        this.settings = settings;
    }

    public void apply(Scene scene) {
        scenes.add(scene);
        restyle(scene.getStylesheets());
    }

    public void apply(DialogPane pane) {
        panes.add(pane);
        restyle(pane.getStylesheets());
    }

    /** Persists {@code theme} and re-styles every scene and dialog styled so far. Call on the FX thread. */
    public void setTheme(AppSettings.Theme theme) {
        settings.setTheme(theme);
        List.copyOf(scenes).forEach(scene -> restyle(scene.getStylesheets()));
        List.copyOf(panes).forEach(pane -> restyle(pane.getStylesheets()));
    }

    private void restyle(ObservableList<String> sheets) {
        sheets.removeAll(baseSheet, darkSheet);
        if (baseSheet != null) sheets.add(baseSheet);
        if (darkSheet != null && settings.getTheme() == AppSettings.Theme.DARK) sheets.add(darkSheet);
    }

    private static String stylesheet(String path) {
        URL url = ThemeManager.class.getResource(path);
        if (url == null) {
            log.warn("Stylesheet {} not found on the classpath", path);
            return null;
        }
        return url.toExternalForm();
    }
}
