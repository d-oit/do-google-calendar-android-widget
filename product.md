# Product Requirements Document (PRD): Customizable Google Calendar Home Screen Widget

## 1. Executive Summary & Vision

The **Google Calendar Agenda Widget** is an Android home screen widget and customization companion app designed to bring your real schedule directly to your launcher with zero friction. Unlike cloud-dependent solutions, this app requires **no API keys, no external server setups, and no login portals**. It interfaces directly with Android's native `CalendarContract` provider to access the user's authenticated Google accounts already synced on the device.

The app features a **Live Customization Studio** where users can configure the widget's visual theme (Material You dynamic colors, dark mode, high contrast), background opacity (from translucent glass to solid card), typography scale, date horizon, and multi-calendar filters with an immediate live preview.

---

## 2. Target Persona & Core Problem

- **Persona**: Android users who rely on Google Calendar for daily work and personal scheduling and want a glanceable, beautiful agenda on their home screen that blends with their wallpaper.
- **Pain Points**:
  - Stock widgets often lack customization (fixed opacity, fixed colors, fixed time horizons).
  - Third-party widgets often demand external API keys, OAuth tokens, or display fake/mockup data.
  - Inability to selectively filter which Google sub-calendars (e.g. Work vs. Personal vs. Holidays) appear on the widget.

---

## 3. Key Functional Specifications

### 3.1 Data Source & Security (Zero API Keys)
- **Direct OS Integration**: Uses Android `CalendarContract.Instances` and `CalendarContract.Calendars`.
- **Zero Mockup Data**: Displays strictly genuine calendar records from the device. If no events exist, presents an honest, clean empty state with a manual refresh action.
- **Pull-to-Refresh Force Sync**: Scrollable event agenda list supports a pull-down swipe gesture with Material 3 `PullToRefreshBox` that immediately force-syncs events from Android's `CalendarContract` ContentProvider.
- **Permissions Required**:
  - `android.permission.READ_CALENDAR`: To query events and calendar accounts.
  - `android.permission.WRITE_CALENDAR`: To add/delete events when requested by the user.

### 3.2 Widget Customization Engine
The widget's appearance and content are persisted in `WidgetPreferencesManager` (using `SharedPreferences`) for synchronous, ultra-fast access by the widget's `RemoteViewsService`:

1. **Background Opacity**:
   - `100%`: Solid Material card.
   - `80%`: Subtle translucency (default).
   - `50%`: Medium frost glassmorphism.
   - `20%`: High translucency.
   - `0%`: Completely clear / borderless minimalist layout.

2. **Color Schemes**:
   - `Material You Dynamic`: Tints backgrounds and accents using the system wallpaper palette (Android 12+).
   - `Google Blue`: Classic Google Calendar royal blue (`#1A73E8`).
   - `Midnight Slate`: Deep obsidian dark theme (`#1E1E1E` background, `#90CAF9` accents).
   - `Emerald Green`: Nature-inspired theme (`#137333` / `#34A853`).
   - `Sunset Warm`: High-contrast amber/coral (`#E65100` / `#FB8C00`).

3. **Typography & Density**:
   - `Compact`: Reduced padding, 11sp times, 12sp titles (fits 6–8 events in small widgets).
   - `Regular`: Balanced spacing, 12sp times, 14sp titles (default).
   - `Large`: High legibility, 14sp times, 16sp titles (ideal for accessibility or large grids).

4. **Days Horizon**:
   - `Today Only` (1 day)
   - `2 Days`
   - `3 Days`
   - `7 Days` (1 week)
   - `14 Days` (2 weeks)
   - `30 Days` (Full month)

5. **Multi-Calendar Filtering**:
   - Automatically discovers all Google Calendar accounts on the device.
   - Provides checkboxes for each calendar (e.g., `user@gmail.com`, `Holidays in United States`, `Work`).
   - Master toggle: "Show All Google Calendars" vs. "Custom Selection".

6. **Display Options**:
   - Show/hide event location and video meeting links.
   - Show/hide date group header chips ("TODAY", "TOMORROW", "FRI, NOV 7").
   - Show/hide past completed events of the current day.

---

## 4. User Journeys & Screens

### Journey 1: Setup & First Launch
1. User launches the app.
2. System checks `READ_CALENDAR` permission.
3. If not granted, the app presents a welcoming "Connect Device Google Calendar" card.
4. User taps "Grant Access" -> system permission dialog opens -> granted.
5. App immediately queries device calendars and displays real events in the Live Preview.

### Journey 2: Customizing the Home Screen Widget
1. User adjusts the Opacity slider (e.g. from 80% to 50%).
2. Live Widget Preview card immediately reflects the glassmorphism effect in real time.
3. User selects "Material You Dynamic" and sets Days Horizon to "7 Days".
4. User taps "Apply to Home Screen Widget".
5. Broadcast notification `notifyAppWidgetViewDataChanged` is dispatched to the launcher widget.
6. Home screen widget reloads with the new styling instantaneously.

### Journey 3: Adding a Real Event
1. User taps the Floating Action Button `+` (in app or on widget).
2. Add Event dialog opens with Title, Location, Date, Time, and Color Category.
3. User taps "Save".
4. Repository inserts event directly into `CalendarContract.Events` under the chosen Google Calendar.
5. Event appears in Google Calendar and in the home screen widget simultaneously.

---

## 5. Non-Functional Requirements & Performance
- **Zero Binder Lags**: `CalendarAgendaViewsFactory` caches the current list in memory and only queries the indexed Room database or ContentProvider during `onDataSetChanged()`.
- **Battery Efficiency**: No continuous background polling. Widget updates on launcher demand, periodic 30-minute system alarms, or explicit user action (refresh button / event creation).
- **Responsive Viewports**: Supports compact phones (<600dp width) with tabbed navigation and wide screens / tablets (>=600dp width) with dual-pane canvas.
