# Mias Platform Master Plan — one TypeScript/React-Native codebase, every target

**Status:** Active · supersedes the earlier RN migration prompt (removed) and the
R0–R7 phase list. Stages run S0→S8; **S0–S5 complete** (S5 = the PC-extension
Bridge P1: relay + session server + SecureChannel graduated from the PoC to
production TS, with the VS Code extension scaffold). Next: S6.

## 1. Goal

One controllable codebase that builds and tests **all** Mias surfaces:

| Target | Delivery | Stage |
|---|---|---|
| Android app (APK/AAB) | React Native app (`/mobile`), New Architecture | S0–S4 (parity), S7 (cutover) |
| PC extension (offload host) | VS Code extension (TypeScript) + session server, per `bridge/docs/` | S5 |
| iOS app | Same RN app + Swift implementations of the native interfaces | S8 |
| Windows / macOS desktop app | Evaluate `react-native-windows` / `react-native-macos` shells reusing the shared TS packages (the VS Code extension is the *first* PC surface) | S8 |

The **Bridge** (remote PC offload, `bridge/docs/00–11`) is the driving feature:
its protocol and client live in shared TS packages so the phone app and the PC
extension are two consumers of the same code.

## 2. Architecture rule — what is TS, what is native

**TypeScript-first: all logic, protocol, orchestration, and UI live in shared TS
packages.** Native code exists only where physics/OS demand it, behind small,
typed interfaces:

- **Compute:** llama.cpp (C++/JNI/Metal), NPU (MediaPipe), ONNX Runtime.
- **OS security:** hardware Keystore / Secure Enclave, biometric prompt.
- **Platform services:** speech (ML Kit), thermal, notifications, WorkManager.

Per-platform implementations of those interfaces: Kotlin (Android, exists),
Swift (iOS, S8), and on the PC the session server + inference worker
(`desktop/server.py`) per the Bridge architecture. Everything else that is
currently Kotlin gets **rewritten into shared TS** over the stages below.

## 3. Repo layout (end state)

```
/packages/bridge-protocol   frames, seq/ack cursors, BridgeTransport, MCP client (S1)
/packages/domain            chat session, ReAct parsing, personas, RAG orchestration (S2–S4)
/packages/ui-tokens         theme tokens (eye-comfort system from bridge/docs/10) (S3+)
/mobile                     RN app (Android now, iOS S8) — thin screens over the packages
/extension                  VS Code extension + session server (S5)
/core/*                     shrinks to native adapters only (inference, security, platform)
/app                        legacy Compose app — deleted at S7 cutover
/desktop                    inference worker (llama.cpp server) — PC side of the Bridge
/bridge                     architecture docs + relay PoC (spec source of truth)
```

Packages are consumed by `file:` links (no hoisting — the RN Gradle integration
depends on `mobile/node_modules` paths). Zero runtime dependencies in packages;
tests run on Node's built-in runner.

## 4. Core-module disposition (nothing left behind)

| Module | Disposition | Where it lands |
|---|---|---|
| `core/inference` engines (llama.cpp JNI, AI Edge/NPU, ONNX, vision) | **Native adapter** (compute) | typed `InferenceModule`; Swift/Metal impls in S8 |
| `core/inference` ReAct (parser, sanitizer, templates, registry) | **TS rewrite** (pure logic) | `packages/domain` (S2–S3) |
| `core/inference` orchestrator (role routing, policy) | **TS rewrite**, thin native hooks for engine load/health | `packages/domain` (S3) |
| `core/network` MCP client | **TS rewrite** | `packages/bridge-protocol` (S1) |
| `core/network` mesh/Tailscale | **TS** transport variant (T3, optional) | `packages/bridge-protocol` (S5+) |
| `core/data` Room DB + repositories | **Native adapter now**, cross-platform SQLite (`op-sqlite`-class) decision at S6 with real migrations | `DataModule` → S6 |
| `core/data` RAG/Hindsight logic (chunking, scoring, dedup) | **TS rewrite**; embeddings stay native (compute) | `packages/domain` (S4) |
| `core/security` (ZkVault, BiometricGate, guardrails) | **Native adapter** (Keystore/biometric) + **TS** guardrail heuristics | `SecurityModule` (S3); guardrails → `packages/domain` |
| `core/model-hub` (search, download, SHA-256, registry) | **TS rewrite** (fetch/fs/progress), native only for file streams if needed | `packages/domain` (S4) |
| `core/speech` (ML Kit STT/TTS) | **Native adapter** (platform service) | `SpeechModule` (S4) |
| `core/thermal`, `core/resilience` | **Native adapter** (health signals) + **TS** queue/retry logic | S3–S4 |
| `core/agent` (tools, storage guard) | **TS rewrite** for tool logic; native adapter for storage access | `packages/domain` (S4) |
| `core/evolution` (WorkManager self-learning) | **Native adapter** for scheduling; job logic → TS | S6 |
| `core/soul` (personas), `core/language` (intents) | **TS rewrite** (pure logic) | `packages/domain` (S3–S4) |
| `core/common`, `core/ui` (Compose components) | Types → TS; Compose UI replaced by RN screens | S2–S4 |

## 5. Stages (each ends buildable + verified; one commit per stage)

- **S0 ✅ RN shell.** `/mobile` (RN 0.81.6, New Arch, arm64, 16 KB) in the single
  root Gradle build; biometric-gate launcher; legacy `:app` untouched and green.
- **S1 Protocol foundation.** `packages/bridge-protocol` in TS: frame model +
  per-stream seq/ack cursors + `frame_id` dedup (bridge/docs/04), `BridgeTransport`
  interface, LAN MCP transport + MCP client (2025-03-26 handshake, `X-Mias-Token`)
  targeting `desktop/server.py`. Node-runner unit tests; consumed by `/mobile`.
- **S2 Chat vertical slice.** TS `ChatSession` in `packages/domain` (ReAct stream
  parsing, delta semantics) + native `InferenceModule` wrapping the on-device
  engines with streamed token events; RN Chat screen renders both paths: local
  inference and LAN offload via S1. Exit: real streamed turn on device from both.
- **S3 Native adapter belt.** Typed modules: `DataModule` (conversations/repos),
  `SecurityModule` (CryptoObject-bound biometric + vault), settings/secure
  storage; orchestrator/personas/guardrails logic moves to TS. Chat reaches
  feature parity (RAG, personas, memory chips, stop/regenerate).
- **S4 Full surface.** Remaining screens in RN (Voice orb + live transcript,
  Vision, Model hub with download progress, Home, Chats, Knowledge, Settings);
  model-hub + RAG + agent-tool logic in TS; `SpeechModule`/`ThermalModule`.
- **S5 PC extension (Bridge P1).** `/extension`: VS Code extension hosting the
  session server, consuming `packages/bridge-protocol`; relay + balanced-PAKE
  pairing + E2EE per `bridge/docs/03–04`; phone connects over the internet
  (rendezvous relay), streams a real offload turn. The PoC (`bridge/poc`)
  graduates to production code here.
- **S6 Data + background.** **Storage decision: keep native Room behind
  `DataModule`** — the conversation/RAG/Hindsight schema is deeply tied to Room +
  the on-device embedding engine, so a TS-owned SQLite rewrite would re-implement
  the schema and risk data loss for zero user-visible gain (the data is on-device
  native either way). iOS gets its own persistence impl behind the same
  `DataModule` interface (S8). **No destructive fallback:** the conversations DB's
  unconditional `fallbackToDestructiveMigration(dropAllTables=true)` — the S0
  data-loss landmine — is removed; migrations 1→5 cover every shipped version and
  a missing future migration now fails loudly instead of wiping data. Evolution /
  self-learning is exposed via a native module (run-now + status + background
  scheduling). Also lands the deferred **Vision** screen.
- **S7 Cutover.** Legacy `app/` Compose UI deleted; RN app takes `io.mias.app`;
  `core/*` reduced to the native-adapter set; single product.
- **S8 Apple + desktop.** Swift implementations (llama.cpp Metal, Keychain,
  FaceID, AVSpeech); iOS app ships from the same codebase. Evaluate
  `react-native-windows`/`macos` shells reusing the packages for desktop apps
  beyond the VS Code extension.

## 6. Invariants (hold at every stage)

1. **Privacy:** no analytics/crash/cloud-AI SDKs; outbound = host allowlist
   (HuggingFace + GitHub CDNs; + the self-hostable relay from S5, per
   `bridge/docs/03` section 5). E2EE fail-closed on the Bridge.
2. **Security:** secrets never in JS-accessible storage; Keystore/Enclave only;
   biometric gate on cold start; `allowBackup=false`.
3. **Green builds:** `./gradlew :app:assembleDebug :mobile:assembleDebug` (until
   S7 removes `:app`), package tests, `tsc --noEmit` — all pass before a stage
   commit.
4. **Identifiers:** `io.mias.app`, release keystore, `version.properties`.
5. **No regression in inference quality/perf:** on-device paths stay native.

## 7. Verification per stage

Unit tests for every TS package (Node runner); RN `tsc` + jest; both Gradle
builds; on-device streamed-chat smoke test from S2 on; Bridge exit tests from
S5 follow `bridge/docs/06` (P1/P2 gates: ciphertext-only relay, one-guess codes,
zero-loss resume).
