#!/usr/bin/env bash
# =============================================================================
# Unified Test Runner — runs all tests across Android, Desktop, and scripts
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

RED='\033[0;31m'
GREEN='\033[0;32m'
CYAN='\033[0;36m'
NC='\033[0m'

info()    { echo -e "${CYAN}[TEST]${NC}  $*"; }
success() { echo -e "${GREEN}[PASS]${NC}  $*"; }
error()   { echo -e "${RED}[FAIL]${NC}  $*"; }

failures=0

# ---------------------------------------------------------------------------
# 1. Dependency audit (Zero-Cloud policy)
# ---------------------------------------------------------------------------
info "Running dependency audit..."
if bash "$PROJECT_ROOT/scripts/audit_deps.sh"; then
    success "Dependency audit"
else
    error "Dependency audit"
    ((failures++))
fi

# ---------------------------------------------------------------------------
# 2. Android unit tests
# ---------------------------------------------------------------------------
info "Running Android unit tests..."
cd "$PROJECT_ROOT"
if ./gradlew testDebugUnitTest --no-daemon --console=plain 2>/dev/null; then
    success "Android unit tests"
else
    error "Android unit tests"
    ((failures++))
fi

# ---------------------------------------------------------------------------
# 3. ktlint
# ---------------------------------------------------------------------------
info "Running ktlint..."
cd "$PROJECT_ROOT"
if ./gradlew ktlintCheck --no-daemon --console=plain 2>/dev/null; then
    success "ktlint"
else
    error "ktlint"
    ((failures++))
fi

# ---------------------------------------------------------------------------
# 4. MCP bridge contract tests (if pytest available)
# ---------------------------------------------------------------------------
if command -v python3 &>/dev/null; then
    info "Running MCP bridge contract tests..."
    if python3 -m pytest "$SCRIPT_DIR/../contract/" -v 2>/dev/null; then
        success "MCP bridge contract tests"
    else
        error "MCP bridge contract tests"
        ((failures++))
    fi
else
    info "Skipping MCP bridge tests (python3 not available)"
fi

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
echo ""
if [[ "$failures" -gt 0 ]]; then
    error "=== $failures test suite(s) failed ==="
    exit 1
else
    success "=== All test suites passed ==="
fi
