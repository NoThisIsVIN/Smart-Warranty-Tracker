package org.example.dev;

import org.example.model.Warranty;
import org.example.service.WarrantyService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DevListWarranties implements CommandLineRunner {
    private final WarrantyService service;

    public DevListWarranties(WarrantyService service) {
        this.service = service;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("=== Debug: listing warranties and receiptImageUrl ===");
        List<Warranty> all = service.listAll();
        for (Warranty w : all) {
            System.out.println("ID=" + w.getId() + " product=" + w.getProductName() + " model=" + w.getModelName());
            System.out.println("  purchaseDate=" + w.getPurchaseDate() + " warrantyMonths=" + w.getWarrantyMonths() + " expiryDate=" + w.getExpiryDate() + " daysLeft=" + w.getDaysLeft());
            System.out.println("  receiptImageUrl=" + w.getReceiptImageUrl());
        }
        System.out.println("=== End debug ===");
    }
}
