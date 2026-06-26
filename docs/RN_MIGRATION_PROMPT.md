# Prompt — Migrate the Mias Android App from Kotlin to React Native

> Paste everything below the line into a fresh code session opened at the repo
> root. It is written for an agent with codebase access. It tells the agent to
> **analyze first, plan, get sign-off, then migrate in small verified steps** —
> Android first, iOS-ready, without breaking the app at any commit.

---

## Role & objective
You are migrating **Mias** — a privacy-first, fully on-device Android AI assistant
(Kotlin / Jetpack Compose) — to **React Native**, so the codebase can later also
target **iOS** with shared code. **Android is the only target for this migration;
iOS comes later, but no decision may block it.** Optimize for correctness and a
continuously buildable app, then speed. Do **not** rewrite working native logic in
JavaScript just to "make it RN" — wrap it (see Strategy).

## Absolute constraints (do not violate)
1. **Privacy / no-cloud is the product.** No analytics, crash reporters, or cloud
   AI SDKs may be introduced. Outbound network stays on the existing **host
   allowlist** (HuggingFace + GitHub release CDNs). RN tooling must not phone home
   in the shipped app.
2. **Security parity:** biometric gate on cold start, SQLCipher-encrypted DB,
   EncryptedSharedPreferences / Keystore for secrets, `allowBackup=false`. Keep or
   improve all of it. Never store secrets in JS-accessible plaintext or AsyncStorage.
3. **On-device inference must keep working:** llama.cpp JNI (`libmias_inference.so`,
   arm64-v8a), Google AI Edge / MediaPipe NPU path, ONNX Runtime path. These stay
   native; you bridge them, you do not port them to JS.
4. **Preserve identifiers & signing:** applicationId `io.mias.app`, package
   `dev.mias.app`, the release keystore (`mias-release.jks`, `keystore.properties`),
   versioning (`version.properties`).
5. **Keep the app installable at every step.** Use an incremental (strangler-fig)
   migration; never land a commit that leaves `assembleDebug` broken.
6. **Forward-compat with "Mias Bridge"** (the planned remote-PC offload feature):
   put all phone↔server communication behind a transport interface
   `BridgeTransport { connect, send, onMessage, close }` in TypeScript, with the
   first implementation talking to today's LAN MCP server (`desktop/server.py`,
   MCP 2025-03-26 JSON-RPC over HTTP, `X-Mias-Token`). See `bridge/docs/` for why.

## Phase 0 — Analyze before touching anything (REQUIRED, no code yet)
Produce a written **Migration Plan** and wait for my approval before coding.

1. **Map the codebase.** Inventory every Gradle module (`app/`, `core/*`) — note
   the modules in `settings.gradle.kts` (and that `core/neural`, `core/neurocore`
   are NOT in the build; leave them out of scope). For each in-scope module record:
   purpose, public surface, Android/JNI/SDK dependencies, and Compose screens/
   ViewModels it owns.
2. **List user-facing features & screens** to reach parity: Home, Chat, Voice chat
   (animated orb + live transcript), Vision chat, Brain Market / model hub
   (browse, HF search, download w/ pause-resume + SHA-256), Settings (HF token,
   desktop IP/port), biometric unlock, ReAct agent + tools, RAG over documents,
   personas, background evolution/WorkManager, thermal/offload orchestration.
3. **Decide wrap-vs-rewrite per module** and put it in a table. Default policy:
   - **Wrap as a native module** (keep Kotlin, expose via RN TurboModule/Native
     Module): `core/inference` (engines, orchestrator, ReAct, JNI), `core/data`
     (Room + SQLCipher), `core/security` (biometric, guardrails), `core/network`
     (MCP/Ktor/mesh), `core/model-hub`, `core/speech` (ML Kit), `core/thermal`,
     `core/evolution`, `core/agent`. These are high-risk to port and have no JS
     equivalent — reuse them.
   - **Rewrite in RN/TS:** the **UI layer** (`app/` Compose screens, navigation,
     view-models → React Navigation + React components + a TS state layer) and
     thin orchestration/glue.
   - Justify any deviation.
4. **Choose the RN setup** and justify: bare React Native vs Expo with a custom
   dev client + config plugins. Given the heavy custom native modules (JNI, NPU
   SDK, SQLCipher, biometric, ML Kit), pick the one that cleanly supports custom
   native code on Android now and iOS later. State the RN version, architecture
   (New Architecture / TurboModules + Fabric vs old bridge), and language (TS,
   strict).
5. **Define the target structure** (e.g., `/mobile` RN app at repo root, or
   replacing `app/`), how the existing `core/*` Gradle modules get consumed by the
   RN Android project, and the TS↔native module boundary (typed specs).
6. **Risks & sequencing:** call out the hardest bridges (streaming token flow from
   native inference into JS UI; download progress; voice transcript streaming;
   biometric gating the JS app start) and how you'll de-risk them first.
7. **Ask me** any blocking questions as a short numbered list (RN-vs-Expo,
   New-Architecture y/n, repo layout) before proceeding.

## Strategy guidance
- **Strangler-fig:** stand up the RN shell hosting one screen, prove a native
  module bridge end-to-end, then migrate screens one by one. The legacy Compose
  screens can remain reachable until each RN replacement reaches parity.
- **First vertical slice = Chat over LAN offload.** It exercises the whole stack:
  RN UI → TS domain → `BridgeTransport(WSS/HTTP→LAN MCP)` → native inference →
  **streamed tokens back into the RN list**. Getting this one slice solid validates
  the architecture (and doubles as Bridge step "R1").
- **Streaming contract:** native→JS token deltas via an event emitter; preserve
  the existing "incremental delta, not cumulative" semantics and the ReAct step
  flow (Thought/Action/Observation/FinalAnswer, cognition states).
- **Keep DI/data boundaries:** expose repositories (conversations, hindsight,
  model registry) through native module methods returning typed results; don't
  duplicate Room schemas in JS.

## Suggested phase order (adapt as you see fit)
1. **Scaffold** the RN Android app; integrate it with the existing Gradle build so
   `core/*` modules are reachable; app launches to a placeholder behind the
   biometric gate.
2. **Native-module bridges** (typed): inference/orchestrator (incl. streaming),
   data/repositories, security/biometric, model-hub (downloads + progress),
   speech, settings/secure storage, MCP transport via `BridgeTransport`.
3. **Chat slice** to full parity (text + streaming + ReAct + RAG + personas).
4. **Voice** screen (orb + live transcript), **Vision** screen, **Brain Market**,
   **Home**, **Settings**.
5. **Background work** (WorkManager evolution) verified from the RN app.
6. **Cutover:** remove the legacy Compose UI once every screen has an RN
   replacement at parity; keep native modules.
7. **iOS readiness pass:** confirm every native module has a defined iOS plan
   (even if unimplemented); no JS code assumes Android-only APIs.

## Testing & verification (required each phase)
- App **builds and installs** (`./gradlew assembleDebug` + the RN build) at the end
  of every phase; never merge a red build.
- **Unit tests** for TS domain/transport; keep/port meaningful Kotlin tests for the
  wrapped native modules.
- **Instrumented/E2E** for the migrated critical paths (biometric unlock → chat →
  streamed answer; model download; voice transcript).
- **Privacy check** each phase: no new outbound hosts beyond the allowlist; grep
  for accidental analytics/SDKs; confirm no secrets in JS storage.
- **Parity check:** the migrated screen matches the Compose original in behavior;
  note any intentional differences.
- Verify the **streaming smoothness** and TTFT didn't regress vs the Kotlin app on
  a real device.

## How to work
- Lead with the Phase-0 plan; **do not write migration code until I approve it.**
- Then proceed in **small, reviewable commits**, each leaving the app buildable,
  with a one-line summary of what changed vs. the plan and any new follow-ups.
- Output minimal diffs; touch only files relevant to the current step; don't scan
  or refactor unrelated modules (`core/neural`/`neurocore` are out of scope).
- Surface decisions/blockers as numbered options; pick sensible defaults and keep
  moving when the choice is conventional.
