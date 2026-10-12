package org.example;

import javafx.application.Application;

/** Plain entry point so the shaded jar starts without JavaFX module-path checks on {@link MainApp}. */
public class Launcher {
    public static void main(String[] args) {
        Application.launch(MainApp.class, args);
    }
}
