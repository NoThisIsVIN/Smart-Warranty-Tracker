package org.example.service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

/** Shared HTTP client with sane timeouts and a User-Agent (some hosts, e.g. Wikimedia, reject requests without one). */
public final class Http {
    public static final String USER_AGENT = "SmartWarrantyTracker/1.0";

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private Http() {}

    public static HttpClient client() {
        return CLIENT;
    }

    /** Request builder with a 20 s timeout and the app User-Agent. */
    public static HttpRequest.Builder request(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", USER_AGENT);
    }

    /** {@code base?k1=v1&k2=v2} with every key and value URL-encoded. */
    public static URI uri(String base, Map<String, String> params) {
        String query = params.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
        return URI.create(query.isEmpty() ? base : base + (base.contains("?") ? "&" : "?") + query);
    }

    public static String encode(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }
}
