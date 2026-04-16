package org.example.service;

import org.example.model.Warranty;
import org.example.repo.WarrantyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Properties;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.json.JSONObject;

@Service
public class WarrantyService {
    private final WarrantyRepository repo;

    public WarrantyService(WarrantyRepository repo) {
        this.repo = repo;
        loadApiConfig();
    }

    private String apiKey;
    private String apiEndpoint;
    private String searchEngineId;

    private void loadApiConfig() {
        try (InputStream input = getClass().getResourceAsStream("/image-fetch-config.properties")) {
            Properties prop = new Properties();
            prop.load(input);
            apiKey = prop.getProperty("api.key");
            apiEndpoint = prop.getProperty("api.endpoint");
            searchEngineId = prop.getProperty("search.engine.id");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Warranty addWarranty(Warranty w) { return repo.save(w); }

    public List<Warranty> listAll() { return repo.findAll(); }

    public List<Warranty> listAllWithDefaults() {
        List<Warranty> all = repo.findAll();
        for (Warranty w : all) {
            if (w.getImageUrl() == null || w.getImageUrl().isBlank()) {
                try {
                    Path destDir = Paths.get("data", "images");
                    Files.createDirectories(destDir);
                    String ext = ".png";
                    Path dest = destDir.resolve(UUID.randomUUID().toString() + ext);
                    try (InputStream in = getClass().getResourceAsStream("/images/placeholder.png")) {
                        if (in != null) {
                            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
                            String uri = dest.toUri().toString();
                            w.setImageUrl(uri);
                            repo.save(w);
                        }
                    }
                } catch (Exception ex) {

                    ex.printStackTrace();
                }
            }
        }
        return repo.findAll();
    }

    private String fetchImageUrl(String query) {

        String googleResult = fetchFromGoogle(query);
        if (googleResult != null) return googleResult;

        return fetchFromDuckDuckGo(query);
    }
    
    private String fetchFromGoogle(String query) {
        try {
            System.out.println("fetchFromGoogle: Searching for: " + query);
            
            if (apiKey == null || apiKey.isBlank() || searchEngineId == null || searchEngineId.isBlank()) {
                System.out.println("fetchFromGoogle: API key or search engine ID not configured");
                return null;
            }
            
            URI uri = new URI(apiEndpoint + "?q=" + java.net.URLEncoder.encode(query, "UTF-8") +
                              "&key=" + apiKey + "&cx=" + searchEngineId + "&searchType=image&num=1");
            System.out.println("fetchFromGoogle: Request URI: " + uri);
            
            HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            System.out.println("fetchFromGoogle: Response status: " + response.statusCode());
            
            if (response.statusCode() != 200) {
                System.out.println("fetchFromGoogle: Error response: " + response.body());
                return null;
            }

            JSONObject json = new JSONObject(response.body());
            if (!json.has("items") || json.getJSONArray("items").length() == 0) {
                System.out.println("fetchFromGoogle: No items in response");
                return null;
            }
            
            String imageLink = json.getJSONArray("items").getJSONObject(0).getString("link");
            System.out.println("fetchFromGoogle: Found image link: " + imageLink);
            return imageLink;
        } catch (Exception e) {
            System.err.println("fetchFromGoogle: Exception occurred:");
            e.printStackTrace();
            return null;
        }
    }
    
    private String fetchFromDuckDuckGo(String query) {
        try {
            System.out.println("fetchFromDuckDuckGo: Searching for: " + query);

            String searchQuery = java.net.URLEncoder.encode(query + " product image", "UTF-8");
            URI uri = new URI("https://api.duckduckgo.com/?q=" + searchQuery + "&format=json&no_html=1");
            
            HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .header("User-Agent", "Mozilla/5.0")
                .build();
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            System.out.println("fetchFromDuckDuckGo: Response status: " + response.statusCode());
            
            if (response.statusCode() == 200) {
                JSONObject json = new JSONObject(response.body());
                if (json.has("Image") && !json.getString("Image").isEmpty()) {
                    String imageUrl = json.getString("Image");
                    if (!imageUrl.startsWith("http")) {
                        imageUrl = "https://duckduckgo.com" + imageUrl;
                    }
                    System.out.println("fetchFromDuckDuckGo: Found image: " + imageUrl);
                    return imageUrl;
                }
            }
            
            System.out.println("fetchFromDuckDuckGo: No image found, trying Wikipedia/Wikimedia Commons");

            return fetchFromWikipedia(query);
            
        } catch (Exception e) {
            System.err.println("fetchFromDuckDuckGo: Exception occurred:");
            e.printStackTrace();
            return null;
        }
    }
    
    private String fetchFromWikipedia(String query) {
        try {
            System.out.println("fetchFromWikipedia: Searching for: " + query);
            HttpClient client = HttpClient.newHttpClient();

            String searchQuery = java.net.URLEncoder.encode(query, "UTF-8");
            URI searchUri = new URI("https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=" + searchQuery + "&format=json&srlimit=1");
            HttpRequest searchReq = HttpRequest.newBuilder(searchUri)
                .GET().header("User-Agent", "WarrantyTracker/1.0").build();
            HttpResponse<String> searchResp = client.send(searchReq, HttpResponse.BodyHandlers.ofString());
            if (searchResp.statusCode() != 200) return null;
            JSONObject searchJson = new JSONObject(searchResp.body());
            if (!searchJson.has("query") || searchJson.getJSONObject("query").getJSONArray("search").isEmpty()) return null;
            String title = searchJson.getJSONObject("query").getJSONArray("search").getJSONObject(0).getString("title");

            String titleEnc = java.net.URLEncoder.encode(title, "UTF-8");
            URI pageUri = new URI("https://en.wikipedia.org/w/api.php?action=query&format=json&prop=pageimages&piprop=original&titles=" + titleEnc);
            HttpRequest pageReq = HttpRequest.newBuilder(pageUri)
                .GET().header("User-Agent", "WarrantyTracker/1.0").build();
            HttpResponse<String> pageResp = client.send(pageReq, HttpResponse.BodyHandlers.ofString());
            if (pageResp.statusCode() != 200) return null;
            JSONObject pageJson = new JSONObject(pageResp.body());
            JSONObject pages = pageJson.getJSONObject("query").getJSONObject("pages");
            String firstKey = pages.keys().next();
            JSONObject page = pages.getJSONObject(firstKey);
            if (page.has("original")) {
                String imageUrl = page.getJSONObject("original").getString("source");
                System.out.println("fetchFromWikipedia: Found image: " + imageUrl + " (title: " + title + ")");
                return imageUrl;
            }
        } catch (Exception e) {
            System.err.println("fetchFromWikipedia: Exception occurred:");
            e.printStackTrace();
        }
        return null;
    }

    public List<Warranty> listAllWithImageFetch() {
        List<Warranty> all = repo.findAll();
        for (Warranty w : all) {
            if (w.getImageUrl() == null || w.getImageUrl().isBlank()) {
                try {
                    String query = w.getProductName() + " " + (w.getModelName() == null ? "" : w.getModelName());
                    String imageUrl = fetchImageUrl(query);
                    if (imageUrl != null) {
                        Path destDir = Paths.get("data", "images");
                        Files.createDirectories(destDir);
                        String ext = ".jpg";
                        Path dest = destDir.resolve(UUID.randomUUID().toString() + ext);
                        try (InputStream in = URI.create(imageUrl).toURL().openStream()) {
                            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
                            w.setImageUrl(dest.toUri().toString());
                            repo.save(w);
                        }
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
        return repo.findAll();
    }

    @Transactional
    public Warranty ensureProductImage(Warranty w, boolean overwriteIfExists) {
        if (w == null) return null;
        if (!overwriteIfExists && w.getImageUrl() != null && !w.getImageUrl().isBlank()) {
            return w;
        }
        try {
            String query = buildImageQuery(w);
            if (query.isBlank()) {
                System.out.println("ensureProductImage: query is blank, skipping");
                return w;
            }
            System.out.println("ensureProductImage: Fetching image for query: " + query);
            String remote = fetchImageUrl(query);
            if (remote == null) {
                System.out.println("ensureProductImage: fetchImageUrl returned null");
                return w;
            }
            System.out.println("ensureProductImage: Got remote URL: " + remote);
            Path destDir = Paths.get("data", "images");
            Files.createDirectories(destDir);
            String ext = ".jpg";
            Path dest = destDir.resolve(UUID.randomUUID().toString() + ext);
            try (InputStream in = URI.create(remote).toURL().openStream()) {
                Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
                w.setImageUrl(dest.toUri().toString());
                System.out.println("ensureProductImage: Saved image to: " + dest.toUri().toString());
                return repo.save(w);
            }
        } catch (Exception ex) {
            System.err.println("ensureProductImage: Exception occurred:");
            ex.printStackTrace();
            return w;
        }
    }

    private String buildImageQuery(Warranty w) {
        String brand = w.getRetailer() == null ? "" : w.getRetailer();
        String product = w.getProductName() == null ? "" : w.getProductName();
        String model = w.getModelName() == null ? "" : w.getModelName();

        String base = (brand + " " + product + " " + model).trim();
        if (base.isBlank()) return "";

        base = base.replaceAll("\\b[A-Z0-9]{8,}\\b", "").replaceAll("\\s+", " ").trim();

        String lower = base.toLowerCase();
        if (lower.contains("ipad") && lower.contains("air")) {

            String size = lower.contains("11") ? " 11" : lower.contains("13") ? " 13" : "";

            String gen = "";
            try {
                if (w.getPurchaseDate() != null && w.getPurchaseDate().getYear() >= 2024) gen = " M3";
            } catch (Exception ignore) {}
            base = "iPad Air" + size + gen;
        } else if (lower.contains("ipad pro")) {
            base = "iPad Pro";
        } else if (lower.contains("iphone")) {
            base = "iPhone";
        } else if (lower.contains("macbook")) {
            base = "MacBook";
        }

        base = base + " product image";
        return base.trim();
    }

    public Optional<Warranty> findById(Long id) { return repo.findById(id); }

    public void delete(Long id) { repo.deleteById(id); }

    public void deleteWarranty(Long id) {
        delete(id);
    }

    public List<Warranty> expiringBefore(LocalDate date) { return repo.findByPurchaseDateBefore(date); }
}
