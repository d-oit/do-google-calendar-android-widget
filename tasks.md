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


