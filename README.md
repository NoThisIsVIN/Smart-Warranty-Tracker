# Smart Warranty Tracker

A desktop app for keeping track of product warranties: what you bought, when it expires, and where the receipt is.
Built with JavaFX for the UI and Spring Boot (non-web) for dependency injection, persistence and scheduling.
Data lives in an embedded H2 database under `%APPDATA%\SmartWarrantyTracker` (`~/.smart-warranty-tracker` on other systems).

## Features
- **Dashboard**: cards for every warranty, with live search, status filters (Active / Expiring / Expired), sorting and summary tiles.
- **Add and edit**: a single form with a date picker and validation. Optionally attach a receipt (image, PDF or DOCX) and the
  app reads it in the background to pre-fill the product, model, retailer, purchase date and warranty length.
- **Details**: progress through the warranty period, the receipt (open or replace it), and helpful support and extended-warranty links.
- **Reminders**: a toast at startup and a daily 09:00 check for warranties expiring within your reminder window (set in Settings).
- **Product images**: fetched in the background for warranties that don't have one (Google Custom Search if configured,
  otherwise DuckDuckGo and Wikipedia).
- **Light and dark themes.**

## Requirements
- JDK 21 or newer
- Maven 3.9+

## Run
```bash
mvn javafx:run
```

## Test
```bash
mvn test
```

## Package for Windows
```powershell
.\packaging\package-windows.ps1            # zip of a ready-to-run folder, Java included
.\packaging\package-windows.ps1 -Type exe  # installer; needs the WiX Toolset on PATH
```
Output goes to `target\dist`.

## Where data is stored
Database, images, receipts, API keys and logs go in `%APPDATA%\SmartWarrantyTracker`.
Set `-Dswt.data.dir=<path>` to use another folder. On first start, data from an older `./data` folder is copied over.

## Optional API keys
Open **Settings → API keys**. Keys are stored locally in `config/image-fetch-config.properties` inside the data folder.

| Key | Used for |
| --- | --- |
| Google Vision | OCR of receipt images (best quality) |
| OCR.space | OCR fallback. Without a key, OCR.space's public demo key is used, which is rate-limited. |
| Google Custom Search key + engine ID | Product image lookup |

Receipt images are sent to these OCR services only when you attach one. PDF and DOCX receipts are read locally.

## Project layout
```
src/main/java/org/example/
  config/       data folder (AppPaths), user preferences (AppSettings) and API keys (ApiKeys)
  model/        Warranty entity and WarrantyStatus
  repo/         Spring Data repository
  service/      warranty CRUD, file storage, product image lookup, shared HTTP client
  receipt/      text extraction (PDF / DOCX / OCR) and the receipt parser
  recommender/  support and extended-warranty links
  scheduler/    daily expiry check
  ui/           JavaFX controllers, dialogs, theming, notifications
src/main/resources/
  fxml/         main, detail and settings views
  styles/       app.css (tokens + components) and dark.css (dark tokens)
packaging/      Windows release script (jpackage)
```

See [ARCHITECTURE.md](ARCHITECTURE.md) for how the pieces fit together.

## License
MIT. See [LICENSE](LICENSE).
