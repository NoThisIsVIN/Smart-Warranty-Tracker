package org.example.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.Period;

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
        this.notified = false;
    }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getReceiptImageUrl() { return receiptImageUrl; }
    public void setReceiptImageUrl(String receiptImageUrl) { this.receiptImageUrl = receiptImageUrl; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getRetailer() { return retailer; }
    public void setRetailer(String retailer) { this.retailer = retailer; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public LocalDate getPurchaseDate() { return purchaseDate; }
    public void setPurchaseDate(LocalDate purchaseDate) { this.purchaseDate = purchaseDate; }
    public int getWarrantyMonths() { return warrantyMonths; }
    public void setWarrantyMonths(int warrantyMonths) { this.warrantyMonths = warrantyMonths; }
    public boolean isNotified() { return notified; }
    public void setNotified(boolean notified) { this.notified = notified; }

    @Transient
    public LocalDate getExpiryDate() {
        return purchaseDate.plusMonths(warrantyMonths);
    }

    @Transient
    public long getDaysLeft() {
        return java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), getExpiryDate());
    }
}
