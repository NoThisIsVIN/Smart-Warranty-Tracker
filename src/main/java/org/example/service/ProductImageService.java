package org.example.service;

import jakarta.annotation.PreDestroy;
import org.example.config.ApiKeys;
import org.example.model.Warranty;
import org.example.repo.WarrantyRepository;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

/** Finds and stores a product photo for warranties that have none. All network work runs on one daemon thread. */
@Service
public class ProductImageService {
    private static final Logger log = LoggerFactory.getLogger(ProductImageService.class);

    private static final String DEFAULT_GOOGLE_ENDPOINT = "https://www.googleapis.com/customsearch/v1";
    private static final String DUCKDUCKGO_ENDPOINT = "https://api.duckduckgo.com/";
    private static final String WIKIPEDIA_ENDPOINT = "https://en.wikipedia.org/w/api.php";
    private static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;
    /** Stores rather than manufacturers; they don't help an image search. */
    private static final Set<String> STORES = Set.of("amazon", "flipkart", "best buy", "walmart", "target", "costco",
            "croma", "reliance digital", "ebay", "newegg");
    private static final Map<String, String> IMAGE_EXTENSIONS = Map.of(
            "image/jpeg", ".jpg", "image/jpg", ".jpg", "image/png", ".png", "image/gif", ".gif",
            "image/webp", ".webp", "image/bmp", ".bmp");

    private record Job(Long id, List<String> queries) {}

    private final WarrantyRepository repo;
    private final FileStorage files;
    private final ApiKeys apiKeys;
    private final Set<Long> attempted = ConcurrentHashMap.newKeySet();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "product-image-fetch");
        t.setDaemon(true);
        return t;
    });

    public ProductImageService(WarrantyRepository repo, FileStorage files, ApiKeys apiKeys) {
        this.repo = repo;
        this.files = files;
        this.apiKeys = apiKeys;
    }

    /**
     * For each warranty with no image that hasn't been tried this session: fetch on a background daemon executor
     * (Google Custom Search if keys set, then DuckDuckGo instant answer, then Wikipedia page images), download,
     * store via {@link FileStorage}, persist via {@code repo.updateImageUrl}, then call {@code onUpdated} with the
     * reloaded warranty ON THE BACKGROUND THREAD (callers wrap UI work in {@code Platform.runLater}).
     */
    public void fetchMissingAsync(Collection<Warranty> warranties, Consumer<Warranty> onUpdated) {
        List<Job> jobs = new ArrayList<>();
        for (Warranty w : warranties) {
            if (w.getId() == null || !isBlank(w.getImageUrl())) continue;
            List<String> queries = queriesFor(w);
            if (!queries.isEmpty() && attempted.add(w.getId())) jobs.add(new Job(w.getId(), queries));
        }
        if (jobs.isEmpty()) return;
        try {
            executor.execute(() -> jobs.forEach(job -> run(job, onUpdated)));
        } catch (RejectedExecutionException e) {
            log.debug("Image fetch skipped: executor is shut down");
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    /** Product + model (serial-like tokens removed) + manufacturer, then a broader product-only query. */
    static List<String> queriesFor(Warranty w) {
        String product = clean(w.getProductName());
        String model = stripSerials(clean(w.getModelName()));
        String retailer = clean(w.getRetailer());
        String lowerProduct = product.toLowerCase(Locale.ROOT);
        String brand = retailer.isEmpty() || STORES.contains(retailer.toLowerCase(Locale.ROOT))
                || lowerProduct.contains(retailer.toLowerCase(Locale.ROOT)) ? "" : retailer;
        if (!model.isEmpty() && lowerProduct.contains(model.toLowerCase(Locale.ROOT))) model = "";

        Set<String> queries = new LinkedHashSet<>();
        addQuery(queries, brand, product, model);
        if (!product.isEmpty()) addQuery(queries, brand, product, "");
        return List.copyOf(queries);
    }

    private void run(Job job, Consumer<Warranty> onUpdated) {
        if (Thread.currentThread().isInterrupted()) return;
        try {
            Optional<String> stored = Optional.empty();
            for (String query : job.queries()) {
                stored = findAndStore(query);
                if (stored.isPresent()) break;
            }
            if (stored.isEmpty()) {
                log.info("No product image found for warranty {}", job.id());
                return;
            }
            if (repo.updateImageUrl(job.id(), stored.get()) == 0) {
                files.deleteIfOwned(stored.get()); // warranty was deleted meanwhile
                return;
            }
            repo.findById(job.id()).ifPresent(updated -> {
                try {
                    onUpdated.accept(updated);
                } catch (RuntimeException e) {
                    log.warn("Image update callback failed", e);
                }
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            log.warn("Image fetch for warranty {} failed: {}", job.id(), e.toString());
        }
    }

    private Optional<String> findAndStore(String query) throws InterruptedException {
        for (String candidate : candidates(query)) {
            Optional<String> stored = download(candidate);
            if (stored.isPresent()) return stored;
        }
        return Optional.empty();
    }

    private List<String> candidates(String query) throws InterruptedException {
        List<String> urls = new ArrayList<>(googleImages(query));
        if (urls.isEmpty()) urls.addAll(duckDuckGo(query));
        if (urls.isEmpty()) urls.addAll(wikipedia(query));
        return urls;
    }

    private List<String> googleImages(String query) throws InterruptedException {
        if (!apiKeys.has(ApiKeys.GOOGLE_SEARCH_KEY) || !apiKeys.has(ApiKeys.GOOGLE_SEARCH_ENGINE_ID)) return List.of();
        String endpoint = apiKeys.has(ApiKeys.GOOGLE_SEARCH_ENDPOINT)
                ? apiKeys.get(ApiKeys.GOOGLE_SEARCH_ENDPOINT) : DEFAULT_GOOGLE_ENDPOINT;
        Map<String, String> params = new LinkedHashMap<>();
        params.put("q", query);
        params.put("key", apiKeys.get(ApiKeys.GOOGLE_SEARCH_KEY));
        params.put("cx", apiKeys.get(ApiKeys.GOOGLE_SEARCH_ENGINE_ID));
        params.put("searchType", "image");
        params.put("safe", "active");
        params.put("num", "3");
        JSONObject json = getJson("Google image search", Http.uri(endpoint, params));
        List<String> urls = new ArrayList<>();
        JSONArray items = json == null ? null : json.optJSONArray("items");
        for (int i = 0; items != null && i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item != null && !item.optString("link").isEmpty()) urls.add(item.optString("link"));
        }
        return urls;
    }

    private List<String> duckDuckGo(String query) throws InterruptedException {
        JSONObject json = getJson("DuckDuckGo", Http.uri(DUCKDUCKGO_ENDPOINT,
                Map.of("q", query, "format", "json", "no_html", "1", "skip_disambig", "1")));
        String image = json == null ? "" : json.optString("Image");
        if (image.isEmpty()) return List.of();
        return List.of(image.startsWith("http") ? image : "https://duckduckgo.com" + image);
    }

    private List<String> wikipedia(String query) throws InterruptedException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("action", "query");
        params.put("format", "json");
        params.put("generator", "search");
        params.put("gsrsearch", query);
        params.put("gsrlimit", "3");
        params.put("prop", "pageimages");
        params.put("piprop", "thumbnail");
        params.put("pithumbsize", "640");
        JSONObject json = getJson("Wikipedia", Http.uri(WIKIPEDIA_ENDPOINT, params));
        JSONObject pages = json == null || json.optJSONObject("query") == null
                ? null : json.getJSONObject("query").optJSONObject("pages");
        if (pages == null) return List.of();

        // Pages come back keyed by id; "index" is the search rank.
        String best = null;
        int bestIndex = Integer.MAX_VALUE;
        for (String key : pages.keySet()) {
            JSONObject page = pages.optJSONObject(key);
            JSONObject thumb = page == null ? null : page.optJSONObject("thumbnail");
            int index = page == null ? Integer.MAX_VALUE : page.optInt("index", Integer.MAX_VALUE - 1);
            if (thumb != null && !thumb.optString("source").isEmpty() && index < bestIndex) {
                best = thumb.optString("source");
                bestIndex = index;
            }
        }
        return best == null ? List.of() : List.of(best);
    }

    /** GETs JSON; logs only the source name and status, never the URI (it may carry an API key). */
    private JSONObject getJson(String source, URI uri) throws InterruptedException {
        try {
            HttpResponse<String> resp = Http.client().send(
                    Http.request(uri).header("Accept", "application/json").GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.info("{} returned HTTP {}", source, resp.statusCode());
                return null;
            }
            return new JSONObject(resp.body());
        } catch (IOException | JSONException e) {
            log.info("{} lookup failed: {}", source, e.getClass().getSimpleName());
            return null;
        }
    }

    private Optional<String> download(String url) throws InterruptedException {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) {
            return Optional.empty();
        }
        try {
            HttpResponse<byte[]> resp = Http.client().send(
                    Http.request(uri).header("Accept", "image/*").GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            String type = resp.headers().firstValue("Content-Type").orElse("")
                    .split(";")[0].trim().toLowerCase(Locale.ROOT);
            String ext = IMAGE_EXTENSIONS.get(type);
            byte[] body = resp.body();
            if (resp.statusCode() != 200 || ext == null || body.length == 0 || body.length > MAX_IMAGE_BYTES) {
                log.info("Skipping image from {}: HTTP {}, type '{}'", uri.getHost(), resp.statusCode(), type);
                return Optional.empty();
            }
            return Optional.of(files.saveBytes(body, ext, FileStorage.Kind.IMAGE));
        } catch (IOException | IllegalArgumentException e) {
            log.info("Image download from {} failed: {}", uri.getHost(), e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private static void addQuery(Set<String> queries, String... parts) {
        String q = String.join(" ", parts).trim().replaceAll("\\s+", " ");
        if (!q.isEmpty()) queries.add(q);
    }

    /** Drops serial-number-like tokens (long runs of capitals and digits) that only confuse a search. */
    private static String stripSerials(String model) {
        StringBuilder out = new StringBuilder();
        for (String token : model.split("\\s+")) {
            boolean serialLike = token.length() > 16
                    || (token.matches("[A-Z0-9]{8,}") && token.matches(".*\\d.*"));
            if (!serialLike && !token.isEmpty()) out.append(out.isEmpty() ? "" : " ").append(token);
        }
        return out.toString();
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
