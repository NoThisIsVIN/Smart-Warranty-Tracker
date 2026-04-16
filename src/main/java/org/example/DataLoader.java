package org.example;

import org.example.model.Warranty;
import org.example.repo.WarrantyRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class DataLoader implements CommandLineRunner {
    private final WarrantyRepository repo;
    public DataLoader(WarrantyRepository repo) { this.repo = repo; }

    @Override
    public void run(String... args) {
        if (repo.count() == 0) {
            repo.save(new Warranty("Smartphone", "X1000", "Retailer A", LocalDate.now().minusMonths(3), 12));
            repo.save(new Warranty("Laptop", "L-Model-7", "Retailer B", LocalDate.now().minusMonths(20), 24));
        }
    }
}
