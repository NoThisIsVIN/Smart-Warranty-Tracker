package org.example.recommender;

/** A labelled link shown next to a warranty (protection plans, support pages). */
public record Recommendation(String label, String url) {
}
