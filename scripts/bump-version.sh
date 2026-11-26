#!/usr/bin/env bash
# ==============================================================================
# scripts/bump-version.sh: Automated SemVer & Android VERSION_CODE bump helper
#
# Usage:
#   bash scripts/bump-version.sh <major|minor|patch> [--dry-run] [--update-changelog]
# ==============================================================================

set -euo pipefail

BUMP_TYPE="${1:-}"
DRY_RUN=false
UPDATE_CHANGELOG=false

if [ -z "$BUMP_TYPE" ]; then
    echo "Usage: bash scripts/bump-version.sh <major|minor|patch> [--dry-run] [--update-changelog]" >&2
    exit 1
fi

shift || true
for arg in "$@"; do
    case "$arg" in
        --dry-run) DRY_RUN=true ;;
        --update-changelog) UPDATE_CHANGELOG=true ;;
        *)
            echo "Unknown option: $arg" >&2
            exit 1
            ;;
    esac
done

VERSION_FILE="version.properties"
if [ ! -f "$VERSION_FILE" ]; then
    echo "ERROR: $VERSION_FILE not found in $(pwd)" >&2
    exit 1
fi

VERSION_MAJOR=$(grep -E '^VERSION_MAJOR=' "$VERSION_FILE" | cut -d'=' -f2 | tr -d '[:space:]')
VERSION_MINOR=$(grep -E '^VERSION_MINOR=' "$VERSION_FILE" | cut -d'=' -f2 | tr -d '[:space:]')
VERSION_PATCH=$(grep -E '^VERSION_PATCH=' "$VERSION_FILE" | cut -d'=' -f2 | tr -d '[:space:]')
VERSION_CODE=$(grep -E '^VERSION_CODE=' "$VERSION_FILE" | cut -d'=' -f2 | tr -d '[:space:]')
GITHUB_REPOSITORY=$(grep -E '^GITHUB_REPOSITORY=' "$VERSION_FILE" | cut -d'=' -f2- | tr -d '\r' || true)

if ! [[ "$VERSION_MAJOR" =~ ^[0-9]+$ && "$VERSION_MINOR" =~ ^[0-9]+$ && "$VERSION_PATCH" =~ ^[0-9]+$ && "$VERSION_CODE" =~ ^[1-9][0-9]*$ ]]; then
    echo "ERROR: Invalid version.properties values." >&2
    exit 1
fi

OLD_VERSION="${VERSION_MAJOR}.${VERSION_MINOR}.${VERSION_PATCH}"
NEW_MAJOR=$VERSION_MAJOR
NEW_MINOR=$VERSION_MINOR
NEW_PATCH=$VERSION_PATCH
NEW_CODE=$((VERSION_CODE + 1))

case "$BUMP_TYPE" in
    major)
        NEW_MAJOR=$((VERSION_MAJOR + 1))
        NEW_MINOR=0
        NEW_PATCH=0
        ;;
    minor)
        NEW_MINOR=$((VERSION_MINOR + 1))
        NEW_PATCH=0
        ;;
    patch)
        NEW_PATCH=$((VERSION_PATCH + 1))
        ;;
    *)
        echo "ERROR: Invalid bump type '$BUMP_TYPE'. Expected major, minor, or patch." >&2
        exit 1
        ;;
esac

NEW_VERSION="${NEW_MAJOR}.${NEW_MINOR}.${NEW_PATCH}"
TODAY=$(date -u +%Y-%m-%d)

echo "Current version : v${OLD_VERSION} (VERSION_CODE=${VERSION_CODE})"
echo "Next version    : v${NEW_VERSION} (VERSION_CODE=${NEW_CODE})"

if [ "$DRY_RUN" = true ]; then
    echo "[dry-run] No files were modified."
    exit 0
fi

cat > "$VERSION_FILE" <<EOF
# Single source of truth for Semantic Versioning (SemVer 2.0.0)
# versionName is composed as \${VERSION_MAJOR}.\${VERSION_MINOR}.\${VERSION_PATCH}
# VERSION_CODE must strictly increase for every Android release build.
VERSION_MAJOR=${NEW_MAJOR}
VERSION_MINOR=${NEW_MINOR}
VERSION_PATCH=${NEW_PATCH}
VERSION_CODE=${NEW_CODE}

# Optional GitHub repository slug (owner/repo) used by the in-app HTTPS update checker.
# Leave blank if no public GitHub Releases repository is configured yet.
GITHUB_REPOSITORY=${GITHUB_REPOSITORY}
EOF

echo "Updated ${VERSION_FILE} -> v${NEW_VERSION} (VERSION_CODE=${NEW_CODE})."

if [ "$UPDATE_CHANGELOG" = true ] && [ -f "CHANGELOG.md" ]; then
    python3 - "$NEW_VERSION" "$TODAY" <<'PY'
import sys

new_version = sys.argv[1]
today = sys.argv[2]

with open("CHANGELOG.md", "r", encoding="utf-8") as fh:
    content = fh.read()

marker = "## [Unreleased]\n"
if marker in content and f"## [{new_version}]" not in content:
    replacement = (
        "## [Unreleased]\n\n"
        "### Added\n"
        "- _Add upcoming changes here before cutting the next release._\n\n"
        f"## [{new_version}] - {today}\n"
    )
    content = content.replace(marker, replacement, 1)
    with open("CHANGELOG.md", "w", encoding="utf-8") as fh:
        fh.write(content)
    print(f"Updated CHANGELOG.md with section [{new_version}] - {today}.")
else:
    print("Skipped CHANGELOG.md update (section already exists or marker not found).")
PY
fi

echo "Next steps: review git diff, run 'bash harness.sh', commit, and tag v${NEW_VERSION}."
