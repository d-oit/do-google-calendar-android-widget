# Google Calendar API Sync & Multi-Calendar Color Agenda

Direct Google Calendar API integration with Google Sign-In, allowing the app to fetch genuine Google Calendar events from `dmmotec@gmail.com` and all sub-calendars. Displays each event with its authentic synced Google Calendar color strip and calendar name badge.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following confirmed user choices govern the execution:

- **Confirmed Decision 1 (Google Calendar API & OAuth Sync)**: Use Google Sign-In / OAuth (`https://www.googleapis.com/auth/calendar.readonly`) to query Google Calendar API endpoints directly (`calendarList` and `events`). This guarantees events load directly from the user's real account without needing an account logged into the Android emulator's OS settings.
- **Confirmed Decision 2 (Multi-Calendar Color Differentiation)**: Display a prominent synced color bar strip on the left of each event card and a calendar badge pill indicating which calendar the event belongs to (e.g. "Work", "Personal", "Family").
- **Confirmed Decision 3 (Dual Sync Fallback)**: Support direct Google Calendar API sync as the primary cloud data source, while maintaining native `CalendarContract` support when running on devices with native system accounts.

---

## 1. Overview & Core Concept

- **What It Does**: Connects directly to Google Calendar using Google OAuth and the Google Calendar v3 REST API. Synchronizes all calendars (`users/me/calendarList`), retrieves real upcoming events with their native background colors, caches them in Room, and updates the Agenda UI and home screen widget.
- **Target Audience**: Users needing instant, accurate Google Calendar agenda sync on web emulators and physical Android devices alike.
- **Key Value**: Solves the empty calendar issue in cloud emulators by directly retrieving live account schedules and visually distinguishing events by calendar color.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **Sign-In & Automatic Sync**:
   - Header bar displays a Google Sign-In button / account status.
   - Tapping "Sign in with Google" initiates the Google OAuth consent flow for Calendar read access (`calendar.readonly`).
   - Upon authentication, the app automatically fetches all user calendars and upcoming events.
2. **Agenda List with Calendar Colors**:
   - Date Group Headers: `TODAY, NOV 5`, `TOMORROW, NOV 6`, etc.
   - Event Cards:
     - Left indicator: 5dp rounded vertical color strip matching the calendar's Google Calendar `backgroundColor`.
     - Calendar Badge: Pill with a colored dot and calendar name (e.g. `Personal • Green`, `Work • Blue`).
     - Event Title: 14sp SemiBold text.
     - Time Range: Formatted time or "All day" badge.
     - Location: Location details if present.
3. **Home Screen Widget**:
   - RemoteViews list displays the event's synced color stripe and calendar tag.
   - Immediate refresh on account sync or tap.

---

## 3. Technical Architecture & Data Strategy

### Architecture & Component Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                 Google Calendar v3 REST API                 │
│   - GET /users/me/calendarList (all sub-calendars & colors) │
│   - GET /calendars/{calendarId}/events (upcoming instances) │
└──────────────────────────────┬──────────────────────────────┘
                               │ (OAuth Bearer Token)
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                     GoogleCalendarApi                       │
│  - Retrofit + Moshi REST client                             │
│  - Fetches calendar colors (backgroundColor, foregroundColor)│
│  - Fetches events (summary, start, end, location, htmlLink)  │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                     CalendarRepository                      │
│  - Maps Google API responses to EventEntity                 │
│  - Preserves calendar colorHex & calendarId displayName     │
│  - Batch inserts into Room DAO (calendar_events)            │
└──────────────┬──────────────────────────────┬───────────────┘
               │                              │
               ▼                              ▼
┌──────────────────────────────┐ ┌────────────────────────────┐
│      CalendarViewModel       │ │ CalendarAgendaViewsFactory │
│  - Manages sign-in state     │ │   (Widget RemoteViews)     │
│  - Reactive agenda uiState   │ └────────────┬───────────────┘
└──────────────┬───────────────┘              │
               ▼                              ▼
┌──────────────────────────────┐ ┌────────────────────────────┐
│      AgendaScreen & Cards    │ │ CalendarAgendaWidgetProvider│
│  - Color bar & calendar pill │ │   (Home Screen Widget)     │
└──────────────────────────────┘ └────────────────────────────┘
```

### Component Updates

1. **OAuth Setup**:
   - Request `set_up_oauth` for `https://www.googleapis.com/auth/calendar.readonly`.
2. **`GoogleCalendarApi.kt`**:
   - Add `getCalendarList()` endpoint to query `users/me/calendarList` and retrieve all user calendars with their `backgroundColor` and `summary`.
   - Update `getUpcomingEvents()` to accept bearer authorization headers and query per calendar.
3. **`CalendarRepository.kt`**:
   - Add `syncFromGoogleCalendarApi(token: String)` method that pulls all calendars, downloads events, assigns their colors, and stores them in Room.
4. **`EventItemCard.kt`**:
   - Render the synced color bar strip and calendar name badge.
5. **`CalendarAgendaViewsFactory.kt` & `WidgetPreviewCard.kt`**:
   - Reflect the synced calendar colors on widget item strips and times.
