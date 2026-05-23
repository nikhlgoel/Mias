# Mias — Tech Stack

This is the authoritative list of what's actually in the build, with versions
pulled from `gradle/libs.versions.toml` and module `build.gradle.kts` files.
Update this file when you change a version, not when you plan to.

---

## Platform targets

| Surface | Value |
|---|---|
| Min SDK | **30** (Android 11) |
| Target SDK | **36** (Android 16) |
| Compile SDK | **36** |
| JVM target | 21 |
| NDK ABI | `arm64-v8a` only |
| Build system | Gradle 8.13 + AGP 8.12.0 |
| Kotlin | 2.1.21 (incl. Compose Compiler plugin) |
| KSP | 2.1.21-2.0.1 |

---

## Android app layer

| Concern | Library | Version |
|---|---|---|
| UI toolkit | Jetpack Compose (BOM) | 2025.05.00 |
| Material design | Material 3 | from BOM |
| Navigation | Navigation Compose | 2.9.0 |
| Activity | androidx.activity.compose | 1.10.1 |
| Lifecycle | androidx.lifecycle | 2.9.0 |
| DI | Hilt | 2.56.2 |
| Hilt navigation | androidx.hilt.navigation.compose | 1.2.0 |
| Background jobs | WorkManager | 2.10.1 + hilt-work 1.2.0 |
| Local key-value | DataStore Preferences | 1.1.7 |
| Local relational DB | Room | 2.7.1 (with SQLCipher gating) |
| Biometric prompt | androidx.biometric | 1.1.0 |
| At-rest crypto | androidx.security.crypto | 1.1.0-alpha07 |

---

## Inference

| Concern | What ships | Notes |
|---|---|---|
| NPU / accelerated Gemma | Google AI Edge SDK — MediaPipe Tasks GenAI 0.10.27 | Activated by `GoogleAiEdgeEngine` when the model file is present and the chipset supports it. |
| CPU GGUF inference | Vendored `llama.cpp` (`core/inference/src/main/cpp/`) compiled via CMake to `libmias_inference.so`, called from `LlamaCppEngine` via JNI. | ARM NEON enabled. ABI restricted to `arm64-v8a`. |
| ONNX path | onnxruntime-android 1.22.0 | Available but not the primary path. |
| Tool-use loop | `ReActEngine` (Kotlin) | JSON parsed via `kotlinx.serialization` 1.7.3. Max 7 iterations. |

---

## Networking (local-only)

| Concern | What ships | Notes |
|---|---|---|
| HTTP client | Ktor 3.1.3 (CIO engine) | Two configurations: `NetworkModule` with a 30 s request timeout, and `@ModelHubHttpClient` with no timeout for multi-GB downloads. |
| JSON | kotlinx-serialization-json 1.7.3 | Via Ktor `ContentNegotiation`. |
| Mesh | `TailscaleMeshClient` over the Tailscale local API (desktop) | Note: the Android local API path is currently non-functional; users enter the desktop's Tailscale IP manually in Settings. |
| Desktop offload protocol | MCP 2025-03-26 over JSON-RPC HTTP | `McpClient` (client), `desktop/server.py` (server). |
| Auth between phone and desktop | `X-Mias-Token` shared secret | Optional; enabled when `MIAS_TOKEN` is set on the server and configured in the app. |

---

## Speech and language

| Concern | What ships |
|---|---|
| Speech-to-text | Google ML Kit speech recognition (on-device, 13 languages) via `core/speech/`. |
| Language detection | `core/language/` (intent classification, currently keyword-based). |

---

## Storage and data

| Concern | What ships |
|---|---|
| Conversation DB | Room (`core/data/`), AES via SQLCipher gating at boot. |
| Hindsight memory | Room entities in `core/data/db/entity/Hindsight*Entity.kt`, cosine-similarity dedup on insert. |
| Model registry | Room (`core/model-hub/db/`). Schema currently uses `fallbackToDestructiveMigration` — temporary. |
| Model files | `${context.filesDir}/models/{id}.gguf`. Partials in `.partial/`. |
| Secrets | EncryptedSharedPreferences (planned for the HF token UI hookup). |

---

## Desktop server (`desktop/`)

| Concern | What ships |
|---|---|
| Language | Python 3.11+ |
| HTTP framework | FastAPI |
| Server | Uvicorn |
| Inference | `llama-cpp-python` with CUDA 12.x runtime |
| Container | `nvidia/cuda:12.8.1-runtime-ubuntu24.04`, exposes `8401` |
| Auth | `X-Mias-Token` header, validated when `MIAS_TOKEN` env var is set |

---

## Code quality

| Tool | Version |
|---|---|
| ktlint | 12.2.0 |
| detekt | 1.23.8 |

---

## Testing

| Layer | Stack |
|---|---|
| Unit | JUnit 5 (5.12.2) + MockK 1.14.2 + Turbine 1.2.1 + Truth 1.4.4 |
| Android local | Robolectric 4.14.1 |
| Instrumented | androidx Compose UI test (from BOM) + MockK-android 1.14.2 |
| Coroutines | kotlinx-coroutines-test 1.10.2 |

---

## What we deliberately do **not** depend on

- Firebase, Crashlytics, Sentry, Mixpanel, or any analytics/crash SDK.
- OpenAI, Anthropic, Google Cloud AI, or any cloud LLM API.
- Google Play Services beyond what ML Kit speech requires.
- A retrofit/okhttp pair (Ktor handles everything).
- React Native, Flutter, Compose Multiplatform, Capacitor — the app is
  100% native Kotlin for performance and JNI / NPU SDK access reasons.

---

## Module map

```
app/                    Android entry point, navigation, screens, view-models
core/
  agent/                AgentCapability interface + concrete capabilities
  common/               MiasResult, dispatchers, vector utils, shared models
  data/                 Room DB, conversation + hindsight repositories
  evolution/            Self-improvement daemon and WorkManager scheduling
  inference/            InferenceEngine + LlamaCpp / GoogleAiEdge / Embedding
                        engines, ReActEngine, InferenceOrchestrator, JNI bridge
  language/             Intent classification utilities
  model-hub/            Model catalog, HF registry, downloads, role assignment
  network/              Ktor clients, MCP client, mesh
  neural/               (reserved)
  neurocore/            (reserved)
  resilience/           ConnectivityMonitor, DeviceHealthMonitor, RetryExecutor
  security/             BiometricGate, GuardrailProcessor, ZkVault
  soul/                 Persona / LoRA slider state (UI-only today)
  speech/               ML Kit speech wrapper + view-model
  thermal/              TawsGovernor, ThermalSnapshot
  ui/                   Shared composables, theme, glass components
desktop/                Python FastAPI MCP server
```
