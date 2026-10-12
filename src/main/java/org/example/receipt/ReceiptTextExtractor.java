package org.example.receipt;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.example.config.ApiKeys;
import org.example.service.FileStorage;
import org.example.service.Http;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Pulls plain text out of receipt files. All methods block; call them off the JavaFX thread. */
@Component
public class ReceiptTextExtractor {
    private static final Logger log = LoggerFactory.getLogger(ReceiptTextExtractor.class);

    private static final Set<String> IMAGE_EXTENSIONS = Set.of(".png", ".jpg", ".jpeg", ".gif", ".bmp", ".webp");
    private static final String OCR_SPACE_DEMO_KEY = "helloworld";
    private static final String VISION_ENDPOINT = "https://vision.googleapis.com/v1/images:annotate";
    private static final String OCR_SPACE_ENDPOINT = "https://api.ocr.space/parse/image";

    private final ApiKeys apiKeys;

    public ReceiptTextExtractor(ApiKeys apiKeys) {
        this.apiKeys = apiKeys;
    }

    public static boolean isSupported(String fileName) {
        String ext = FileStorage.extension(fileName);
        return IMAGE_EXTENSIONS.contains(ext) || ext.equals(".pdf") || ext.equals(".docx");
    }

    /** Text of a PDF, DOCX or image (OCR); "" when nothing could be extracted. */
    public String extractText(Path file) throws IOException {
        String ext = FileStorage.extension(file.getFileName().toString());
        String text;
        if (ext.equals(".pdf")) {
            text = extractPdf(file);
        } else if (ext.equals(".docx")) {
            text = extractDocx(file);
        } else if (IMAGE_EXTENSIONS.contains(ext)) {
            text = extractImage(file, ext);
        } else {
            throw new IOException("Unsupported file type: " + file.getFileName());
        }
        return text == null ? "" : text.trim();
    }

    public Optional<BufferedImage> renderFirstPage(Path pdf) {
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            if (doc.getNumberOfPages() == 0) return Optional.empty();
            return Optional.of(new PDFRenderer(doc).renderImageWithDPI(0, 110));
        } catch (IOException | RuntimeException e) {
            log.warn("Could not render first page of {}: {}", pdf.getFileName(), e.getMessage());
            return Optional.empty();
        }
    }

    private String extractPdf(Path file) throws IOException {
        try (PDDocument doc = Loader.loadPDF(file.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(doc);
        }
    }

    private String extractDocx(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file);
             XWPFDocument doc = new XWPFDocument(in);
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return extractor.getText();
        }
    }

    private String extractImage(Path file, String ext) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        if (apiKeys.has(ApiKeys.GOOGLE_VISION_KEY)) {
            String text = googleVision(bytes);
            if (text != null && !text.isBlank()) return text;
        }
        return ocrSpace(bytes, mimeType(ext));
    }

    private String googleVision(byte[] bytes) {
        JSONObject request = new JSONObject()
                .put("image", new JSONObject().put("content", Base64.getEncoder().encodeToString(bytes)))
                .put("features", new JSONArray().put(new JSONObject().put("type", "TEXT_DETECTION")))
                .put("imageContext", new JSONObject().put("languageHints", new JSONArray().put("en")));
        JSONObject payload = new JSONObject().put("requests", new JSONArray().put(request));
        URI uri = Http.uri(VISION_ENDPOINT, Map.of("key", apiKeys.get(ApiKeys.GOOGLE_VISION_KEY)));
        try {
            HttpResponse<String> resp = Http.client().send(
                    Http.request(uri)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.warn("Google Vision OCR returned HTTP {}; falling back to OCR.space", resp.statusCode());
                return null;
            }
            JSONArray responses = new JSONObject(resp.body()).optJSONArray("responses");
            JSONObject first = responses == null ? null : responses.optJSONObject(0);
            if (first == null) return null;
            JSONObject full = first.optJSONObject("fullTextAnnotation");
            if (full != null && !full.optString("text").isBlank()) return full.optString("text");
            JSONArray annotations = first.optJSONArray("textAnnotations");
            JSONObject annotation = annotations == null ? null : annotations.optJSONObject(0);
            return annotation == null ? null : annotation.optString("description", null);
        } catch (IOException | JSONException e) {
            log.warn("Google Vision OCR failed: {}", e.getMessage());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private String ocrSpace(byte[] bytes, String mime) throws IOException {
        String key = apiKeys.has(ApiKeys.OCR_SPACE_KEY) ? apiKeys.get(ApiKeys.OCR_SPACE_KEY) : OCR_SPACE_DEMO_KEY;
        String dataUri = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
        String form = "apikey=" + Http.encode(key)
                + "&language=eng&isTable=true&scale=true&OCREngine=2&detectOrientation=true"
                + "&base64Image=" + Http.encode(dataUri);
        try {
            HttpResponse<String> resp = Http.client().send(
                    Http.request(URI.create(OCR_SPACE_ENDPOINT))
                            .timeout(Duration.ofSeconds(60))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(form))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new IOException("OCR service returned HTTP " + resp.statusCode());
            }
            JSONObject json = new JSONObject(resp.body());
            JSONArray results = json.optJSONArray("ParsedResults");
            if (results == null || results.isEmpty()) {
                Object error = json.opt("ErrorMessage");
                if (error != null) log.warn("OCR.space error: {}", error);
                return "";
            }
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < results.length(); i++) {
                text.append(results.getJSONObject(i).optString("ParsedText")).append('\n');
            }
            return text.toString();
        } catch (JSONException e) {
            throw new IOException("Unexpected OCR response", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("OCR interrupted", e);
        }
    }

    private static String mimeType(String ext) {
        return switch (ext) {
            case ".png" -> "image/png";
            case ".gif" -> "image/gif";
            case ".bmp" -> "image/bmp";
            case ".webp" -> "image/webp";
            default -> "image/jpeg";
        };
    }
}
