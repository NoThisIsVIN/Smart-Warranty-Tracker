package org.example.recommender;

import org.example.model.Warranty;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SimpleRecommenderTest {

    private static final LocalDate PURCHASED = LocalDate.of(2024, 3, 5);
    private final SimpleRecommender recommender = new SimpleRecommender();

    @Test
    void encodesSpecialCharactersInQueries() {
        Warranty w = new Warranty("Tom & Jerry Speaker+", "X1#2", "Local Shop", PURCHASED, 12);
        List<Recommendation> recs = recommender.recommend(w);

        assertThat(recs).hasSize(2);
        assertThat(recs).allSatisfy(r -> {
            assertThat(r.url()).startsWith("https://www.google.com/search?q=");
            assertThat(r.url()).contains("%26").contains("%2B").contains("%23");
            assertThat(r.url().substring(r.url().indexOf("?q=") + 3)).doesNotContain("&", "#", " ");
        });
    }

    @Test
    void routesSamsungToSamsungPages() {
        Warranty w = new Warranty("Galaxy S23", "SM-S911B", "Amazon", PURCHASED, 12);
        List<Recommendation> recs = recommender.recommend(w);

        assertThat(recs).extracting(Recommendation::url).containsExactly(
                "https://www.samsung.com/us/support/samsung-care-plus/",
                "https://www.samsung.com/us/support/search/?query=SM-S911B");
        assertThat(recs.get(0).label()).contains("Samsung");
    }

    @Test
    void usesRetailerWhenProductHasNoBrand() {
        Warranty w = new Warranty("Laptop", "Latitude 5440", null, PURCHASED, 12);
        assertThat(recommender.recommend(w).get(1).url())
                .isEqualTo("https://www.dell.com/support/search/en-us?query=Latitude+5440");

        Warranty byRetailer = new Warranty("Laptop", "X1 Carbon", "Lenovo", PURCHASED, 12);
        assertThat(recommender.recommend(byRetailer).get(1).url()).startsWith("https://support.lenovo.com/");
    }

    @Test
    void brandMatchingUsesWordBoundaries() {
        Warranty w = new Warranty("Smartphone", "chpad 3", null, PURCHASED, 12);
        assertThat(recommender.recommend(w).get(0).url()).startsWith("https://www.google.com/search?q=extended+warranty");
    }

    @Test
    void nullFieldsAreSafe() {
        List<Recommendation> recs = recommender.recommend(new Warranty(null, null, null, null, 12));
        assertThat(recs).hasSize(2).allSatisfy(r -> {
            assertThat(r.label()).isNotBlank();
            assertThat(r.url()).startsWith("https://").doesNotContain("null");
        });
    }

    @Test
    void searchesByProductWhenModelMissing() {
        Warranty w = new Warranty("iPhone 15", null, null, PURCHASED, 12);
        assertThat(recommender.recommend(w).get(1).url())
                .isEqualTo("https://support.apple.com/en-us/search?query=iPhone+15");
    }
}
