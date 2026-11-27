# Privacy Policy

**Last updated**: 2026-11-26

**d.o.it Agenda Widget** is designed to keep your calendar schedule private and under your direct control.

---

## 1. Data We Access

- **Android Calendar Data (`READ_CALENDAR`, `WRITE_CALENDAR`)**: Used solely on your device to display your upcoming schedule in the companion app and home screen widgets, and to create, edit, or delete calendar events when you explicitly choose to do so.
- **Widget Appearance Preferences**: Stored locally in private Android `SharedPreferences` (`google_calendar_widget_prefs.xml`) to remember your chosen theme, opacity, typography density, horizon, and visible calendars.

---

## 2. On-Device Storage & Backups

- Calendar events are cached locally in an app-private Room SQLite database (`calendar_widget_db`) so home screen widgets render smoothly without blocking.
- The local event cache is **excluded** from Android Auto Backup and cloud/device-to-device data extraction rules so calendar contents are not copied to external backup providers by this app.

---

## 3. Network Access & Third Parties

- **No Analytics or Tracking**: d.o.it Agenda Widget includes zero analytics SDKs, advertising SDKs, crash telemetry uploaders, or third-party trackers.
- **No Calendar Data Leaves Your Device**: d.o.it Agenda Widget never transmits your calendar events, account names, or notes to any external server.
- **Optional Update Check (`INTERNET`)**: Only when you manually tap **Check for Updates** in the **About** sheet (and only if a repository is configured), the app sends a standard read-only HTTPS GET request to `https://api.github.com` to compare the latest release tag against the installed version. No personal or calendar data is included in this request.

---

## 4. Diagnostic Reports

- If you choose to tap **Copy Diagnostic Report** in **Sync Diagnostics**, account emails and log URLs/tokens are automatically redacted before the report is copied to your clipboard, and the clipboard entry is flagged as sensitive on Android 13+.
