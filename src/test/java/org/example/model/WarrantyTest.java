package org.example.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class WarrantyTest {

    private static final LocalDate PURCHASED = LocalDate.of(2024, 1, 15);

    private static Warranty warranty(LocalDate purchased, int months) {
        return new Warranty("Galaxy S23", "SM-S911B", "Samsung", purchased, months);
    }

    @Test
    void expiryAddsWarrantyMonths() {
        assertThat(warranty(PURCHASED, 12).getExpiryDate()).isEqualTo(LocalDate.of(2025, 1, 15));
        assertThat(warranty(LocalDate.of(2024, 1, 31), 1).getExpiryDate()).isEqualTo(LocalDate.of(2024, 2, 29));
    }

    @Test
    void daysLeftIsSignedRelativeToToday() {
        Warranty w = warranty(PURCHASED, 12);
        assertThat(w.daysLeft(LocalDate.of(2025, 1, 5))).isEqualTo(10);
        assertThat(w.daysLeft(LocalDate.of(2025, 1, 15))).isZero();
        assertThat(w.daysLeft(LocalDate.of(2025, 1, 18))).isEqualTo(-3);
    }

    @Test
    void elapsedFractionIsClamped() {
        Warranty w = warranty(PURCHASED, 12);
        assertThat(w.elapsedFraction(LocalDate.of(2023, 6, 1))).isZero();
        assertThat(w.elapsedFraction(PURCHASED)).isZero();
        assertThat(w.elapsedFraction(LocalDate.of(2024, 7, 15))).isCloseTo(0.5, within(0.01));
        assertThat(w.elapsedFraction(LocalDate.of(2026, 1, 1))).isEqualTo(1.0);
        assertThat(warranty(PURCHASED, 0).elapsedFraction(PURCHASED)).isEqualTo(1.0);
    }

    @Test
    void statusThresholds() {
        assertThat(WarrantyStatus.of(31, 30)).isEqualTo(WarrantyStatus.ACTIVE);
        assertThat(WarrantyStatus.of(30, 30)).isEqualTo(WarrantyStatus.EXPIRING);
        assertThat(WarrantyStatus.of(0, 30)).isEqualTo(WarrantyStatus.EXPIRING);
        assertThat(WarrantyStatus.of(-1, 30)).isEqualTo(WarrantyStatus.EXPIRED);

        Warranty w = warranty(PURCHASED, 12);
        assertThat(w.status(LocalDate.of(2024, 6, 1), 30)).isEqualTo(WarrantyStatus.ACTIVE);
        assertThat(w.status(LocalDate.of(2025, 1, 1), 30)).isEqualTo(WarrantyStatus.EXPIRING);
        assertThat(w.status(LocalDate.of(2025, 1, 1), 7)).isEqualTo(WarrantyStatus.ACTIVE);
        assertThat(w.status(LocalDate.of(2025, 2, 1), 30)).isEqualTo(WarrantyStatus.EXPIRED);
    }

    @Test
    void missingPurchaseDateIsSafe() {
        Warranty w = warranty(null, 12);
        LocalDate today = LocalDate.of(2025, 1, 1);
        assertThat(w.getExpiryDate()).isNull();
        assertThat(w.daysLeft(today)).isZero();
        assertThat(w.elapsedFraction(today)).isZero();
        assertThat(w.status(today, 30)).isEqualTo(WarrantyStatus.ACTIVE);
    }

    @Test
    void displayNameJoinsProductAndModel() {
        assertThat(warranty(PURCHASED, 12).getDisplayName()).isEqualTo("Galaxy S23 SM-S911B");
        assertThat(new Warranty("Galaxy S23", "S23", null, PURCHASED, 12).getDisplayName()).isEqualTo("Galaxy S23");
        assertThat(new Warranty(null, "WH-1000XM5", null, PURCHASED, 12).getDisplayName()).isEqualTo("WH-1000XM5");
        assertThat(new Warranty("  Kindle ", null, null, PURCHASED, 12).getDisplayName()).isEqualTo("Kindle");
        assertThat(new Warranty(null, null, null, PURCHASED, 12).getDisplayName()).isEmpty();
    }
}
