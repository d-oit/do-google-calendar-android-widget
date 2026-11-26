# Contributing to Agenda Widget

Thank you for contributing to **Agenda Widget**! Please follow these engineering and quality guidelines so every change stays fast, private, accessible, and reliable.

---

## Core Engineering Rules

1. **Native OS Calendar Only — Zero API Keys**:
   - All calendar data must be read and written via Android's `CalendarContract`.
   - Never add Google Cloud API keys, Firebase SDKs, or external OAuth flows.
2. **Absolute Zero Mockup Data**:
   - Never seed or hardcode fake calendar events anywhere in production code.
   - Empty schedules must show honest, localized empty states with real sync/add actions.
3. **Structured Logging & Privacy**:
   - Always log through `com.example.util.AppLogger` (`AppLogger.d/i/w/e`).
   - Never call `android.util.Log.*` directly outside `AppLogger.kt`, never call `printStackTrace()`, and never log raw emails, URL query strings, or secrets.
4. **Internationalization (i18n)**:
   - Do not hardcode user-facing UI text in Kotlin/Compose files.
   - Add new string keys to `values/strings.xml`, `values-es/strings.xml`, `values-fr/strings.xml`, and `values-de/strings.xml` with identical format specifiers.
   - Format dates and times exclusively via `CalendarDateFormatter`.
5. **Accessibility & Testing**:
   - Interactive Compose and widget touch targets must be at least `48.dp`.
   - Add `Modifier.testTag("snake_case_tag")` to primary interactive elements.
   - Write fast JVM / Robolectric unit tests under `app/src/test/` (do not add emulator-dependent `androidTest` suites).

---

## Local Verification Before Opening a PR

```bash
# Always run the full verification harness before pushing
bash harness.sh

# Generate coverage report if adding/modifying business logic
gradle :app:jacocoDebugUnitTestReport

# Confirm release compilation succeeds
gradle :app:compileReleaseKotlin
```

> Always invoke `gradle` directly rather than `./gradlew`.

---

## Branching & Commit Conventions

- Branch from `main` using descriptive names (`feat/...`, `fix/...`, `docs/...`, `chore/...`).
- Follow Conventional Commits where practical (`feat:`, `fix:`, `docs:`, `test:`, `refactor:`, `chore:`).
- Update `CHANGELOG.md` under `## [Unreleased]` for user-visible changes.
- Complete the checklist in `.github/PULL_REQUEST_TEMPLATE.md` when opening a pull request.
