# Mias — Multi-Language Architecture

This document defines which programming language is used for which part of Mias, and why.

Mias is intentionally polyglot. The goal is to use the right tool for each layer — not to be impressive, but because each language genuinely fits its role better than the alternatives.

---

## Current Stack

### Kotlin — Android Application Layer
**Files:** `app/`, `core/ui/`, `core/data/`, `core/soul/`, `core/evolution/`, `core/language/`, `core/security/`, `core/resilience/`, `core/speech/`, `core/thermal/`, `core/network/`, `core/model-hub/`, `core/agent/`

Kotlin is the only practical choice for Compose UI on Android. It stays here.

### C++ — Native Inference Kernel
**Files:** `core/inference/src/main/cpp/`

llama.cpp is a C++ library. The JNI bridge (`mias_jni_bridge.cpp`) connects the Android layer to it. C++ gives direct memory control for the quantized weight math. Assembly SIMD extensions (ARM64 NEON) will be added here for matrix multiply hot paths.

### Python — Desktop Prototype Server + ML Tooling
**Files:** `desktop/server.py`, `scripts/`, `tests/`

Python stays for the initial desktop server (Flask + llama-cpp-python) and for model conversion, benchmarking, and data prep scripts. It will be replaced by Rust for production desktop once the interface is stable.

---

## Planned Additions

### Rust — Production Desktop Server
**Target files:** `desktop/mias-server/` (new)

The Python desktop server works but has real limitations: GIL threading, memory overhead, startup time. Rust with `candle` (HuggingFace's Rust ML framework) or a direct llama.cpp FFI binding gives:
- No garbage collector pauses mid-inference
- Single binary deployment — no virtualenv, no Python runtime
- Memory safety without runtime cost
- ~10x lower idle memory footprint than Flask

**Start point:** Keep Python server running. Build `desktop/mias-server/` in Rust as an alternative. Once feature-parity is reached, switch the default.

**Crate plan:**
```toml
axum         # HTTP server (async, fast)
tokio        # async runtime
serde_json   # JSON
llama-cpp-rs # llama.cpp Rust bindings (or candle)
```

### Go — MCP Bridge Server
**Target files:** `scripts/mcp-bridge/` (replace `scripts/mcp_bridge.py`)

The MCP bridge needs to be a long-running daemon that handles concurrent connections cleanly. Python's async story here is messy. Go gives:
- Single binary, zero dependencies
- goroutines for concurrent client handling
- Clean struct-based JSON marshaling for MCP protocol types
- Compiles to Linux/Mac/Windows/ARM with one command

The Python `mcp_bridge.py` is the prototype. The Go replacement will be spec-compliant with MCP 2024-11 (initialization handshake, capability negotiation, typed schemas).

### ARM64 Assembly — SIMD Matrix Operations
**Target files:** `core/inference/src/main/cpp/kernels/` (new)

The llama.cpp quantized inference hot path is matrix multiplication. ARM64 NEON SIMD intrinsics can be called directly from C++ via inline assembly or `.S` files. This is only worth doing for:
- Q4_0 and Q4_K_M dequantization inner loops
- dot product accumulation for attention scores

This is not a beginner contribution area — Assembly in this context means ARM NEON intrinsics, not general-purpose assembly. Add only with benchmarks proving improvement.

### Swift — iOS Port
**Target:** Future. Package name `io.mias.app` is already chosen with cross-platform in mind.

The core inference (llama.cpp) already has iOS support. The challenge is porting the Compose UI to SwiftUI. Plan: shared C++ inference layer, platform-native UI.

---

## Language Selection Logic

When adding a new component, choose the language this way:

```
Is it Android UI or Android business logic?
  → Kotlin

Is it performance-critical numeric code (inference, SIMD)?
  → C++ (with optional ARM64 Assembly for inner loops)

Is it a long-running network daemon or CLI tool?
  → Go (if networking-heavy) or Rust (if memory/performance-critical)

Is it ML model work, scripts, or data processing?
  → Python

Is it a web interface?
  → TypeScript
```

Do not add a new language without a clear answer to "what does this language do here that another existing language in the project cannot?"

---

## Build System Integration

Each language has its own build toolchain, but they connect:

| Language | Toolchain | Integration point |
|---|---|---|
| Kotlin | Gradle | `app/build.gradle.kts`, module `build.gradle.kts` |
| C++ | CMake (via Gradle) | `core/inference/src/main/cpp/CMakeLists.txt` |
| Rust | Cargo | `desktop/mias-server/Cargo.toml` — built separately |
| Go | `go build` | `scripts/mcp-bridge/` — built separately, Docker image |
| Python | pip | `requirements.txt`, virtual env |
| Assembly | Assembled by CMake/Clang | `.S` files in `cpp/kernels/` |

