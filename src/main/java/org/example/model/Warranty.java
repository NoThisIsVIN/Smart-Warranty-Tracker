package org.example.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * A tracked product warranty. Field names map 1:1 to existing database columns, so do not rename them.
 * {@code imageUrl} and {@code receiptImageUrl} hold file URIs (receipts may be images, PDFs or DOCX files).
 */
@Entity
public class Warranty {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String productName;
    private String modelName;
    private String retailer;
    private LocalDate purchaseDate;
    private int warrantyMonths;
    private boolean notified;
    private String imageUrl;
    private String receiptImageUrl;

    public Warranty() {}

    public Warranty(String productName, String modelName, String retailer, LocalDate purchaseDate, int warrantyMonths) {
        this.productName = productName;
        this.modelName = modelName;
        this.retailer = retailer;
        this.purchaseDate = purchaseDate;
        this.warrantyMonths = warrantyMonths;
    }

    /** Expiry date, or {@code null} when no purchase date is known. */
    public LocalDate getExpiryDate() {
        return purchaseDate == null ? null : purchaseDate.plusMonths(warrantyMonths);
    }

    /** Days until expiry relative to {@code today}; negative once expired, 0 when the expiry date is unknown. */
    public long daysLeft(LocalDate today) {
        LocalDate expiry = getExpiryDate();
        return expiry == null ? 0 : ChronoUnit.DAYS.between(today, expiry);
    }

    public long getDaysLeft() {
        return daysLeft(LocalDate.now());
    }

    /** Fraction of the warranty period that has elapsed, clamped to [0, 1]. */
    public double elapsedFraction(LocalDate today) {
        LocalDate expiry = getExpiryDate();
        if (expiry == null) return 0;
        long total = ChronoUnit.DAYS.between(purchaseDate, expiry);
        if (total <= 0) return 1;
        long elapsed = ChronoUnit.DAYS.between(purchaseDate, today);
        return Math.max(0, Math.min(1, (double) elapsed / total));
    }

    /** Status relative to {@code today}; a warranty without a purchase date counts as ACTIVE. */
    public WarrantyStatus status(LocalDate today, int reminderDays) {
        if (getExpiryDate() == null) return WarrantyStatus.ACTIVE;
        return WarrantyStatus.of(daysLeft(today), reminderDays);
    }

    /** Product and model joined for display, e.g. "Galaxy S23 SM-S911B". */
    public String getDisplayName() {
        String name = productName == null ? "" : productName.trim();
        String model = modelName == null ? "" : modelName.trim();
        if (model.isEmpty() || name.toLowerCase().contains(model.toLowerCase())) return name;
        return name.isEmpty() ? model : name + " " + model;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public String getRetailer() { return retailer; }
    public void setRetailer(String retailer) { this.retailer = retailer; }
    public LocalDate getPurchaseDate() { return purchaseDate; }
    public void setPurchaseDate(LocalDate purchaseDate) { this.purchaseDate = purchaseDate; }
    public int getWarrantyMonths() { return warrantyMonths; }
    public void setWarrantyMonths(int warrantyMonths) { this.warrantyMonths = warrantyMonths; }
    public boolean isNotified() { return notified; }
    public void setNotified(boolean notified) { this.notified = notified; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getReceiptImageUrl() { return receiptImageUrl; }
    public void setReceiptImageUrl(String receiptImageUrl) { this.receiptImageUrl = receiptImageUrl; }
}
