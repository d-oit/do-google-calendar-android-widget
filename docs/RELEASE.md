# Release Management Playbook

This document defines the standardized GitHub release lifecycle for **d.o.it Agenda Widget**, starting from version **0.1.0**.

---

## 1. Versioning Policy (SemVer 2.0.0)

Version metadata is managed in a single source of truth at the repository root: `version.properties`.

```properties
VERSION_MAJOR=0
VERSION_MINOR=1
VERSION_PATCH=0
VERSION_CODE=1
GITHUB_REPOSITORY=owner/repo
```

- **Pre-1.0 (`0.x.y`)**: Initial stabilization and feature iteration (`0.1.0`, `0.2.0`, `0.2.1`).
- **Production (`1.0.0+`)**:
  - `MAJOR`: Incompatible behavioral or storage changes.
  - `MINOR`: Backwards-compatible features or widget capabilities.
  - `PATCH`: Backwards-compatible bug fixes and reliability patches.
- **`VERSION_CODE`**: Strictly monotonically increasing integer (`1`, `2`, `3`, ...) required by Android and Google Play for every uploaded build.
- **`GITHUB_REPOSITORY`**: Optional `owner/repo` slug embedded into `BuildConfig.GITHUB_REPOSITORY` for the user-initiated HTTPS **Check for Updates** button in the in-app About sheet. Leave empty if no public repository is configured yet.
- **Git Tag Format**: `v<MAJOR>.<MINOR>.<PATCH>` (or pre-release `v<MAJOR>.<MINOR>.<PATCH>-rc.1`).

The Release workflow enforces strict parity between the Git tag and `version.properties` so mismatched artifacts can never be published. Release builds enable R8 code minification (`isMinifyEnabled = true`) and resource shrinking (`isShrinkResources = true`), and signed releases archive `agenda-widget-<version>-mapping.txt` for crash deobfuscation.

---

## 2. Signing & GitHub Secrets Setup

`app/build.gradle.kts` reads release signing credentials from environment variables (`KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`) with key alias `upload`.

To enable **Signed Release APK & AAB** builds in GitHub Actions, configure these three Repository Secrets under **Settings → Secrets and variables → Actions**:

| GitHub Secret | Description |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded contents of your Play upload keystore (`base64 -w 0 my-upload-key.jks`) |
| `ANDROID_STORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_PASSWORD` | Key password for alias `upload` |

### Graceful Fallback Mode
If the three signing secrets above are not yet configured, the release pipeline does **not** fail; instead it:
1. Emits a clear warning in the workflow logs and release notes.
2. Builds a verified Debug APK (`agenda-widget-<version>-debug-fallback.apk`) using the repository's base64 debug keystore.
3. Attaches the fallback APK and SHA-256 checksums to the GitHub Release so testing and early distribution work immediately on `v0.1.0`.

> [!IMPORTANT]
> Never commit production `.jks` / `.keystore` files or raw passwords to Git. Temporary decoded keystores in GitHub Actions are stored in `$RUNNER_TEMP` and deleted in an `if: always()` cleanup step.

---

## 3. Step-by-Step Release Process

### Step 1: Prepare the Release Commit
1. Bump `version.properties` (and optionally insert a dated changelog heading) using the helper script:
   ```bash
   # Preview next patch/minor/major bump
   bash scripts/bump-version.sh patch --dry-run

   # Apply bump and roll CHANGELOG.md section
   bash scripts/bump-version.sh patch --update-changelog
   ```
2. Review `CHANGELOG.md` to ensure all completed items are documented under the new version heading.
3. Run local verification:
   ```bash
   bash harness.sh
   gradle :app:jacocoDebugUnitTestReport :app:compileReleaseKotlin
   ```
4. Merge the release preparation PR into `main` after all CI checks are green.

### Step 2: Execute QA Test Plan
1. Open a **Release QA Test Plan** issue on GitHub (`.github/ISSUE_TEMPLATE/qa_test_plan.yml`) targeting the release commit SHA.
2. Complete all automated and manual smoke checks and verify zero open P0/P1 blockers.

### Step 3: Optional Dry-Run Verification
1. Open **Actions → Android Release Pipeline → Run workflow**.
2. Set `tag_name` to `v0.1.0` and `dry_run` to `true`.
3. Inspect the generated artifacts, JaCoCo coverage report, Lint report, and `checksums-sha256.txt` without publishing a public release.

### Step 4: Cut & Publish the Release
Choose either supported trigger:

- **Option A — Tag Push (Standard)**:
  ```bash
  git checkout main
  git pull origin main
  git tag -a v0.1.0 -m "Release v0.1.0"
  git push origin v0.1.0
  ```
- **Option B — Manual Workflow Dispatch**:
  - Go to **Actions → Android Release Pipeline → Run workflow**.
  - Provide `tag_name` (e.g. `v0.1.0`), toggle `prerelease` if needed, leave `dry_run` unchecked, and click **Run workflow**.

---

## 4. Verifying Published Artifacts

Every GitHub Release attaches `checksums-sha256.txt`. Consumers and QA reviewers can verify downloaded binaries locally:

```bash
sha256sum -c checksums-sha256.txt
```

---

## 5. Hotfix & Rollback Procedure

- **Hotfix**: Branch from the affected tag (`git checkout -b hotfix/0.1.1 v0.1.0`), fix the defect with a regression test, increment `VERSION_PATCH` and `VERSION_CODE` in `version.properties`, update `CHANGELOG.md`, merge to `main`, and tag `v0.1.1`.
- **Rollback**: Mark a defective GitHub Release as a pre-release or remove faulty binaries with an advisory note, then ship a forward-fix patch release (`VERSION_CODE` must always move forward for Android upgrades).
