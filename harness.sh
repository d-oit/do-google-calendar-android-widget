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
echo -e "\n${YELLOW}[1/3] Checking project structure...${NC}"
REQUIRED_FILES=(
    "app/build.gradle.kts"
    "app/src/main/AndroidManifest.xml"
    "app/src/main/java/com/example/MainActivity.kt"
    "app/src/main/java/com/example/widget/CalendarAgendaWidgetProvider.kt"
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

# 2. Verify zero mockup data rule
echo -e "\n${YELLOW}[2/3] Verifying Zero Mockup Data rule...${NC}"
MOCK_MATCHES=$(grep -rn --exclude-dir=.gradle --exclude-dir=build --exclude-dir=.git --exclude="tasks.md" --exclude="product.md" --exclude="AGENTS.md" --exclude="implementation_plan.md" --exclude="harness.sh" "generateSeedEvents" . || true)
if [ -n "$MOCK_MATCHES" ]; then
    echo -e "${RED}ERROR: Found forbidden seed mockup data references:${NC}"
    echo "$MOCK_MATCHES"
    exit 1
fi
echo -e "${GREEN}✓ Zero mockup data verified across codebase.${NC}"

# 3. Compile and Run Unit Tests
echo -e "\n${YELLOW}[3/3] Running Compilation & Robolectric Test Suite...${NC}"
gradle :app:compileDebugKotlin :app:testDebugUnitTest

echo -e "\n${GREEN}======================================================${NC}"
echo -e "${GREEN}  ✓ All Harness Verification Gates Passed Successfully!${NC}"
echo -e "${GREEN}======================================================${NC}"
