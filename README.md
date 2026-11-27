# d.o.it Agenda Widget

**d.o.it Agenda Widget** is a native Android companion app and home screen widget suite for Google Calendar built with **Kotlin**, **Jetpack Compose**, **Material Design 3**, **Room**, **WorkManager**, and **Jetpack Glance**.

It connects directly to Android's built-in `CalendarContract` ContentProvider — **zero API keys, zero custom OAuth backends, zero cloud telemetry, and zero mockup data**.

---

## Highlights

- **Native Device Calendar Sync**: Reads and writes directly through Android `CalendarContract` using the Google accounts already signed in on your device.
- **Dual Home Screen Widgets**:
  - **Adaptive Glance Widget** (`CalendarGlanceWidget`): Material 3 styling, accessible 48dp touch targets, syncing indicator, and periodic WorkManager refresh.
  - **Classic Scrollable Agenda** (`CalendarAgendaWidgetProvider`): Full scrollable agenda list with custom opacity (`0%`–`100%`), 5 color themes, typography density, and verified tap-to-open.
- **Live Customization Studio**: Real-time interactive wallpaper preview, multi-calendar source picker, horizon selector (`1`–`30` days), display toggles, and Classic/Glance pin chooser.
- **Internationalization (i18n)**: Full English (`en`), Spanish (`es`), French (`fr`), and German (`de`) localization, Android 13+ per-app language support (`locales_config.xml`), and locale-safe 12h/24h date/time formatting.
- **Sync Diagnostics & Privacy-First Logging**: Interactive Sync Diagnostics sheet with one-tap Google account sync repair, PII-redacted clipboard diagnostic reports, and a bounded in-memory sanitized log viewer.
- **Release & Update Management**: R8 minification & resource shrinking for release builds, SemVer automation (`scripts/bump-version.sh`), and an in-app **About & Release Info** sheet with optional user-initiated HTTPS GitHub Releases update checks.

---

## Requirements

- **JDK 17**
- **Android SDK** (compileSdk 36, minSdk 26)
- **Gradle 9.3.1** (use `gradle`, not `./gradlew`)

---

## Build & Verify Locally

```bash
# Run full automated verification harness (static checks, compilation, tests, Android Lint)
bash harness.sh

# Run fast static safety & governance checks only
bash harness.sh --static-only

# Run JVM & Robolectric test suite
gradle :app:testDebugUnitTest

# Run tests with JaCoCo XML/HTML coverage report
gradle :app:jacocoDebugUnitTestReport

# Verify release Kotlin compilation
gradle :app:compileReleaseKotlin

# Preview a version bump without editing files
bash scripts/bump-version.sh patch --dry-run
```

---

## Architecture Overview

- `com.example.data.repository`: Modular calendar access split into `CalendarProviderDataSource` (reads), `CalendarEventWriter` (writes), `CalendarRepository` (sync orchestration), and `CalendarSyncDiagnosticsHelper`.
- `com.example.data.dao` / `db`: Indexed Room cache (`calendar_events`) excluded from Android Auto Backup & Device Transfer for privacy.
- `com.example.data.update`: Optional HTTPS-only GitHub Releases update checker (`AppUpdateChecker`) configured via `GITHUB_REPOSITORY` in `version.properties`.
- `com.example.widget`: Classic RemoteViews widget (`CalendarAgendaWidgetProvider`, `CalendarAgendaViewsFactory`) and Jetpack Glance widget (`CalendarGlanceWidget`, `CalendarGlanceWidgetReceiver`).
- `com.example.worker`: Periodic and immediate WorkManager synchronization (`CalendarSyncWorker`, `CalendarSyncScheduler`).
- `com.example.ui`: MVVM state (`CalendarViewModel`) with reactive midnight date rollover, background-threaded filtering, and adaptive single-pane/dual-pane Material 3 layouts.

---

## Documentation & Governance

- [Contributing Guide](CONTRIBUTING.md)
- [Security Policy](SECURITY.md)
- [Privacy Policy](PRIVACY.md)
- [Release Management Playbook](docs/RELEASE.md)
- [Testing & QA Playbook](docs/TESTING.md)
- [Changelog](CHANGELOG.md)
- [License (Apache-2.0)](LICENSE)
