## Smart Warranty Tracker - System Architecture

### Overview
Smart Warranty Tracker is a desktop JavaFX application bootstrapped with a Spring Boot backend (non-web) for persistence, dependency injection, and scheduling. It stores data in an embedded H2 database (accessed over TCP) and renders a modern JavaFX UI. Optional integrations include lightweight recommendation links.

### Key Technologies
- JavaFX for UI (`main.fxml`, `detail.fxml`, `settings.fxml`)
- Spring Boot for DI, lifecycle, and scheduling (non-web mode)
- Spring Data JPA with H2
- Java Preferences API for user settings (reminder days, theme)

### Runtime Composition
- Process: single desktop process launched via `org.example.Launcher` → `org.example.MainApp`.
- Spring Context: started inside JavaFX app (`SmartWarrantyTrackerApplication`) with `@EnableScheduling`.
- H2 Server: started as a TCP server during `MainApp.init()` to avoid local file locking.

### High-Level Module Diagram (C4: Container/Components)
```
[User]
  |
  v
JavaFX UI (Views + Controllers)
  - org.example.ui.MainController
  - org.example.ui.DetailController
  - org.example.ui.SettingsController
  - ThemeManager
       |
       v (service calls)
Spring Services
  - WarrantyService
  - SimpleRecommender
       |
       v (persistence)
Repository (Spring Data JPA)
  - WarrantyRepository
       |
       v
H2 Database (TCP)  <-->  DataLoader (seeding)

Schedulers
  - WarrantyScheduler (daily reminder flagging)

Optional REST (for future headless/remote use)
  - WarrantyController (/api/warranties)
```

### Request/Data Flows
- UI Listing/Actions:
  1) `MainController` → `WarrantyService.listAllWithImageFetch()`
  2) `WarrantyService` ensures `imageUrl` is populated (fetch/copy) and persists via `WarrantyRepository`.
  3) UI renders list/cards; users can view, search, delete, and open details.

- Detail View:
  - `DetailController.setWarranty(...)` computes expiry, days left, loads images, and allows delete.

- Reminders:
  - `MainController` periodically refreshes reminders list (<= 30 days).
  - `WarrantyScheduler` runs daily at 09:00 to set `notified` and log reminders.
  - `MainApp.start(...)` may show a Windows tray notification on startup when items are near-expiry.

- Settings & Theme:
  - `SettingsController` stores `reminder.days` and `theme` in Java Preferences and calls `ThemeManager.applyTheme`.
  - `MainApp` applies base `app.css` and optional `dark.css` on startup.



- Recommendations:
  - `SimpleRecommender` builds Google search links based on local data; results rendered as hyperlinks in Recommendations pane.

### Persistence and Configuration
- Database: H2 TCP server started in `MainApp.init()`; Spring connects via `spring.datasource.url=jdbc:h2:tcp://localhost/./data/smartwarranty`.
- JPA: `ddl-auto=update` for schema evolution; `Warranty` is the primary entity.
- Seeding: `DataLoader` inserts sample data on first run.
- Image/Receipt files: stored under `./data/images` and `./data/receipts`; UI loads via file URIs.
- Image Fetch Config: `/image-fetch-config.properties` defines API key/endpoint/search engine ID used by `WarrantyService` to fetch an image when missing.

### Notable Classes
- `Launcher` → entry point delegating to JavaFX `MainApp`.
- `MainApp` → boots Spring, starts H2 TCP, loads `main.fxml`, sets theme, and triggers tray notifications.
- `SmartWarrantyTrackerApplication` → Spring Boot application with scheduling enabled.
- `MainController` → dashboard, reminders, recommendations, add product, search.
- `DetailController` → warranty detail modal, image viewing, receipt attach/view.
- `SettingsController` → preferences (reminder days, theme), theme apply.
- `WarrantyService` → CRUD, image default/fetch, queries.
- `WarrantyRepository` → Spring Data JPA repository.
- `WarrantyScheduler` → daily expiry checks.
- `SimpleRecommender` → simple link-based recommendations.


### Deployment/Packaging
- Built as a Spring Boot jar with JavaFX resources; launched as a desktop app. The web-application-type is set to `none` to avoid starting an HTTP server for the UI flow. A REST controller exists for potential future integrations but is inactive in normal desktop use.

### Quality and Extensibility Considerations
- Separation of concerns: UI controllers delegate to services; persistence via repository.
- Background tasks: JavaFX Timelines for periodic UI refresh; Spring `@Scheduled` for daily checks.
- Theming: runtime theme toggling via `ThemeManager`.
- Extensible points: reinstate email service, integrate external recommender, enable web mode to expose REST endpoints.

