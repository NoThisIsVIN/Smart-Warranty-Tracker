package org.example;

import javafx.application.Application;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public class Launcher {
    public static void main(String[] args) {
        Application.launch(MainApp.class, args);
    }
}
