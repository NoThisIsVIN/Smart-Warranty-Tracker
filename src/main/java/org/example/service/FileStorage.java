package org.example.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Owns the app's file area: {@code data/images} and {@code data/receipts} relative to the working directory.
 * Stored references are absolute file URI strings.
 */
@Component
public class FileStorage {
    public enum Kind {
        IMAGE("images"), RECEIPT("receipts");

        private final String dirName;

        Kind(String dirName) { this.dirName = dirName; }
    }

    private static final Logger log = LoggerFactory.getLogger(FileStorage.class);

    private final Path baseDir;

    public FileStorage() {
        this(Path.of("data"));
    }

    FileStorage(Path baseDir) {
        this.baseDir = baseDir.toAbsolutePath().normalize();
    }

    /** Copies {@code source} to {@code data/<dir>/<uuid><.ext>} and returns its file URI. */
    public String importFile(Path source, Kind kind) throws IOException {
        Path dest = newFile(kind, extension(source.getFileName().toString()));
        Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
        return dest.toUri().toString();
    }

    public String savePng(BufferedImage img, Kind kind) throws IOException {
        Path dest = newFile(kind, ".png");
        if (!ImageIO.write(img, "png", dest.toFile())) {
            Files.deleteIfExists(dest);
            throw new IOException("No PNG writer available");
        }
        return dest.toUri().toString();
    }

    public String saveBytes(byte[] bytes, String extWithDot, Kind kind) throws IOException {
        Path dest = newFile(kind, extWithDot == null ? "" : extWithDot.toLowerCase(Locale.ROOT));
        Files.write(dest, bytes);
        return dest.toUri().toString();
    }

    /**
     * The existing file for a stored value (file URI or plain path). If the recorded location no longer exists
     * (e.g. the app folder was moved), looks for a file with the same name in {@code data/images} or
     * {@code data/receipts}. Never throws.
     */
    public Optional<Path> resolve(String uriOrPath) {
        if (uriOrPath == null || uriOrPath.isBlank()) return Optional.empty();
        String value = uriOrPath.trim();
        Path direct = toPath(value);
        if (direct != null && Files.isRegularFile(direct)) return Optional.of(direct.toAbsolutePath().normalize());

        String fileName = fileName(value, direct);
        if (fileName == null) return Optional.empty();
        for (Kind kind : Kind.values()) {
            Path candidate = dir(kind).resolve(fileName);
            if (Files.isRegularFile(candidate)) return Optional.of(candidate);
        }
        return Optional.empty();
    }

    /** Deletes the referenced file, but only if it lives inside {@code data/images} or {@code data/receipts}. */
    public void deleteIfOwned(String uriOrPath) {
        resolve(uriOrPath).ifPresent(path -> {
            try {
                Path real = path.toRealPath();
                if (!isOwned(real)) {
                    log.warn("Not deleting {}: outside the app data folders", real);
                    return;
                }
                Files.deleteIfExists(real);
                log.info("Deleted {}", real.getFileName());
            } catch (IOException e) {
                log.warn("Could not delete {}: {}", path, e.getMessage());
            }
        });
    }

    /** Lower-case extension including the dot (".pdf"), or "" when there is none. */
    public static String extension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) return "";
        String ext = fileName.substring(dot).toLowerCase(Locale.ROOT);
        return ext.matches("\\.[a-z0-9]{1,8}") ? ext : "";
    }

    private Path newFile(Kind kind, String ext) throws IOException {
        Path dir = dir(kind);
        Files.createDirectories(dir);
        String safeExt = ext.matches("|\\.[a-z0-9]{1,8}") ? ext : "";
        return dir.resolve(UUID.randomUUID() + safeExt);
    }

    private Path dir(Kind kind) {
        return baseDir.resolve(kind.dirName);
    }

    private boolean isOwned(Path real) {
        for (Kind kind : Kind.values()) {
            Path dir = dir(kind);
            try {
                if (Files.isDirectory(dir)) dir = dir.toRealPath();
            } catch (IOException e) {
                continue;
            }
            if (real.startsWith(dir) && !real.equals(dir)) return true;
        }
        return false;
    }

    private static Path toPath(String value) {
        try {
            if (value.regionMatches(true, 0, "file:", 0, 5)) return Path.of(URI.create(value));
            if (value.contains("://")) return null;
            return Path.of(value);
        } catch (IllegalArgumentException | java.nio.file.FileSystemNotFoundException e) { // incl. InvalidPathException
            return null;
        }
    }

    /** Bare file name of a stored value, or null when it has none or looks unsafe. */
    private static String fileName(String value, Path parsed) {
        String name;
        if (parsed != null && parsed.getFileName() != null) {
            name = parsed.getFileName().toString();
        } else {
            String s = value.replace('\\', '/');
            int q = s.indexOf('?');
            if (q >= 0) s = s.substring(0, q);
            name = s.substring(s.lastIndexOf('/') + 1);
        }
        if (name.isBlank() || name.equals(".") || name.equals("..") || name.contains(":")) return null;
        return name;
    }
}
