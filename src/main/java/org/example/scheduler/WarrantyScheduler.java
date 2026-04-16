package org.example.scheduler;

import org.example.model.Warranty;
import org.example.repo.WarrantyRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class WarrantyScheduler {
    private final WarrantyRepository repo;

    public WarrantyScheduler(WarrantyRepository repo) { this.repo = repo; }

    @Scheduled(cron = "0 0 9 * * *")
    public void checkExpiries() {
        List<Warranty> all = repo.findAll();
        LocalDate now = LocalDate.now();
        for (Warranty w : all) {
            long daysLeft = java.time.temporal.ChronoUnit.DAYS.between(now, w.getExpiryDate());
            if (daysLeft <= 30 && !w.isNotified()) {
                System.out.println("[Reminder] Warranty for " + w.getProductName() + " expires in " + daysLeft + " days.");

                try {
                    String title = "Warranty reminder";
                    String body = w.getProductName() + " expires in " + daysLeft + " days";
                    org.example.ui.WindowsNotifier.notify(title, body);
                } catch (Throwable ignore) {}

                w.setNotified(true);
                repo.save(w);
            }
        }
    }
}
