package org.example.ui;

import javafx.application.HostServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;

/** Opens web links and local files with the system default handler. */
public final class Browser {
    private static final Logger log = LoggerFactory.getLogger(Browser.class);
    private static volatile HostServices hostServices;

    private Browser() {}

    public static void setHostServices(HostServices services) {
        hostServices = services;
    }

    /** Opens an http(s) URL, a {@code file:} URI or a plain file path; anything else is ignored. */
    public static void open(String uriOrUrl) {
        if (uriOrUrl == null || uriOrUrl.isBlank()) return;
        String target = toDocument(uriOrUrl.trim());
        if (target == null) {
            log.warn("Refusing to open unsupported link: {}", uriOrUrl);
            return;
        }
        HostServices services = hostServices;
        if (services == null) {
            log.warn("Cannot open {}: host services not available", target);
            return;
        }
        services.showDocument(target);
    }

    private static String toDocument(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("file:")) return value;
        // Any other scheme (mailto:, javascript:, ...) is rejected; single-letter "C:" drive paths fall through.
        if (lower.matches("^[a-z][a-z0-9+.-]+:.*")) return null;
        try {
            return Path.of(value).toAbsolutePath().toUri().toString();
        } catch (InvalidPathException e) {
            return null;
        }
    }
}
