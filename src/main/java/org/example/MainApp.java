package org.example;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.example.config.AppSettings;
import org.example.model.Warranty;
import org.example.ui.Browser;
import org.example.ui.Dialogs;
import org.example.ui.MainController;
import org.example.ui.Notifier;
import org.example.ui.ThemeManager;
import org.example.ui.ViewLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.time.LocalDate;
import java.util.List;

/** JavaFX entry point: starts Spring in {@link #init()}, shows the main window, closes Spring on exit. */
public class MainApp extends Application {
    private static final Logger log = LoggerFactory.getLogger(MainApp.class);

    private ConfigurableApplicationContext context;
    private RuntimeException startupError;

    @Override
    public void init() {
        try {
            context = new SpringApplicationBuilder(SmartWarrantyTrackerApplication.class)
                    .headless(false)
                    .run(getParameters().getRaw().toArray(String[]::new));
        } catch (RuntimeException e) {
            log.error("Application context failed to start", e);
            startupError = e;
        }
    }

    @Override
    public void start(Stage stage) {
        Browser.setHostServices(getHostServices());
        if (startupError != null) {
            showStartupError();
            Platform.exit();
            return;
        }

        ThemeManager themeManager = context.getBean(ThemeManager.class);
        Dialogs.setThemeManager(themeManager);
        Notifier.setThemeManager(themeManager);

        ViewLoader.View<MainController> view = context.getBean(ViewLoader.class).load("/fxml/main.fxml");
        Scene scene = new Scene(view.root(), 1280, 800);
        themeManager.apply(scene);
        stage.setScene(scene);
        stage.setTitle("Warranty Tracker");
        stage.setMinWidth(960);
        stage.setMinHeight(640);
        stage.setMaximized(true);
        stage.show();
        Notifier.setOwner(stage);

        announceExpiring(view.controller().expiringSoon(), context.getBean(AppSettings.class).getReminderDays());
    }

    @Override
    public void stop() {
        if (context != null) context.close();
    }

    /** One summary toast for everything inside the reminder window. */
    private static void announceExpiring(List<Warranty> soon, int reminderDays) {
        if (soon.isEmpty()) return;
        String body;
        if (soon.size() == 1) {
            Warranty w = soon.get(0);
            long days = w.daysLeft(LocalDate.now());
            String when = days == 0 ? "today" : days == 1 ? "tomorrow" : "in " + days + " days";
            body = w.getDisplayName() + " expires " + when + ".";
        } else {
            body = soon.size() + " warranties expire within " + reminderDays + " days.";
        }
        Notifier.show("Expiring soon", body, Notifier.Type.WARNING);
    }

    private void showStartupError() {
        // Spring isn't available, but the theme only needs the stored preference.
        Dialogs.setThemeManager(new ThemeManager(new AppSettings()));
        String message = isDatabaseLocked(startupError)
                ? "Warranty Tracker is already running. Switch to the open window, or close it and try again."
                : "Something went wrong while starting up: " + rootCause(startupError).getMessage()
                        + "\n\nSee the log for details.";
        Dialogs.error(null, "Warranty Tracker couldn't start", message);
    }

    private static boolean isDatabaseLocked(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            String message = t.getMessage();
            if (message != null && (message.contains("already in use") || message.contains("locked by another process"))) {
                return true;
            }
        }
        return false;
    }

    private static Throwable rootCause(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        return root;
    }
}
