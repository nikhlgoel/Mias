# Project #001 — {Kid}

## High-Level Summary

**{Kid}** is a 100% private, local-first, cross-device AI ecosystem targeting **Android 17** (mobile) and **Windows/Linux** (desktop).

### Architecture Pillars

| Layer | Mobile (Android 17) | Desktop (Windows / Linux) |
|---|---|---|
| **Primary Model** | Gemma-4-e4b (NPU-accelerated) | Qwen3-Coder-Next (MoE, GPU/CPU) |
| **Runtime** | ONNX Runtime Mobile / MediaPipe | llama.cpp / vLLM local |
| **Networking** | Tailscale P2P mesh | Tailscale P2P mesh |
| **Cloud Usage** | **NONE — Zero-Cloud** | **NONE — Zero-Cloud** |

- **Mobile-First**: The Android app is the primary interaction surface. NPU delegation via Android NNAPI / Qualcomm QNN for Gemma-4-e4b inference.
- **Desktop Companion**: MoE Qwen3-Coder-Next runs on the PC for heavier code-generation and reasoning tasks, reachable over the Tailscale mesh.
- **Tailscale P2P Mesh**: All device communication is peer-to-peer over WireGuard (Tailscale). No relay servers, no cloud middlemen.
- **Zero-Cloud**: No external cloud APIs, no telemetry, no SaaS dependencies. Everything runs on hardware we own.

---

## Strict Rules

### 1. NEVER Use External Cloud APIs
- **No** OpenAI, Anthropic, Google Cloud, AWS, Azure, or any hosted inference API.
- **No** Firebase, Supabase, or any cloud database/auth service.
- **No** analytics, crash-reporting, or telemetry SDKs that phone home.
- All model inference is local (on-device or on-LAN via Tailscale).
- All data stays on devices we physically control.

### 2. No Implicit Network Calls
- Every dependency added must be audited for hidden network calls.
- Gradle dependencies must use `implementation` with explicit version pinning — no `+` or `latest.release`.
- If a library requires internet at runtime, it is **rejected**.

### 3. Reproducible Builds
- Pin all dependency versions in `gradle/libs.versions.toml`.
- Use Gradle wrapper (`gradlew`) committed to the repo.
- Docker or Podman for desktop model serving — Dockerfiles committed.

---

## Build, Test & Validation Instructions

### Android (Kotlin)

```bash
# Full build
./gradlew assembleDebug

# Unit tests
./gradlew testDebugUnitTest

# Instrumented tests (requires emulator / device)
./gradlew connectedDebugAndroidTest

# Lint
./gradlew lintDebug

# ktlint format check
./gradlew ktlintCheck

# ktlint auto-format
./gradlew ktlintFormat
```

### Before Committing

1. Run `./scripts/init.sh --check` to execute the full pre-commit validation suite (lint + tests + format check).
2. Ensure **zero** lint warnings and **zero** test failures.
3. Do **not** push code that adds any new cloud/network dependency without explicit approval.

### Desktop (Model Serving)

```bash
# Build desktop server container
docker build -t kid-desktop-server -f desktop/Dockerfile .

# Run local model server
docker run --gpus all -p 8400:8400 kid-desktop-server

# Validate with health check
curl http://localhost:8400/health
```

### Local MCP Bridge

```bash
# Start the MCP server bridge (connects Android ↔ Desktop)
./scripts/init.sh --mcp
```

---

## Project Structure

```
.github/              → CI config, Copilot instructions
app/                  → Android application (Kotlin, Jetpack Compose)
  src/main/           → Production source
  src/test/           → Unit tests
  src/androidTest/    → Instrumented tests
core/                 → Shared Kotlin modules (inference, networking, data)
desktop/              → Desktop model server (Dockerized)
scripts/              → Build, init, and validation scripts
tests/                → Cross-cutting integration & contract tests
docs/                 → Architecture docs, PRD
gradle/               → Version catalog, wrapper
```

---

## Conventions

- **Language**: Kotlin (Android), Python (desktop model server glue)
- **UI**: Jetpack Compose (Material 3, dynamic color)
- **Min SDK**: 35 (Android 17)
- **Target SDK**: 35
- **JDK**: 21
- **Kotlin**: 2.1.x
- **Gradle**: 8.12+
- **Architecture**: MVVM + Clean Architecture layers
- **DI**: Hilt (Dagger)
- **Testing**: JUnit 5 + Turbine (Flows) + MockK + Robolectric
- **Formatting**: ktlint (official Kotlin style)
