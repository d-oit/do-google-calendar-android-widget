# AGENTS.md: Developer & Agent Engineering Instructions

This document governs the engineering standards, architectural patterns, and operational guardrails for AI coding agents and developers working on the **Customizable Google Calendar Home Screen Widget**.

---

## 1. Core Principles & Philosophy

1. **Native OS First — Zero API Keys**:
   - The application **must not** require or ask users for Google Cloud API keys or external OAuth setup.
   - All calendar access must use Android's native `CalendarContract` ContentProvider (`READ_CALENDAR`, `WRITE_CALENDAR`).
   - The user's Google accounts already authenticated on the Android device are the single source of truth.

2. **Absolute Zero Mockup Data**:
   - Never generate, seed, or hardcode fake/mock calendar events (e.g. "Sprint Planning", "1:1 Sync").
   - If no calendar permissions are granted, or if the user has no upcoming events, display an honest, clear empty state ("No upcoming events scheduled") with a manual sync action.

3. **Synchronous Widget Performance**:
   - AppWidget `RemoteViewsService.RemoteViewsFactory` operates synchronously on background binder threads.
   - Never perform blocking network requests or asynchronous locks during RemoteViews rendering.
   - Cache query results in `EventDao` (indexed Room database) and read preferences synchronously from `SharedPreferences` via `WidgetPreferencesManager`.

---

## 2. Architecture & Data Layer Standards

### Package Structure
```
com.example
├── CalendarApp.kt                       # Application entry point & DI singleton
├── MainActivity.kt                      # Companion Activity (Live Customization Studio)
├── data
│   ├── dao/EventDao.kt                  # Room DAO with @Transaction batch ops
│   ├── db/AppDatabase.kt                # Room database instance (excluded from cloud backups)
│   ├── model/CalendarSyncDiagnostics.kt # Diagnostics snapshot + PII-redacted report builder
│   ├── model/EventEntity.kt             # Room entity with composite indices
│   ├── model/OperationResult.kt         # Sealed AppError & OperationResult types
│   ├── pref/WidgetPreferencesManager.kt # Customization preferences store (debounced broadcasts)
│   ├── repository/
│   │   ├── CalendarEventWriter.kt       # Native CalendarContract create/update/delete writes
│   │   ├── CalendarProviderDataSource.kt# Native CalendarContract read queries & fallback
│   │   ├── CalendarRepository.kt        # Sync orchestration & Room cache coordination
│   │   └── CalendarSyncDiagnosticsHelper.kt # Provider health & sync adapter repair
│   └── update/AppUpdateChecker.kt       # Optional HTTPS-only GitHub Releases SemVer checker
├── ui
│   ├── CalendarViewModel.kt             # Jetpack Compose UI state, midnight rollover & UiEvent
│   ├── components
│   │   ├── AboutReleaseBottomSheet.kt   # Version info, privacy summary & update check sheet
│   │   ├── AddEventDialog.kt            # Create/edit real event dialog
│   │   ├── AgendaScheduleContent.kt     # Search, locale-safe category filters & agenda list
│   │   ├── CalendarPermissionRequestCard.kt # Native CalendarContract permission onboarding
│   │   ├── EventDetailDialog.kt         # Event detail view
│   │   ├── EventItemCard.kt             # Agenda item card
│   │   ├── EventListLazyColumn.kt       # Pull-to-refresh list + empty states
│   │   ├── SyncDiagnosticsBottomSheet.kt# Diagnostics inspector + sanitized log viewer
│   │   ├── WidgetCustomizerControls.kt  # Customization controls (sliders, chips)
│   │   ├── WidgetPinChooserDialog.kt    # Glance vs. Classic widget pin selector
│   │   └── WidgetPreviewCard.kt         # Real-time interactive widget canvas
│   └── theme/                           # Color, Theme (AgendaWidgetTheme), Type tokens (M3)
├── util
│   ├── AppLogger.kt                     # Tagged logger + LogSanitizer + 120-entry ring buffer
│   ├── CalendarColorMapper.kt           # CalendarContract color extraction & mapping
│   ├── CalendarDateFormatter.kt         # Locale-aware 12h/24h date/time & day-boundary utils
│   └── RuntimeEnvironment.kt            # Shared Robolectric/JVM test environment detection
├── widget
│   ├── CalendarAgendaViewsFactory.kt    # RemoteViews list factory with custom themes
│   ├── CalendarAgendaWidgetProvider.kt  # Hardened AppWidgetProvider broadcast receiver
│   ├── CalendarAgendaWidgetService.kt   # RemoteViewsService binding
│   ├── CalendarGlanceWidget.kt          # Jetpack Glance AppWidget + GlanceWidgetUpdater
│   ├── CalendarGlanceWidgetReceiver.kt  # GlanceAppWidgetReceiver host (date/time/provider)
│   └── WidgetThemeHelper.kt             # Dynamic & preset widget color resolution
└── worker
    ├── CalendarSyncScheduler.kt         # WorkManager unique periodic/immediate sync scheduler
    └── CalendarSyncWorker.kt            # CoroutineWorker syncing CalendarContract + Glance updates
```

### Persistence Rules
- **Event Cache**: Room Database (`calendar_events` table).
  - Primary key: `id` (e.g., `dev_${eventId}_${startMillis}`).
  - Indices: `startMillis`, `endMillis`, `calendarId` to guarantee $O(\log N)$ range queries.
- **Widget Customization**: `WidgetPreferencesManager` backed by `SharedPreferences` (mode `MODE_PRIVATE`).
  - Key settings: `bg_opacity`, `color_theme`, `text_density`, `days_horizon`, `selected_cal_ids`.

---

## 3. Tool Execution & CLI Protocols

1. **Automated Verification Harness (`harness.sh`)**:
   - Before completing tasks, always run `bash harness.sh` to execute the full test suite and compilation verification.
2. **Standard Compilation**:
   - Use the `compile_applet` tool to verify Android builds.
   - Keep build times fast; do NOT perform clean builds unless necessary.
3. **Robolectric JVM Tests**:
   - Execute via `gradle :app:testDebugUnitTest`.
   - Never write instrumented tests requiring `androidTest` or an emulator.
4. **Shell Commands**:
   - Always run Gradle commands as `gradle <task_name>`, never `./gradlew`.
5. **Compose UI Standards**:
   - Always apply `Modifier.testTag("snake_case_tag")` to all primary buttons, inputs, chips, and sliders.
   - Enforce 48dp minimum interactive touch target size.
   - Use `Icons.AutoMirrored` for directional icons (e.g. Back, Launch).
6. **Error Handling, Logging & Concurrency Standards**:
   - Never use `printStackTrace()` or silent `catch (_: Throwable)` blocks; log via `AppLogger` with standardized tags (`AppLogger.TAG_*`).
   - Always rethrow `CancellationException` inside `suspend` `try/catch` blocks.
   - Surface repository/sync failures via `OperationResult` -> `UiError` / `UiEvent.ShowSnackbar` with Retry or Settings actions.
   - Wrap multi-step Room clear+insert operations in `@Transaction` methods (`replaceSyncedEvents`, `replaceAllEvents`).

---

## 4. Widget Invalidation Protocol
Whenever customization settings or calendar data change:
```kotlin
val appWidgetManager = AppWidgetManager.getInstance(context)
val componentName = ComponentName(context, CalendarAgendaWidgetProvider::class.java)
val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
if (appWidgetIds.isNotEmpty()) {
    // 1. Invalidate list view data
    appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_agenda_list)
    // 2. Broadcast update intent to re-render root widget view
    val intent = Intent(context, CalendarAgendaWidgetProvider::class.java).apply {
        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
    }
    context.sendBroadcast(intent)
}
// Also update Jetpack Glance widgets via GlanceAppWidgetManager:
// GlanceWidgetUpdater.updateAllGlanceWidgets(context)
```
