package org.example.ui;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;

/** Loads FXML views whose controllers are created (and constructor-injected) by Spring, and builds themed modal stages. */
@Component
public class ViewLoader {
    public record View<T>(Parent root, T controller) {}

    private final AutowireCapableBeanFactory beanFactory;
    private final ThemeManager themeManager;

    public ViewLoader(ApplicationContext context, ThemeManager themeManager) {
        this.beanFactory = context.getAutowireCapableBeanFactory();
        this.themeManager = themeManager;
    }

    public <T> View<T> load(String fxmlPath) {
        URL url = ViewLoader.class.getResource(fxmlPath);
        if (url == null) throw new IllegalArgumentException("FXML not found: " + fxmlPath);
        FXMLLoader loader = new FXMLLoader(url);
        loader.setControllerFactory(beanFactory::createBean);
        try {
            Parent root = loader.load();
            return new View<>(root, loader.getController());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load " + fxmlPath, e);
        }
    }

    public Stage modal(String title, Parent root, Window owner) {
        Stage stage = new Stage();
        stage.setTitle(title);
        stage.initModality(Modality.WINDOW_MODAL);
        if (owner != null) stage.initOwner(owner);
        Scene scene = new Scene(root);
        themeManager.apply(scene);
        stage.setScene(scene);
        return stage;
    }
}
