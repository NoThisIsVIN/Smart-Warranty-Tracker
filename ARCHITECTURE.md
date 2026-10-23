# Architecture

## Runtime
A single desktop process. `Launcher` → `MainApp` (JavaFX `Application`).

- `MainApp.init()` starts the Spring context (`SmartWarrantyTrackerApplication`, web type `none`, scheduling enabled).
  If startup fails, for example because another instance holds the database lock, `start()` shows an error and exits.
- `MainApp.start()` loads `main.fxml` through `ViewLoader`, applies the theme, and shows one summary toast for
  warranties inside the reminder window.
- The database is embedded H2 in file mode (`jdbc:h2:file:<data dir>/smartwarranty`). The data dir comes from `AppPaths`: `%APPDATA%\SmartWarrantyTracker` by default. No network port is opened.

## Layers
```
ui (JavaFX)              MainController, WarrantyCard, WarrantyFormDialog, DetailView/DetailController,
                         SettingsView/SettingsController, ViewLoader, ThemeManager, Notifier, Dialogs, Browser, Formats
   │  calls
service                  WarrantyService (CRUD + file lifecycle), FileStorage, ProductImageService (async), Http
receipt                  ReceiptTextExtractor (PDFBox / POI / Vision / OCR.space), ReceiptParser (pure)
recommender              SimpleRecommender → Recommendation(label, url)
scheduler                WarrantyScheduler (daily 09:00)
config                   AppSettings (Java Preferences), ApiKeys (config/ properties in the data dir), AppPaths (data dir)
   │
repo / model             WarrantyRepository (Spring Data JPA) / Warranty, WarrantyStatus
```

## Key decisions
- **Nothing slow runs on the FX thread.** Receipt reading uses a `javafx.concurrent.Task`. Product image lookup runs on
  `ProductImageService`'s single background thread, at most once per warranty per session, and writes only the image
  column (`updateImageUrl`) so it can't overwrite concurrent edits.
- **Files are owned by warranties.** Receipts and images are copied into `receipts` and `images` in the data dir only when a
  warranty is saved, and deleted with it. `FileStorage.resolve` also finds files whose stored absolute path no longer
  exists (for example after data moved from the old `./data` folder) by looking them up by name.
- **Controllers get constructor injection.** `ViewLoader` creates FXML controllers through Spring's
  `AutowireCapableBeanFactory`, so they don't need to be singletons.
- **Theming uses tokens.** `app.css` defines looked-up colours on `.root` plus every component style. `dark.css` only
  redefines the tokens. `ThemeManager` restyles every open scene when the theme changes.
- **Status is derived, not stored.** `Warranty.status(today, reminderDays)` gives ACTIVE / EXPIRING / EXPIRED, so the
  reminder window from Settings is applied consistently everywhere.

## Data
`Warranty` columns: id, productName, modelName, retailer, purchaseDate, warrantyMonths, notified, imageUrl,
receiptImageUrl. The schema is managed by Hibernate `ddl-auto=update`. H2 is pinned to 2.2.224 to match the existing
database file format.
