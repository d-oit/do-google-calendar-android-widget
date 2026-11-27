# Test Management & Quality Assurance Playbook

This playbook defines the test architecture, quality gates, coverage tracking, and release QA sign-off process for **d.o.it Agenda Widget**.

---

## 1. Testing Philosophy & Pyramid

d.o.it Agenda Widget enforces a **fast, deterministic, JVM-first testing strategy**:

1. **Tier 1 — Static Safety & Governance Gates (`harness.sh --static-only`)**
   - Verifies required architecture & workflow files exist.
   - Blocks forbidden mock/seed data (`generateSeedEvents`), raw `printStackTrace()`, silent `catch (_: Throwable)`, and hardcoded Google API keys.
   - Validates Semantic Versioning syntax in `version.properties`.
2. **Tier 2 — Pure JVM Unit Tests**
   - Validates version parity (`ReleaseVersionTest`), utility formatting, and pure Kotlin logic in milliseconds.
3. **Tier 3 — Robolectric, Room, Compose, WorkManager & Glance CUJ Tests**
   - Executes Critical User Journeys (`CUJ_01`–`CUJ_33`) against real Android framework APIs simulated via Robolectric (in-memory Room, fake `CalendarContract` `ContentProvider`, Compose UI test rules, `TestListenableWorkerBuilder`, and `runGlanceAppWidgetUnitTest`) with zero emulator flakiness.
4. **Tier 4 — Static Analysis, Coverage & Supply Chain**
   - **Android Lint (`:app:lintDebug`)**: Detects Android API, accessibility, resource, and correctness issues.
   - **JaCoCo Coverage (`:app:jacocoDebugUnitTestReport`)**: Produces XML and HTML coverage metrics for every PR and release.
   - **Dependency Review (`actions/dependency-review-action`)**: Blocks pull requests introducing high/critical dependency vulnerabilities.
5. **Tier 5 — Pre-Release Manual Device Smoke QA**
   - Executed via the GitHub **Release QA Test Plan** issue template prior to tagging a release.

---

## 2. Automated Test Suite Traceability Matrix

| Test Suite | Scope & Domain | Coverage IDs |
| --- | --- | --- |
| `ReleaseVersionTest` | SemVer 2.0.0 compliance & `version.properties` ↔ `BuildConfig` parity | Release Governance |
| `ExampleRobolectricTest` | App resource context, date headers, and time formatting | Core Utilities |
| `CalendarViewportTestHarness` | Phone/Foldable/Tablet viewports, search & filter, CRUD, Room caching, RemoteViews rendering, opacity/themes, `CalendarColorMapper`, pull-to-refresh, widget refresh, `AppLogger` & Snackbar error recovery, atomic Room transactions | `CUJ_01` – `CUJ_22` |
| `CalendarWorkManagerGlanceTest` | Periodic/immediate WorkManager scheduling, worker sync & retry, Jetpack Glance empty/populated rendering, last-sync timestamp indicator | `CUJ_23` – `CUJ_27` |
| `CalendarSyncDiagnosticsRobolectricTest` | Disabled `SYNC_EVENTS` detection & repair, zero-raw-events classification, horizon expansion, multi-day resilience, ContentObserver reactivity, PII-redacted Diagnostics UI sheet | `CUJ_28` – `CUJ_33` |
| `HardeningI18nAndUpdateTest` | Widget broadcast click verification, `LogSanitizer` & `AppLogger` ring buffer, locale-safe filtering & `es`/`fr`/`de` parity, midnight rollover, 12h/24h formatting, HTTPS `AppUpdateChecker`, Glance syncing state & About sheet | Security, i18n & Release |

---

## 3. Running Tests Locally

> [!NOTE]
> Always invoke `gradle` directly (not `./gradlew`) to match both the AI Studio build environment and GitHub Actions (`gradle/actions/setup-gradle`).

```bash
# Full local verification harness (static checks + compile + unit/Robolectric tests + Android Lint)
bash harness.sh

# Static safety & version structure checks only
bash harness.sh --static-only

# Unit & Robolectric tests only
gradle :app:testDebugUnitTest

# Unit & Robolectric tests + JaCoCo XML/HTML coverage report
gradle :app:jacocoDebugUnitTestReport

# Android Lint only
gradle :app:lintDebug
```

### Local Report Output Paths
- **JUnit XML**: `app/build/test-results/testDebugUnitTest/`
- **JUnit HTML**: `app/build/reports/tests/testDebugUnitTest/index.html`
- **JaCoCo XML**: `app/build/reports/jacoco/jacocoDebugUnitTestReport/jacocoDebugUnitTestReport.xml`
- **JaCoCo HTML**: `app/build/reports/jacoco/jacocoDebugUnitTestReport/html/index.html`
- **Android Lint HTML**: `app/build/reports/lint-results-debug.html`

---

## 4. GitHub Actions CI Quality Gates

Every pull request targeting `main` runs `.github/workflows/ci.yml`:

| Gate | Trigger | Output / Enforcement |
| --- | --- | --- |
| Static Safety & Version Gate | Push & PR | Fails build on safety violation or malformed `version.properties` |
| Unit & Robolectric Tests | Push & PR | Inline PR annotations via JUnit reporter; fails on any test failure |
| JaCoCo Coverage Report | Push & PR | Markdown coverage table in Job Summary + downloadable HTML/XML artifact (14d) |
| Android Lint Debug | Push & PR | Fails on lint errors + downloadable HTML/XML report artifact (14d) |
| Dependency Vulnerability Review | PR only | Fails on high/critical CVEs and posts summary comment on failure |

---

## 5. Defect Triage & Release Sign-Off Criteria

| Severity | Definition | Release Policy |
| --- | --- | --- |
| **P0** | Crash, data corruption, broken calendar sync, or security issue | **Hard Release Blocker** — must be fixed with a regression test |
| **P1** | Major feature or widget customization defect | **Release Blocker** — fix or explicitly defer with maintainer sign-off |
| **P2** | Minor UI or edge-case behavior | May be scheduled for next patch release |
| **P3** | Cosmetic polish | Tracked in backlog |

Before tagging any release (starting at `v0.1.0`), open a **Release QA Test Plan** issue, verify all automated CI gates are green, complete the manual smoke matrix, and link the closed QA issue in the release discussion.
