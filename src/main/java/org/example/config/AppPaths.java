package org.example.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Where the app keeps its database, images, receipts, API keys and logs. Defaults to
 * {@code %APPDATA%\SmartWarrantyTracker} on Windows and {@code ~/.smart-warranty-tracker} elsewhere,
 * so it works when installed to a read-only folder. Override with {@code -Dswt.data.dir=<path>}.
 */
public final class AppPaths {
    public static final String DATA_DIR_PROPERTY = "swt.data.dir";

    private static final Logger log = LoggerFactory.getLogger(AppPaths.class);
    private static final String DB_FILE = "smartwarranty.mv.db";
    private static final Path DATA_DIR = resolveDataDir();

    private AppPaths() {
    }

    public static Path dataDir() {
        return DATA_DIR;
    }

    /**
     * Copies data from the old location ({@code ./data} next to where the app was started) into
     * {@link #dataDir()} the first time the new location has no database. The old folder is left untouched.
     */
    public static void migrateLegacyData() {
        Path legacy = Path.of("data").toAbsolutePath().normalize();
        if (legacy.equals(DATA_DIR) || Files.exists(DATA_DIR.resolve(DB_FILE)) || !Files.isRegularFile(legacy.resolve(DB_FILE))) {
            return;
        }
        try {
            copyTree(legacy, DATA_DIR);
            log.info("Copied existing data from {} to {}", legacy, DATA_DIR);
        } catch (IOException e) {
            log.warn("Could not copy existing data from {}: {}", legacy, e.getMessage());
        }
    }

    private static Path resolveDataDir() {
        String override = System.getProperty(DATA_DIR_PROPERTY);
        if (override != null && !override.isBlank()) return Path.of(override).toAbsolutePath().normalize();

        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isBlank()) return Path.of(appData, "SmartWarrantyTracker");
        return Path.of(System.getProperty("user.home"), ".smart-warranty-tracker");
    }

    private static void copyTree(Path from, Path to) throws IOException {
        Files.walkFileTree(from, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(to.resolve(from.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, to.resolve(from.relativize(file).toString()), StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
