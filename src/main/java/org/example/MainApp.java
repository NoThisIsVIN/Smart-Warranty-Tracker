package org.example;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.h2.tools.Server;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.sql.SQLException;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

public class MainApp extends Application {
    private ConfigurableApplicationContext springContext;
    private Server h2Server;
    private static TrayIcon trayIcon;
    private Stage primaryStage;

    @Override
    public void init() {

        try {
            h2Server = Server.createTcpServer("-tcp", "-tcpAllowOthers", "-ifNotExists").start();
            System.out.println("Started H2 TCP server on port " + h2Server.getPort());
        } catch (SQLException e) {
            System.err.println("Failed to start H2 TCP server: " + e.getMessage());

        }

        springContext = new SpringApplicationBuilder(SmartWarrantyTrackerApplication.class).run();
    }

    @Override
    public void start(Stage stage) throws Exception {
        this.primaryStage = stage;

        setupSystemTray();
        
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
        loader.setControllerFactory(springContext::getBean);
        Parent root = loader.load();
        Scene scene = new Scene(root, 900, 600);

        scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());

        String theme = System.getProperty("app.theme");
        if (theme == null || theme.isBlank()) {
            java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userNodeForPackage(org.example.ui.SettingsController.class);
            theme = prefs.get("theme", "Light");
        }
        if ("Dark".equalsIgnoreCase(theme)) {
            scene.getStylesheets().add(getClass().getResource("/styles/dark.css").toExternalForm());
        }

        org.example.ui.ThemeManager.setPrimaryScene(scene);
        stage.setScene(scene);
        stage.setTitle("The Smart Warranty Tracker");

        stage.setMaximized(true);
        stage.show();

        try {

            Preferences prefs = Preferences.userNodeForPackage(org.example.ui.SettingsController.class);
            int reminderDays = prefs.getInt("reminder.days", 30);

            org.example.service.WarrantyService warrantyService = springContext.getBean(org.example.service.WarrantyService.class);
            java.util.List<org.example.model.Warranty> all = warrantyService.listAll();

            java.util.List<org.example.model.Warranty> soon = all.stream()
                    .filter(w -> {
                        long d = w.getDaysLeft();
                        return d >= 0 && d <= reminderDays;
                    })
                    .collect(Collectors.toList());

            System.out.println("[Notify] reminderDays=" + reminderDays + " candidates=" + soon.size());
            for (org.example.model.Warranty w : soon) {
                System.out.println("[Notify] -> " + w.getProductName() + " expiry=" + w.getExpiryDate() + " daysLeft=" + w.getDaysLeft());
            }
            boolean traySupported = false;
            boolean headless = true;
            try {
                traySupported = SystemTray.isSupported();
                headless = java.awt.GraphicsEnvironment.isHeadless();
            } catch (Throwable t) {

            }
            System.out.println("[Notify] SystemTraySupported=" + traySupported + " Headless=" + headless);
            if (!soon.isEmpty()) {

                for (org.example.model.Warranty w : soon) {
                    String title = "Warranty Reminder";
                    String body = w.getProductName() + 
                                  (w.getModelName() != null && !w.getModelName().isBlank() ? " " + w.getModelName() : "") +
                                  " expires in " + w.getDaysLeft() + " days";

                    sendScreenNotification(title, body, org.example.ui.WindowsNotifier.NotificationType.WARNING);

                    try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                }
            }
        } catch (Throwable t) {

            t.printStackTrace();
        }
    }

    private void setupSystemTray() {
        try {
            if (!SystemTray.isSupported() || java.awt.GraphicsEnvironment.isHeadless()) {
                System.out.println("System tray not supported on this platform");
                return;
            }

            SystemTray tray = SystemTray.getSystemTray();

            java.awt.Image image = null;
            try (java.io.InputStream in = MainApp.class.getResourceAsStream("/images/placeholder.png")) {
                if (in != null) {
                    image = javax.imageio.ImageIO.read(in);
                }
            } catch (Exception ex) {

                image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D g = ((BufferedImage) image).createGraphics();
                g.setColor(new java.awt.Color(52, 152, 219));
                g.fillRect(0, 0, 16, 16);
                g.dispose();
            }

            PopupMenu popup = new PopupMenu();
            
            MenuItem openItem = new MenuItem("Open");
            openItem.addActionListener(e -> javafx.application.Platform.runLater(() -> {
                if (primaryStage != null) {
                    primaryStage.show();
                    primaryStage.toFront();
                }
            }));
            
            MenuItem exitItem = new MenuItem("Exit");
            exitItem.addActionListener(e -> {
                javafx.application.Platform.runLater(() -> {
                    if (primaryStage != null) {
                        primaryStage.close();
                    }
                });
            });
            
            popup.add(openItem);
            popup.addSeparator();
            popup.add(exitItem);

            trayIcon = new TrayIcon(image, "Smart Warranty Tracker", popup);
            trayIcon.setImageAutoSize(true);

            trayIcon.addActionListener(e -> javafx.application.Platform.runLater(() -> {
                if (primaryStage != null) {
                    primaryStage.show();
                    primaryStage.toFront();
                }
            }));
            
            tray.add(trayIcon);
            System.out.println("System tray icon added successfully");
            
        } catch (Exception e) {
            System.err.println("Failed to setup system tray: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void sendNotification(String title, String message, TrayIcon.MessageType messageType) {
        if (trayIcon != null) {
            try {
                trayIcon.displayMessage(title, message, messageType);
                System.out.println("Notification sent: " + title + " - " + message);
            } catch (Exception e) {
                System.err.println("Failed to send notification: " + e.getMessage());
            }
        }
    }

    public static void sendNotification(String title, String message) {
        sendNotification(title, message, TrayIcon.MessageType.INFO);
    }

    public static void sendScreenNotification(String title, String message, org.example.ui.WindowsNotifier.NotificationType type) {
        org.example.ui.WindowsNotifier.notify(title, message, type);
    }

    public static void sendScreenNotification(String title, String message) {
        org.example.ui.WindowsNotifier.notify(title, message);
    }

    @Override
    public void stop() {

        if (trayIcon != null) {
            try {
                SystemTray.getSystemTray().remove(trayIcon);
                System.out.println("System tray icon removed");
            } catch (Exception e) {
                System.err.println("Failed to remove tray icon: " + e.getMessage());
            }
        }
        
        if (springContext != null) {
            springContext.close();
        }
        if (h2Server != null) {
            h2Server.stop();
            System.out.println("Stopped H2 TCP server");
        }
    }
}
