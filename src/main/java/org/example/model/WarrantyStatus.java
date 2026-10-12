package org.example.model;

public enum WarrantyStatus {
    ACTIVE("Active"),
    EXPIRING("Expiring soon"),
    EXPIRED("Expired");

    private final String label;

    WarrantyStatus(String label) { this.label = label; }

    public String label() { return label; }

    /** EXPIRED when daysLeft &lt; 0, EXPIRING when 0..reminderDays, otherwise ACTIVE. */
    public static WarrantyStatus of(long daysLeft, int reminderDays) {
        if (daysLeft < 0) return EXPIRED;
        if (daysLeft <= reminderDays) return EXPIRING;
        return ACTIVE;
    }
}
