# tests/ — Cross-Cutting Integration & Contract Tests
#
# This directory contains tests that span multiple modules or validate
# system-level contracts that don't belong to a single core module.
#
# Structure:
#   tests/
#   ├── README.md                       ← This file
#   ├── integration/                    ← Cross-module integration tests
#   │   └── ZeroCloudPolicyTest.kt      ← Validates no cloud deps exist
#   ├── contract/                       ← API contract tests
#   │   └── McpBridgeContractTest.py    ← MCP bridge protocol tests
#   └── scripts/                        ← Test runner scripts
#       └── run_all_tests.sh            ← Unified test runner
#
# Running:
#   ./scripts/init.sh --test            ← Runs Android unit tests via Gradle
#   ./scripts/init.sh --check           ← Full pre-commit suite (lint + tests + build)
#   python3 -m pytest tests/contract/   ← MCP bridge contract tests
#   bash tests/scripts/run_all_tests.sh ← Everything
