# Smart Warranty Tracker

A Java desktop application to manage product warranties. Built with Spring Boot (backend), JavaFX (frontend), and H2 for local storage. This application demonstrates automated expiry calculation, scheduled reminders, and a simple recommender system.

## Project Structure

```
swt/
├── data/                    # Local storage (images, receipts, configurations)
├── src/
│   └── main/
│       ├── java/org/example/
│       │   ├── controller/  # REST Controllers for external data access
│       │   ├── dev/         # Developer tools and query scripts
│       │   ├── model/       # Data entities (e.g., Warranty)
│       │   ├── recommender/ # Recommendation logic
│       │   ├── repo/        # Database repositories (Spring Data JPA)
│       │   ├── scheduler/   # Scheduled tasks (e.g., Expiry notifications)
│       │   ├── service/     # Business logic and external integrations
│       │   └── ui/          # JavaFX controllers and UI logic
│       └── resources/       # Application configuration, FXML views, and CSS
├── ARCHITECTURE.md          # Detailed system architecture
└── pom.xml                  # Maven configuration
```


## Getting Started (Windows PowerShell)

Requirements: Java 17+ and Maven 3+

1. **Build the Application:**
   ```powershell
   mvn clean package
   ```

2. **Run the Application:**
   ```powershell
   mvn javafx:run
   ```

## Key Features
- **Comprehensive Dashboard:** View all your active and expired warranties at a glance.
- **Warranty Management:** Easily add, edit, and categorize product warranties and receipts.
- **OCR Receipt Scanning:** Automatically extract text and details from uploaded receipts using cloud-based Optical Character Recognition (Google Vision API with OCR.space fallback).
- **Automated Expiry Notifications:** A background scheduler continuously monitors your items and triggers Windows desktop tray notifications before a warranty expires.
- **Local Embedded Database:** Uses an H2 database for fast, offline, and seamless data persistence without complicated setup.
- **Smart Recommendations:** Includes a basic recommender system that analyzes your saved items and provides personalized insights.
- **Settings & Customization:** Features a dynamic UI with light/dark themes and customizable image-fetch properties.
- **Data Export & Cloud Prep:** Well-structured architecture designed for eventual cloud sync, email alerts, and external data access via REST Controllers.

## Next Steps
- Replace H2 with PostgreSQL for production use and provide migration scripts.
- Add robust authentication and cloud backup for receipts.
- Implement persistent notification channels like Email or Push notifications.
