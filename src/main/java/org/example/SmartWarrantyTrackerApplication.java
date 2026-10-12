package org.example;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Spring configuration root; the context is started by {@link MainApp#init()}. */
@SpringBootApplication
@EnableScheduling
public class SmartWarrantyTrackerApplication {
}
