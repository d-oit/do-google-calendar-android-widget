# Implementation Tasks: Customizable Google Calendar Home Screen Widget

## Phase 1: Workflow Harness & Project Documentation
- [x] **Task 1.1**: Author `product.md` (Product Requirements Document).
- [x] **Task 1.2**: Author `AGENTS.md` (Operational protocols, architecture rules, zero-mockup guardrails).
- [x] **Task 1.3**: Author `harness.sh` (Automated build, test, and verification script).
- [x] **Task 1.4**: Author `tasks.md` (Checklist tracking and validation criteria).

## Phase 2: Elimination of API Keys & Mockup Relics
- [x] **Task 2.1**: Remove all Google Calendar API key references, `.env` API keys, and dummy strings.
- [x] **Task 2.2**: Ensure zero mock/seed events remain anywhere in the app or startup lifecycle.
- [x] **Task 2.3**: Verify that empty schedules show an honest, clean empty state with refresh capability.

## Phase 3: Widget Preferences & Multi-Calendar Architecture
- [x] **Task 3.1**: Create `WidgetPreferencesManager` in `com.example.data.pref`:
  - Background Opacity (`0%`, `20%`, `50%`, `80%`, `100%`)
  - Color Theme (`Dynamic M3`, `Classic Blue`, `Midnight Dark`, `Emerald`, `Sunset`)
  - Typography Scale (`Compact`, `Regular`, `Large`)
  - Days Horizon (`1`, `2`, `3`, `7`, `14`, `30` days)
  - Selected Calendar IDs (`Set<Long>`)
  - Display toggles (locations, day chips, past events)
- [x] **Task 3.2**: Update `CalendarRepository`:
  - Query all Google accounts & calendars via `CalendarContract.Calendars`.
  - Filter `CalendarContract.Instances` by user-selected calendar IDs and day horizon.
  - Implement real event insertion and deletion.

## Phase 4: Customizable RemoteViews Widget Engine
- [x] **Task 4.1**: Update widget drawables & layouts to support customizable opacity and color accents:
  - Add dynamic background color and opacity tinting in `CalendarAgendaWidgetProvider`.
- [x] **Task 4.2**: Update `CalendarAgendaViewsFactory`:
  - Apply `WidgetPreferencesManager` settings to each RemoteViews list item.
  - Format times according to typography density.
  - Apply theme color accents to date chips and item stripes.

## Phase 5: In-App Live Customization Studio UI
- [x] **Task 5.1**: Build `WidgetCustomizerControls.kt`:
  - Opacity slider with live percentage indicator.
  - Color theme selection chips.
  - Typography density selector (`Compact`, `Regular`, `Large`).
  - Days Horizon segmented chips.
  - Multi-select calendar checklist dialog or expandable sheet.
  - Display option switches.
- [x] **Task 5.2**: Update `WidgetPreviewCard.kt`:
  - Re-render live preview synchronously with user's selected preferences.
  - Show simulated wallpaper background to visualize opacity changes.
  - "Add Widget to Home Screen" button to push immediate broadcast updates.
- [x] **Task 5.3**: Update `MainActivity.kt`:
  - Integrate Live Customization Studio as primary tab/pane.
  - Wire up permission requests, real event sync, and widget invalidation.

## Phase 6: Automated Testing & Verification
- [x] **Task 6.1**: Update `CalendarViewportTestHarness.kt` to test widget customization preferences and multi-calendar filtering.
- [x] **Task 6.2**: Execute `bash harness.sh` to verify Kotlin compilation, Robolectric tests, and asset validation.
- [x] **Task 6.3**: Run `compile_applet` to confirm successful build.

## Phase 7: EventListLazyColumn & Friendly Empty State
- [x] **Task 7.1**: Implement `EventListLazyColumn` component in `com.example.ui.components` displaying event title, time, and color indicators.
- [x] **Task 7.2**: Implement `FriendlyEmptyEventsState` inside the LazyColumn displaying contextual messages for the selected timeframe ("Today", "Tomorrow", "Work", or horizon), status badge, and action buttons ("Add Event", "Sync Now").
- [x] **Task 7.3**: Integrate `EventListLazyColumn` into `MainActivity.kt` `AgendaScheduleContent`.
- [x] **Task 7.4**: Add `CUJ_13` test to `CalendarViewportTestHarness.kt` and verify via `bash harness.sh`.

## Phase 8: Active Fetching Progress Indicator
- [x] **Task 8.1**: Expose `isFetching` in `CalendarRepository` and propagate through `CalendarViewModel` / `CalendarUiState`.
- [x] **Task 8.2**: Add centered `CircularProgressIndicator` with elevated Surface card in `MainActivity.kt` (`CalendarMainScreen`) displayed whenever `CalendarRepository` is actively fetching data.
- [x] **Task 8.3**: Add unit & UI state verification tests in `CalendarViewportTestHarness.kt` and verify via `bash harness.sh`.

## Phase 9: CalendarContract Event Color Mapping Utility & UI Integration
- [x] **Task 9.1**: Create `CalendarColorMapper` utility in `com.example.util` supporting extraction of `EVENT_COLOR`, `CALENDAR_COLOR`, `DISPLAY_COLOR`, and `EVENT_COLOR_KEY` from CalendarContract, with Google Calendar 11-color palette and `CalendarContract.Colors` table lookups.
- [x] **Task 9.2**: Integrate `CalendarColorMapper` into `CalendarRepository` (`queryInstances`, `fetchDirectEventsInternal`, `queryAvailableCalendars`) to read and map native CalendarContract colors.
- [x] **Task 9.3**: Implement `EventUiColorScheme` in `CalendarColorMapper` and apply it to UI elements in `EventItemCard.kt` (accent bar, card container background, border stroke, calendar badge pill, dot, time text, and icons).
- [x] **Task 9.4**: Apply `CalendarColorMapper` across widget list items in `CalendarAgendaViewsFactory.kt`, `WidgetPreviewCard.kt`, and `EventDetailDialog.kt`.
- [x] **Task 9.5**: Add unit test `CUJ_15_calendar_color_mapper_reads_properties_and_maps_to_ui` in `CalendarViewportTestHarness.kt`.
- [x] **Task 9.6**: Verify build, zero-mockup rule, and test suite via `harness.sh` and `compile_applet`.

## Phase 10: Scrollable Event List Pull-to-Refresh Gesture
- [x] **Task 10.1**: Wrap `EventListLazyColumn` inside Material 3 `PullToRefreshBox` with `PullToRefreshDefaults.Indicator` and custom test tags.
- [x] **Task 10.2**: Connect pull-to-refresh swipe gesture to trigger force-syncing calendar data from Android `CalendarContract` ContentProvider (`viewModel.onSyncNow()` / `repository.syncEvents(forceRefresh = true)`).
- [x] **Task 10.3**: Propagate `isRefreshing = uiState.isSyncing` and `onRefresh = onSyncNow` from `AgendaScheduleContent` in `MainActivity.kt`.
- [x] **Task 10.4**: Add `CUJ_16_pull_to_refresh_gesture_force_syncs_calendar_data` in `CalendarViewportTestHarness.kt` to verify pull-to-refresh trigger and force-sync pipeline.
- [x] **Task 10.5**: Verify full harness suite via `bash harness.sh` and `compile_applet`.

## Phase 11: Widget Header Manual Sync/Refresh Button
- [x] **Task 11.1**: Upgrade `widget_calendar_agenda.xml` header refresh and add buttons to 48dp accessible touch targets and add `widget_refresh_progress` ProgressBar.
- [x] **Task 11.2**: Update `CalendarAgendaWidgetProvider` to toggle syncing indicator, check calendar permissions, and force-sync `CalendarContract` data provider via `syncEvents(forceRefresh = true)` on `ACTION_REFRESH_WIDGET`.
- [x] **Task 11.3**: Update `WidgetPreviewCard.kt` to display syncing spinner when `isSyncing` is active for 1:1 widget parity.
- [x] **Task 11.4**: Add `CUJ_20_widget_header_manual_refresh_force_syncs_from_provider` in `CalendarViewportTestHarness.kt` and verify via `bash harness.sh`.

## Phase 12: Error Handling, Structured Logging, Reliability & Verification Harness
- [x] **Task 12.1**: Implement `AppLogger` wrapper around `android.util.Log` with `BuildConfig.DEBUG` gating, standardized domain tags, and JVM unit-test safety.
- [x] **Task 12.2**: Introduce `AppError` / `OperationResult` and wire `UiError` + `UiEvent.ShowSnackbar` with Material 3 `SnackbarHost` (*Retry* / *Settings* actions) in `CalendarViewModel` and `MainActivity`.
- [x] **Task 12.3**: Upgrade `EventDao` with `@Transaction` atomic replacement methods (`replaceSyncedEvents`, `replaceAllEvents`), switch `WidgetPreferencesManager` to non-blocking `apply()`, make `CalendarAgendaViewsFactory` snapshot-safe, and add `ThreadLocal` date formatters + `CoroutineExceptionHandler` across scopes.
- [x] **Task 12.4**: Upgrade `harness.sh` with static safety checks (`printStackTrace`, silent `catch (_: Throwable)`, hardcoded keys) and Android Lint (`:app:lintDebug`), update `AGENTS.md`, and add `CUJ_21` & `CUJ_22` Robolectric tests.

## Phase 13: WorkManager Periodic Calendar Sync & Jetpack Glance Widget
- [x] **Task 13.1**: Add Jetpack Glance (`androidx.glance:glance-appwidget`, `glance-material3`) and WorkManager (`androidx.work:work-runtime-ktx`) dependencies plus ProGuard keep rules for `ActionCallback`.
- [x] **Task 13.2**: Implement `CalendarGlanceWidget`, `CalendarGlanceWidgetReceiver`, `RefreshCalendarGlanceAction`, and `GlanceWidgetUpdater` using `GlanceAppWidgetManager` and `PreferencesGlanceStateDefinition`.
- [x] **Task 13.3**: Implement `CalendarSyncWorker` (`CoroutineWorker`) and `CalendarSyncScheduler` to periodically sync `CalendarContract` into Room and trigger `GlanceAppWidgetManager` widget updates.
- [x] **Task 13.4**: Add `CUJ_23`–`CUJ_26` Robolectric, WorkManager `TestListenableWorkerBuilder`, and `runGlanceAppWidgetUnitTest` tests in `CalendarWorkManagerGlanceTest.kt` and verify via `bash harness.sh`.

## Phase 14: Widget Header Last Successful Sync Indicator
- [x] **Task 14.1**: Persist `lastSuccessfulSyncMillis` in `WidgetPreferencesManager` on successful `CalendarRepository.syncEvents` completion and add `CalendarRepository.formatLastSyncIndicator`.
- [x] **Task 14.2**: Display status dot, last sync timestamp label, and descriptive refresh button tooltip/contentDescription across Classic RemoteViews widget (`widget_calendar_agenda.xml`, `CalendarAgendaWidgetProvider`), Jetpack Glance widget (`CalendarGlanceWidget`), and Live Preview (`WidgetPreviewCard`).
- [x] **Task 14.3**: Verify persisted timestamp & widget header rendering in `CUJ_26` and `CUJ_27`, and run `bash harness.sh`.

## Phase 15: Material 3 Empty Calendar State Illustration
- [x] **Task 15.1**: Generate Material 3 expressive empty calendar illustration drawable (`res/drawable/img_empty_calendar.jpg`) matching the app's Material You / Google Blue color palette.
- [x] **Task 15.2**: Integrate `img_empty_calendar` into `FriendlyEmptyEventsState` (`EventListLazyColumn`), `WidgetPreviewCard`, and `CalendarGlanceWidget` when no upcoming events exist.
- [x] **Task 15.3**: Verify illustration node presence in `CUJ_26` and run full `bash harness.sh` + `compile_applet`.

## Phase 16: GitHub Release & Test Management (Starting at v0.1.0)
- [x] **Task 16.1**: Initialize centralized Semantic Versioning in `version.properties` (`0.1.0`, `versionCode = 1`), wire into `app/build.gradle.kts`, and add `ReleaseVersionTest`.
- [x] **Task 16.2**: Configure JaCoCo (`jacocoDebugUnitTestReport`) in `app/build.gradle.kts` with Robolectric-safe settings and generated-code exclusions.
- [x] **Task 16.3**: Author GitHub Actions CI (`.github/workflows/ci.yml`) and Release Pipeline (`.github/workflows/release.yml`) with tag/manual dispatch, signed Release APK/AAB + debug fallback, SHA-256 checksums, JUnit reporting, JaCoCo summary, Android Lint, and dependency review.
- [x] **Task 16.4**: Add GitHub governance files (`CHANGELOG.md`, `docs/RELEASE.md`, `docs/TESTING.md`, `.github/release.yml`, `.github/dependabot.yml`, PR template, Bug/Feature/Release QA Test Plan issue forms).
- [x] **Task 16.5**: Upgrade `harness.sh` with `--static-only` mode and version metadata validation, then verify via `bash harness.sh`, `gradle :app:jacocoDebugUnitTestReport`, and `compile_applet`.
