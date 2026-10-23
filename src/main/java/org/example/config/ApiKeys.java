package org.example.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Optional third-party API keys. Read from {@code config/image-fetch-config.properties} in {@link AppPaths#dataDir()}
 * (written by the Settings dialog), falling back to the bundled classpath file.
 */
@Component
public class ApiKeys {
    public static final String GOOGLE_SEARCH_KEY = "api.key";
    public static final String GOOGLE_SEARCH_ENGINE_ID = "search.engine.id";
    public static final String GOOGLE_SEARCH_ENDPOINT = "api.endpoint";
    public static final String GOOGLE_VISION_KEY = "google.vision.api.key";
    public static final String OCR_SPACE_KEY = "ocr.api.key";

    private static final Logger log = LoggerFactory.getLogger(ApiKeys.class);
    private static final Path FILE = AppPaths.dataDir().resolve("config").resolve("image-fetch-config.properties");
    private static final String CLASSPATH_FILE = "/image-fetch-config.properties";

    private volatile Properties props;

    public ApiKeys() {
        reload();
    }

    /** Value for {@code key}, trimmed; empty string when unset. */
    public String get(String key) {
        String v = props.getProperty(key);
        return v == null ? "" : v.trim();
    }

    public boolean has(String key) {
        return !get(key).isEmpty();
    }

    /** Merges {@code values} into the external config file and reloads. */
    public synchronized void save(Properties values) throws IOException {
        Properties out = new Properties();
        if (Files.exists(FILE)) {
            try (InputStream in = Files.newInputStream(FILE)) { out.load(in); }
        }
        values.forEach((k, v) -> out.setProperty((String) k, v == null ? "" : v.toString().trim()));
        Files.createDirectories(FILE.getParent());
        try (OutputStream os = Files.newOutputStream(FILE)) {
            out.store(os, "Smart Warranty Tracker API keys");
        }
        reload();
    }

    private synchronized void reload() {
        Properties p = new Properties();
        try (InputStream in = ApiKeys.class.getResourceAsStream(CLASSPATH_FILE)) {
            if (in != null) p.load(in);
        } catch (IOException e) {
            log.warn("Could not read bundled API config: {}", e.getMessage());
        }
        if (Files.exists(FILE)) {
            try (InputStream in = Files.newInputStream(FILE)) {
                Properties external = new Properties();
                external.load(in);
                external.forEach((k, v) -> { if (!v.toString().isBlank()) p.put(k, v); });
            } catch (IOException e) {
                log.warn("Could not read {}: {}", FILE, e.getMessage());
            }
        }
        props = p;
    }
}
