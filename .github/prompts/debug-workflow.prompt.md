---
mode: agent
description: "Debug and fix failing tests or build errors in Project #001 {Kid}."
tools:
  - run_in_terminal
  - get_errors
  - read_file
  - grep_search
  - replace_string_in_file
  - multi_replace_string_in_file
---

# Debug Workflow — Project #001 {Kid}

Use this workflow when a build fails, a test fails, or an error surfaces.

## Steps

### 1. Reproduce
- Run the exact failing command: `./gradlew testDebugUnitTest`, `./gradlew assembleDebug`, etc.
- Capture the full error output. Read the stack trace bottom-up.

### 2. Isolate
- Identify the failing module (`app`, `core/inference`, `core/network`, `core/data`, `core/common`).
- Identify the failing file and line number from the stack trace.
- Read the file at that location for context.

### 3. Diagnose
- Is it a compile error? Check imports, missing dependencies in `build.gradle.kts`, version mismatches in `libs.versions.toml`.
- Is it a test failure? Read the test assertion vs actual value. Check if the contract changed.
- Is it a lint error? Run `./gradlew ktlintFormat` for auto-fixable issues.

### 4. Fix
- Make the minimal change needed. Don't refactor adjacent code.
- If a dependency was added, run `bash scripts/audit_deps.sh` to verify zero-cloud compliance.

### 5. Verify
- Re-run the originally failing command.
- Then run `./scripts/init.sh --check` to confirm nothing else broke.

## Common Failure Patterns

| Symptom | Likely Cause | Fix |
|---|---|---|
| `Unresolved reference` | Missing module dependency | Add `implementation(project(":core:xxx"))` in `build.gradle.kts` |
| `Cannot access class` | Wrong visibility modifier | Make the class/function `public` or `internal` |
| `No matching constructor` | Missing `@Inject constructor` | Add Hilt annotations |
| `Test not found` | JUnit 4 vs 5 mismatch | Use `org.junit.jupiter.api.Test`, not `org.junit.Test` |
| `ktlint: Wildcard import` | Star import | Replace with explicit imports |
| `Dependency audit FAILED` | Banned cloud SDK added | Remove the dependency immediately |
