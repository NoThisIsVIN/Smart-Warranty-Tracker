package org.example.recommender;

import org.example.model.Warranty;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

/** Builds protection-plan and support links for a warranty, routed to the manufacturer's site when it is known. */
@Component
public class SimpleRecommender {

    private record Brand(String key, String displayName, Pattern pattern) {
        static Brand of(String key, String displayName, String keywords) {
            return new Brand(key, displayName, Pattern.compile("\\b(?:" + keywords + ")\\b", Pattern.CASE_INSENSITIVE));
        }
    }

    /** Brand names plus a few product lines that imply the brand. */
    private static final List<Brand> BRANDS = List.of(
            Brand.of("samsung", "Samsung", "samsung|galaxy"),
            Brand.of("oneplus", "OnePlus", "oneplus"),
            Brand.of("apple", "Apple", "apple|iphone|ipad|macbook|imac|airpods"),
            Brand.of("asus", "ASUS", "asus|zenbook|vivobook|rog"),
            Brand.of("dell", "Dell", "dell|inspiron|latitude|xps|alienware"),
            Brand.of("lenovo", "Lenovo", "lenovo|thinkpad|ideapad"),
            Brand.of("hp", "HP", "hp|pavilion|elitebook|omen"),
            Brand.of("acer", "Acer", "acer|aspire|predator"),
            Brand.of("xiaomi", "Xiaomi", "xiaomi|redmi|poco"),
            Brand.of("google", "Google", "google|pixel"),
            Brand.of("motorola", "Motorola", "motorola|moto"),
            Brand.of("nokia", "Nokia", "nokia"),
            Brand.of("oppo", "OPPO", "oppo"),
            Brand.of("vivo", "vivo", "vivo"),
            Brand.of("realme", "realme", "realme"),
            Brand.of("sony", "Sony", "sony|playstation|bravia"));

    public List<Recommendation> recommend(Warranty w) {
        String product = clean(w.getProductName());
        String model = clean(w.getModelName());
        Brand brand = detectBrand(product + " " + model);
        if (brand == null) brand = detectBrand(clean(w.getRetailer()));
        String subject = (product + " " + model).trim();
        String searchTerm = model.isEmpty() ? product : model;

        if (brand == null) {
            return List.of(
                    new Recommendation("Extended warranty options", googleQuery("extended warranty " + subject)),
                    new Recommendation("Support & manuals", googleQuery(subject + " support")));
        }
        return List.of(
                new Recommendation(brand.displayName() + " protection plans", protectionLink(brand, searchTerm)),
                new Recommendation(brand.displayName() + " support", supportLink(brand, searchTerm)));
    }

    private static String protectionLink(Brand brand, String term) {
        return switch (brand.key()) {
            case "samsung" -> "https://www.samsung.com/us/support/samsung-care-plus/";
            case "oneplus" -> "https://www.oneplus.com/support/oneplus-care";
            case "apple" -> "https://support.apple.com/applecare";
            case "asus" -> googleSiteQuery("asus.com", "warranty extension " + term);
            case "dell" -> googleSiteQuery("dell.com", "extended warranty " + term);
            case "lenovo" -> googleSiteQuery("lenovo.com", "warranty upgrade " + term);
            case "hp" -> googleSiteQuery("hp.com", "care pack " + term);
            case "acer" -> googleSiteQuery("acer.com", "extended warranty " + term);
            case "xiaomi" -> googleSiteQuery("mi.com", "protection plan " + term);
            default -> googleSiteQuery(brand.key() + ".com", "extended warranty " + term);
        };
    }

    private static String supportLink(Brand brand, String term) {
        String q = encode(term);
        return switch (brand.key()) {
            case "samsung" -> "https://www.samsung.com/us/support/search/?query=" + q;
            case "oneplus" -> "https://www.oneplus.com/support/search?keyword=" + q;
            case "apple" -> "https://support.apple.com/en-us/search?query=" + q;
            case "asus" -> "https://www.asus.com/supportsearch?keyword=" + q;
            case "dell" -> "https://www.dell.com/support/search/en-us?query=" + q;
            case "lenovo" -> "https://support.lenovo.com/us/en/search?query=" + q;
            case "hp" -> "https://support.hp.com/us-en/search?q=" + q;
            case "acer" -> "https://www.acer.com/us-en/support?query=" + q;
            case "xiaomi" -> "https://www.mi.com/global/support/search?keyword=" + q;
            default -> googleSiteQuery(brand.key() + ".com", term + " support");
        };
    }

    private static Brand detectBrand(String text) {
        for (Brand b : BRANDS) {
            if (b.pattern().matcher(text).find()) return b;
        }
        return null;
    }

    private static String googleQuery(String q) {
        return "https://www.google.com/search?q=" + encode(q);
    }

    private static String googleSiteQuery(String domain, String q) {
        return googleQuery("site:" + domain + " " + q);
    }

    private static String encode(String s) {
        return URLEncoder.encode(s.trim().replaceAll("\\s+", " "), StandardCharsets.UTF_8);
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim();
    }
}
