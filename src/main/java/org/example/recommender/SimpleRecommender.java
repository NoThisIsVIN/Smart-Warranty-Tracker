package org.example.recommender;

import org.example.model.Warranty;
import org.example.repo.WarrantyRepository;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class SimpleRecommender {
    private final WarrantyRepository repo;

    public SimpleRecommender(WarrantyRepository repo) { this.repo = repo; }

    public List<String> recommendByProductAndModel(String productName, String modelName) {
        List<Warranty> all = repo.findAll();
        for (Warranty w : all) {
            if (w.getProductName().equalsIgnoreCase(productName) && w.getModelName() != null
                    && w.getModelName().equalsIgnoreCase(modelName)) {
                return buildRecommendations(w);
            }
        }

        for (Warranty w : all) {
            if (w.getProductName().equalsIgnoreCase(productName)) {
                List<String> recs = new ArrayList<>();
                recs.add("Similar product found: " + w.getModelName() + " — check retailer offers: " + protectionLinkFor(w));
                return recs;
            }
        }
        return new ArrayList<>();
    }

    private List<String> buildRecommendations(Warranty w) {
        List<String> recs = new ArrayList<>();
        if (w.getWarrantyMonths() < 12) recs.add("1-year extended warranty — " + protectionLinkFor(w));
        if (w.getWarrantyMonths() < 24) recs.add("2-year protection plan — " + protectionLinkFor(w));
        recs.add("Service & support page — " + supportLinkFor(w));
        return recs;
    }

    private String protectionLinkFor(Warranty w) {
        String brand = detectBrand(w);
        String model = (w.getModelName() == null ? "" : w.getModelName());
        if (brand == null) return googleQuery("extended warranty " + w.getProductName() + " " + model);
        switch (brand) {
            case "samsung": return "https://www.samsung.com/us/support/samsung-care-plus/";
            case "oneplus": return "https://www.oneplus.com/support/oneplus-care";
            case "asus": return googleSiteQuery("asus.com", "warranty extension " + model);
            case "apple": return "https://support.apple.com/applecare";
            case "dell": return googleSiteQuery("dell.com", "extended warranty " + model);
            case "lenovo": return googleSiteQuery("lenovo.com", "warranty upgrade " + model);
            case "hp": return googleSiteQuery("hp.com", "care pack " + model);
            case "acer": return googleSiteQuery("acer.com", "extended warranty " + model);
            case "xiaomi": return googleSiteQuery("mi.com", "protection plan " + model);
            default: return googleSiteQuery(brand + ".com", "extended warranty " + model);
        }
    }

    private String supportLinkFor(Warranty w) {
        String brand = detectBrand(w);
        String model = (w.getModelName() == null ? "" : w.getModelName());
        if (brand == null) return googleQuery(w.getProductName() + " " + model + " support");
        switch (brand) {
            case "samsung": return "https://www.samsung.com/us/support/search/?query=" + url(model);
            case "oneplus": return "https://www.oneplus.com/support/search?keyword=" + url(model);
            case "asus": return "https://www.asus.com/supportsearch?keyword=" + url(model);
            case "apple": return "https://support.apple.com/en-us/search?query=" + url(model);
            case "dell": return "https://www.dell.com/support/search/en-us?query=" + url(model);
            case "lenovo": return "https://support.lenovo.com/us/en/search?query=" + url(model);
            case "hp": return "https://support.hp.com/us-en/search?q=" + url(model);
            case "acer": return "https://www.acer.com/us-en/support?query=" + url(model);
            case "xiaomi": return "https://www.mi.com/global/support/search?keyword=" + url(model);
            default: return googleSiteQuery(brand + ".com", model + " support");
        }
    }

    private String detectBrand(Warranty w) {
        String text = (w.getProductName() + " " + (w.getModelName() == null ? "" : w.getModelName()) + " " + (w.getRetailer() == null ? "" : w.getRetailer())).toLowerCase();
        List<String> brands = Arrays.asList("samsung","oneplus","asus","apple","dell","lenovo","hp","acer","xiaomi","google","motorola","nokia","oppo","vivo","realme","sony");
        for (String b : brands) if (text.contains(b)) return b;
        return null;
    }

    private String googleQuery(String q) {
        return "https://www.google.com/search?q=" + url(q);
    }

    private String googleSiteQuery(String domain, String q) {
        return "https://www.google.com/search?q=" + url("site:" + domain + " " + q);
    }

    private String url(String s) {
        return s.trim().replace(" ", "+");
    }

}
