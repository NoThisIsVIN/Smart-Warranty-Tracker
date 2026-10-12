package org.example.receipt;

import java.time.LocalDate;

/** Fields guessed from receipt text; any of them may be {@code null} when not found. */
public record ParsedReceipt(String productName, String modelName, String retailer, LocalDate purchaseDate,
                            Integer warrantyMonths) {
}
