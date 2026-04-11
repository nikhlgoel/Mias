#!/usr/bin/env bash
# =============================================================================
# Dependency Audit — Scans for cloud/network SDKs that violate Zero-Cloud policy
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m'

BANNED_PATTERNS=(
    "firebase"
    "com.google.firebase"
    "com.google.android.gms"
    "com.google.cloud"
    "com.amazonaws"
    "software.amazon.awssdk"
    "com.azure"
    "com.microsoft.azure"
    "io.sentry"
    "com.crashlytics"
    "com.bugsnag"
    "com.amplitude"
    "com.mixpanel"
    "com.segment"
    "com.appsflyer"
    "com.facebook.android"
    "latest.release"
    "latest.integration"
)

violations=0

echo ""
echo "  Dependency Audit — Zero-Cloud Policy Check"
echo "  ==========================================="
echo ""

for pattern in "${BANNED_PATTERNS[@]}"; do
    matches=$(grep -rn --include="*.gradle.kts" --include="*.gradle" --include="*.toml" \
        "$pattern" "$PROJECT_ROOT" 2>/dev/null || true)
    if [[ -n "$matches" ]]; then
        echo -e "${RED}[VIOLATION]${NC} Banned pattern: $pattern"
        echo "$matches" | head -5
        echo ""
        ((violations++))
    fi
done

# Check for version wildcards
wildcard_matches=$(grep -rn --include="*.gradle.kts" --include="*.gradle" --include="*.toml" \
    'version.*[+]' "$PROJECT_ROOT" 2>/dev/null || true)
if [[ -n "$wildcard_matches" ]]; then
    echo -e "${RED}[VIOLATION]${NC} Wildcard version detected (+):"
    echo "$wildcard_matches"
    ((violations++))
fi

echo ""
if [[ "$violations" -gt 0 ]]; then
    echo -e "${RED}AUDIT FAILED: $violations violation(s) found.${NC}"
    exit 1
else
    echo -e "${GREEN}AUDIT PASSED: No cloud dependencies or version wildcards detected.${NC}"
fi
