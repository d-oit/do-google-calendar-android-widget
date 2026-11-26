# Security Policy

## Supported Versions

| Version | Supported |
| --- | --- |
| `0.1.x` (current) | Yes |

---

## Reporting a Vulnerability

Please **do not** open a public GitHub issue for security vulnerabilities.

Instead, report vulnerabilities privately via GitHub **Security Advisories** (*Security → Advisories → Report a vulnerability*) or contact the repository maintainers directly. Include:

- Affected version / commit SHA
- Clear reproduction steps or proof-of-concept
- Potential impact and any suggested remediation

Maintainers aim to acknowledge reports within 72 hours and provide a coordinated remediation timeline.

---

## Security & Privacy Architecture

Agenda Widget is built with a defense-in-depth, local-first security model:

- **Zero API Keys or Cloud Accounts**: Calendar access runs exclusively through Android's native `CalendarContract` (`READ_CALENDAR`, `WRITE_CALENDAR`).
- **Private Local Cache**: The Room SQLite database (`calendar_widget_db`) is explicitly excluded from Android Auto Backup (`backup_rules.xml`) and Cloud/Device Transfer (`data_extraction_rules.xml`).
- **Hardened Widget Broadcasts**: Exported widget click broadcasts (`CalendarAgendaWidgetProvider`) never trust raw URL extras from external intents; every click target is validated against the local Room event record and restricted to `content://com.android.calendar/` or `https://` URIs.
- **PII-Redacted Diagnostics & Logs**: `AppLogger` redacts email addresses, URL paths/queries, and bearer tokens before writing to logcat or the in-memory ring buffer. Copied diagnostic reports redact account emails by default and mark clipboard payloads as sensitive on Android 13+ (`ClipDescription.EXTRA_IS_SENSITIVE`).
- **Strict HTTPS Update Checks**: In-app update checks are strictly user-initiated, limited to `https://api.github.com/repos/<owner>/<repo>/releases/latest`, validate returned release links against `https://github.com/<owner>/<repo>/releases`, and never auto-download or execute APKs.
- **Release Hardening**: Release builds enable R8 code shrinking/obfuscation (`isMinifyEnabled = true`) and resource shrinking (`isShrinkResources = true`).
