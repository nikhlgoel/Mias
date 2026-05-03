#!/usr/bin/env bash
# =============================================================================
# Project #001 {Kid} — Init / Validation Script
# =============================================================================
# Usage:
#   ./scripts/init.sh            → Full setup (install deps, validate env)
#   ./scripts/init.sh --check    → Pre-commit validation suite
#   ./scripts/init.sh --test     → Run all tests
#   ./scripts/init.sh --lint     → Run linters only
#   ./scripts/init.sh --mcp      → Start local MCP bridge server
#   ./scripts/init.sh --desktop  → Build & start desktop model server
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

info()    { echo -e "${CYAN}[INFO]${NC}  $*"; }
success() { echo -e "${GREEN}[OK]${NC}    $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error()   { echo -e "${RED}[FAIL]${NC}  $*"; }

# ---------------------------------------------------------------------------
# Environment checks
# ---------------------------------------------------------------------------
check_java() {
    if ! command -v java &>/dev/null; then
        error "Java not found. Install JDK 21."
        exit 1
    fi
    local java_version
    java_version=$(java -version 2>&1 | head -1 | awk -F '"' '{print $2}' | cut -d. -f1)
    if [[ "$java_version" -lt 21 ]]; then
        error "JDK 21+ required. Found: $java_version"
        exit 1
    fi
    success "JDK $java_version"
}

check_gradle_wrapper() {
    if [[ ! -f "$PROJECT_ROOT/gradlew" ]]; then
        warn "Gradle wrapper not found. Generating..."
        cd "$PROJECT_ROOT"
        gradle wrapper --gradle-version 8.12
    fi
    success "Gradle wrapper present"
}

check_docker() {
    if command -v docker &>/dev/null; then
        success "Docker available: $(docker --version | head -1)"
    else
        warn "Docker not found — desktop model server features unavailable"
    fi
}

check_env() {
    info "Checking development environment..."
    check_java
    check_gradle_wrapper
    check_docker
    echo ""
    success "Environment checks passed"
}

# ---------------------------------------------------------------------------
# Android build & test targets
# ---------------------------------------------------------------------------
run_lint() {
    info "Running ktlint..."
    cd "$PROJECT_ROOT"
    ./gradlew ktlintCheck --no-daemon --console=plain
    success "ktlint passed"

    info "Running Android lint..."
    ./gradlew lintDebug --no-daemon --console=plain
    success "Android lint passed"
}

run_unit_tests() {
    info "Running unit tests..."
    cd "$PROJECT_ROOT"
    ./gradlew testDebugUnitTest --no-daemon --console=plain
    success "Unit tests passed"
}

run_instrumented_tests() {
    info "Running instrumented tests..."
    cd "$PROJECT_ROOT"
    ./gradlew connectedDebugAndroidTest --no-daemon --console=plain
    success "Instrumented tests passed"
}

run_build() {
    info "Building debug APK..."
    cd "$PROJECT_ROOT"
    ./gradlew assembleDebug --no-daemon --console=plain
    success "Debug build succeeded"
}

# ---------------------------------------------------------------------------
# Pre-commit validation (--check)
# ---------------------------------------------------------------------------
run_precommit() {
    info "=== Pre-Commit Validation Suite ==="
    echo ""
    run_lint
    echo ""
    run_unit_tests
    echo ""
    run_build
    echo ""
    success "=== All pre-commit checks passed ==="
}

# ---------------------------------------------------------------------------
# MCP Bridge Server (--mcp)
# ---------------------------------------------------------------------------
run_mcp() {
    info "Starting local MCP bridge server..."
    local mcp_script="$SCRIPT_DIR/mcp_bridge.py"
    if [[ ! -f "$mcp_script" ]]; then
        error "MCP bridge script not found at $mcp_script"
        exit 1
    fi

    if ! command -v python3 &>/dev/null; then
        error "Python 3 not found. Required for MCP bridge."
        exit 1
    fi

    info "MCP bridge listening on 127.0.0.1:8401 (Tailscale mesh only)"
    python3 "$mcp_script"
}

# ---------------------------------------------------------------------------
# Desktop Model Server (--desktop)
# ---------------------------------------------------------------------------
run_desktop() {
    info "Building desktop model server container..."
    if ! command -v docker &>/dev/null; then
        error "Docker required for desktop model server"
        exit 1
    fi

    cd "$PROJECT_ROOT"
    docker build -t kid-desktop-server -f desktop/Dockerfile .
    info "Starting desktop model server on port 8400..."
    docker run --rm --gpus all -p 8400:8400 kid-desktop-server
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
main() {
    echo ""
    echo "  ╔═══════════════════════════════════════╗"
    echo "  ║   Project #001 {Kid} — Init Script    ║"
    echo "  ║   Zero-Cloud · Local-First · Private  ║"
    echo "  ╚═══════════════════════════════════════╝"
    echo ""

    case "${1:-}" in
        --check)
            check_env
            echo ""
            run_precommit
            ;;
        --test)
            run_unit_tests
            ;;
        --lint)
            run_lint
            ;;
        --mcp)
            run_mcp
            ;;
        --desktop)
            run_desktop
            ;;
        --build)
            run_build
            ;;
        "")
            check_env
            echo ""
            run_build
            ;;
        *)
            echo "Usage: $0 [--check|--test|--lint|--mcp|--desktop|--build]"
            exit 1
            ;;
    esac
}

main "$@"
