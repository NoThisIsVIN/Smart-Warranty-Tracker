package org.example.scheduler;

import org.example.config.AppSettings;
import org.example.model.Warranty;
import org.example.repo.WarrantyRepository;
import org.example.ui.Notifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/** Daily reminder for warranties entering the user's reminder window. Each warranty is announced once. */
@Component
public class WarrantyScheduler {
    private static final Logger log = LoggerFactory.getLogger(WarrantyScheduler.class);
    private static final int MAX_NAMES = 3;

    private final WarrantyRepository repo;
    private final AppSettings settings;

    public WarrantyScheduler(WarrantyRepository repo, AppSettings settings) {
        this.repo = repo;
        this.settings = settings;
    }

    @Scheduled(cron = "0 0 9 * * *")
    public void checkExpiries() {
        LocalDate today = LocalDate.now();
        int window = settings.getReminderDays();
        List<Warranty> due = repo.findAll().stream()
                .filter(w -> !w.isNotified() && w.getPurchaseDate() != null)
                .filter(w -> {
                    long days = w.daysLeft(today);
                    return days >= 0 && days <= window;
                })
                .toList();
        if (due.isEmpty()) return;

        try {
            if (due.size() == 1) {
                Warranty w = due.get(0);
                Notifier.show("Warranty expiring soon", name(w) + " " + when(w.daysLeft(today)), Notifier.Type.WARNING);
            } else {
                String names = due.stream().limit(MAX_NAMES).map(WarrantyScheduler::name).collect(Collectors.joining(", "));
                if (due.size() > MAX_NAMES) names += " and " + (due.size() - MAX_NAMES) + " more";
                Notifier.show(due.size() + " warranties expiring soon", names, Notifier.Type.WARNING);
            }
        } catch (RuntimeException e) {
            log.warn("Could not show expiry reminder", e);
            return;
        }
        due.forEach(w -> repo.updateNotified(w.getId(), true));
        log.info("Sent expiry reminder for {} warranties", due.size());
    }

    private static String name(Warranty w) {
        String name = w.getDisplayName();
        return name.isEmpty() ? "A product" : name;
    }

    private static String when(long days) {
        if (days == 0) return "expires today";
        if (days == 1) return "expires tomorrow";
        return "expires in " + days + " days";
    }
}
