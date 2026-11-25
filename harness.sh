#!/usr/bin/env bash
# ==============================================================================
# harness.sh: Automated Verification Harness for Google Calendar Agenda Widget
# ==============================================================================

set -e

GREEN='\033[0;32m'
RED='\033[0;31m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${BLUE}======================================================${NC}"
echo -e "${BLUE}  Starting Agenda Widget Verification Harness         ${NC}"
echo -e "${BLUE}======================================================${NC}"

# 1. Check workspace prerequisites
echo -e "\n${YELLOW}[1/4] Checking project structure...${NC}"
REQUIRED_FILES=(
    "app/build.gradle.kts"
    "app/src/main/AndroidManifest.xml"
    "app/src/main/java/com/example/MainActivity.kt"
    "app/src/main/java/com/example/util/AppLogger.kt"
    "app/src/main/java/com/example/data/model/OperationResult.kt"
    "app/src/main/java/com/example/widget/CalendarAgendaWidgetProvider.kt"
    "app/src/main/java/com/example/widget/CalendarGlanceWidget.kt"
    "app/src/main/java/com/example/widget/CalendarGlanceWidgetReceiver.kt"
    "app/src/main/java/com/example/worker/CalendarSyncWorker.kt"
    "app/src/main/java/com/example/worker/CalendarSyncScheduler.kt"
    "product.md"
    "tasks.md"
    "AGENTS.md"
)

for file in "${REQUIRED_FILES[@]}"; do
    if [ ! -f "$file" ]; then
        echo -e "${RED}ERROR: Missing required file: $file${NC}"
        exit 1
    fi
done
echo -e "${GREEN}✓ All core files present.${NC}"

# 2. Static Safety & Quality Gates
echo -e "\n${YELLOW}[2/4] Running static safety & logging checks...${NC}"
MOCK_MATCHES=$(grep -rn --exclude-dir=.gradle --exclude-dir=build --exclude-dir=.git --exclude="tasks.md" --exclude="product.md" --exclude="AGENTS.md" --exclude="implementation_plan.md" --exclude="harness.sh" "generateSeedEvents" . || true)
if [ -n "$MOCK_MATCHES" ]; then
    echo -e "${RED}ERROR: Found forbidden seed mockup data references:${NC}"
    echo "$MOCK_MATCHES"
    exit 1
fi

STACKTRACE_MATCHES=$(grep -rn "printStackTrace(" app/src/main || true)
if [ -n "$STACKTRACE_MATCHES" ]; then
    echo -e "${RED}ERROR: Found raw printStackTrace() calls (use AppLogger instead):${NC}"
    echo "$STACKTRACE_MATCHES"
    exit 1
fi

SILENT_THROWABLE_MATCHES=$(grep -rn "catch (_: Throwable)" app/src/main || true)
if [ -n "$SILENT_THROWABLE_MATCHES" ]; then
    echo -e "${RED}ERROR: Found silent catch (_: Throwable) blocks (use specific exceptions + AppLogger):${NC}"
    echo "$SILENT_THROWABLE_MATCHES"
    exit 1
fi

API_KEY_MATCHES=$(grep -rnE "AIza[0-9A-Za-z_-]{35}" app/src || true)
if [ -n "$API_KEY_MATCHES" ]; then
    echo -e "${RED}ERROR: Found potential hardcoded Google API key:${NC}"
    echo "$API_KEY_MATCHES"
    exit 1
fi
echo -e "${GREEN}✓ Static safety, zero-mockup, and logging checks passed.${NC}"

# 3. Compile and Run Unit Tests
echo -e "\n${YELLOW}[3/4] Running Kotlin Compilation & Robolectric Test Suite...${NC}"
gradle :app:compileDebugKotlin :app:testDebugUnitTest

# 4. Run Android Lint
echo -e "\n${YELLOW}[4/4] Running Android Lint checks...${NC}"
gradle :app:lintDebug

echo -e "\n${GREEN}======================================================${NC}"
echo -e "${GREEN}  ✓ All Harness Verification Gates Passed Successfully!${NC}"
echo -e "${GREEN}======================================================${NC}"
