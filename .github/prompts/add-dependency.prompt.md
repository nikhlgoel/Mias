---
mode: agent
description: "Add or update a dependency in Project #001 {Kid} with zero-cloud audit."
tools:
  - run_in_terminal
  - read_file
  - replace_string_in_file
  - grep_search
---

# Add Dependency Workflow — Project #001 {Kid}

## Before Adding Any Dependency

1. **Verify it has no cloud/network runtime requirement.** Check its README, source code, and transitive dependencies.
2. **Pin the exact version.** No `+`, no `latest.release`, no dynamic versions.
3. **Add to `gradle/libs.versions.toml`** — never inline versions in `build.gradle.kts`.

## Steps

### 1. Add version to `gradle/libs.versions.toml`
```toml
[versions]
new-lib = "1.2.3"

[libraries]
new-lib = { group = "com.example", name = "new-lib", version.ref = "new-lib" }
```

### 2. Add to the target module's `build.gradle.kts`
```kotlin
implementation(libs.new.lib)
```

### 3. Run the audit
```bash
bash scripts/audit_deps.sh
```

### 4. Verify build
```bash
./gradlew assembleDebug
```

## Banned Categories
- Any Google Cloud / Firebase / GMS SDK
- Any AWS / Azure SDK
- Any analytics / crash-reporting / telemetry SDK
- Any library that requires internet at runtime
