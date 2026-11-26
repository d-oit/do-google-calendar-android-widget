# Changelog

All notable changes to **Agenda Widget** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **Internationalization (i18n)**: Full English (`en`), Spanish (`es`), French (`fr`), and German (`de`) localization, Android 13+ per-app language configuration (`locales_config.xml`), locale-safe `AgendaFilterCategory` filtering, and 12h/24h system time formatting via `CalendarDateFormatter`.
- **In-App About & Release Update Check**: New About & Release Info sheet displaying version/build metadata, privacy posture, language settings shortcut, and user-initiated HTTPS GitHub Releases update check (`AppUpdateChecker`, configured via `GITHUB_REPOSITORY` in `version.properties`).
- **Jetpack Glance Parity & Pin Chooser**: Upgraded Glance widget buttons to 48dp accessible touch targets, added syncing indicator state, added system time/timezone/date/calendar broadcast handling, and introduced `WidgetPinChooserDialog` (Adaptive Glance vs. Classic Scrollable).
- **Sanitized Diagnostic Log Ring Buffer**: Added bounded 120-entry PII-redacted log buffer (`LogSanitizer`) in `AppLogger`, surfaced via a collapsible viewer in `SyncDiagnosticsBottomSheet`.
- **Release & Repository Governance**: Added `scripts/bump-version.sh`, `README.md`, `CONTRIBUTING.md`, `SECURITY.md`, `PRIVACY.md`, `LICENSE` (Apache-2.0), `.github/CODEOWNERS`, and `.github/workflows/security.yml` (CodeQL + static security checks).

### Changed
- **Release Build Hardening**: Enabled R8 code minification (`isMinifyEnabled = true`) and resource shrinking (`isShrinkResources = true`) with tailored ProGuard keep rules and CI `mapping.txt` archival.
- **Data Layer Modularization & Performance**: Split `CalendarRepository` into `CalendarProviderDataSource` and `CalendarEventWriter`, offloaded agenda filtering to `Dispatchers.Default`, debounced widget invalidation broadcasts (150ms), and gated legacy seed cleanup to once per process.

### Fixed
- **Midnight Date Rollover & Tablet Scroll**: Added reactive day-boundary refresh on `ACTION_DATE_CHANGED`/`TIME_CHANGED`/`TIMEZONE_CHANGED` and moved `WidgetCustomizerControls` into the scrollable left pane on expanded tablet screens.
- **Security & Privacy**: Hardened exported widget click broadcast handling against untrusted link redirection by verifying event IDs against Room cache, excluded `calendar_widget_db` from Android Auto Backup & Device Transfer, redacted account emails by default in diagnostic reports, flagged copied reports as sensitive on Android 13+, and eliminated workflow expression interpolation in `release.yml`.

## [0.1.0] - 2026-11-26

Initial public pre-1.0 release of **Agenda Widget** (versionCode `1`).

### Added
- **Native OS Calendar Integration (Zero API Keys)**: Direct read/write sync with Android `CalendarContract` accounts and instances with strict zero-mockup enforcement.
- **Live Customization Studio**: Real-time interactive widget preview with configurable background opacity (`0%`–`100%`), 5 color themes (Material You Dynamic, Google Blue, Midnight Slate, Emerald Green, Sunset Warm), typography density (`Compact`, `Regular`, `Large`), days horizon (`1`–`30` days), multi-calendar filtering, and display toggles.
- **Dual Home Screen Widgets**: Both Classic `RemoteViews` (`CalendarAgendaWidgetProvider`) and Jetpack Glance (`CalendarGlanceWidget`) widgets with manual header refresh, 48dp accessible touch targets, and persisted last-sync timestamp indicators.
- **Background Sync & Offline Cache**: Indexed Room database (`calendar_events`) with atomic `@Transaction` updates and `WorkManager` periodic/immediate calendar sync (`CalendarSyncWorker`).
- **Pull-to-Refresh & Event Management**: Material 3 `PullToRefreshBox` force-sync, full event detail view, add/edit/delete real calendar events, native `CalendarContract` color palette mapping (`CalendarColorMapper`), and Material 3 empty-state illustration.
- **Sync Diagnostics & Reliability**: Interactive Sync Diagnostics bottom sheet to inspect and repair disabled `SYNC_EVENTS` flags or horizon filters, structured `AppLogger`, and typed `OperationResult` / `UiError` snackbar recovery actions.
- **GitHub Release & Test Management**:
  - Centralized Semantic Versioning via `version.properties` starting at `0.1.0` (`versionCode = 1`).
  - GitHub Actions CI (`ci.yml`) with static safety harness, Unit & Robolectric JUnit test reporting, JaCoCo XML/HTML code coverage summaries, Android Lint, and PR Dependency Vulnerability Review.
  - GitHub Actions Release Pipeline (`release.yml`) triggered by `v*.*.*` Git tags and manual workflow dispatch, supporting signed Release APK + AAB builds with automatic debug fallback, SHA-256 checksums, and changelog note extraction.
  - Repository governance templates (PR template, Bug/Feature/Release QA Test Plan issue forms, `.github/release.yml`, Dependabot for Gradle & Actions) and maintainer playbooks (`docs/RELEASE.md`, `docs/TESTING.md`).

[Unreleased]: https://github.com/OWNER/REPO/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/OWNER/REPO/releases/tag/v0.1.0
