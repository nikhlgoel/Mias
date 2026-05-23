# Mias — Product Requirements Document

**Status:** Pre-1.0, active development
**Owner:** @nikhlgoel
**Last updated:** 2026-05-23

---

## 1. Vision

A private AI assistant that lives entirely on a student's Android phone. No
cloud calls, no telemetry, no accounts. When the user has access to their own
PC, heavy workloads can be offloaded to it over a local mesh — still without
ever touching a third-party server.

Mias is for people who spend most of their day on their phone — between
classes, commuting, away from a laptop — and want a capable AI without
shipping their data to OpenAI, Anthropic, or Google's cloud.

---

## 2. Target users

| Persona | What they need from Mias |
|---|---|
| **Undergraduate student** (primary) | Help with notes, reading, code, homework explanations, drafting. Wants speed, privacy from the institution, and zero subscription cost. |
| **Privacy-conscious developer** | Local code generation, refactoring help, web-fetch with no usage logs leaving the device. |
| **Field user with intermittent connectivity** | An assistant that works offline by default and seamlessly uses the desktop GPU when at home. |

Out of scope: enterprise tenants, multi-user devices, child accounts, anyone
who wants a cloud-synced assistant.

---

## 3. Success metrics

These are first-version targets, measurable at the user level — no analytics
SDK is shipped, so all measurement is local and self-reported during alpha.

| Metric | Target |
|---|---|
| Time-to-first-token on Gemma 1.5B Q4_K_M, Pixel 7 class | ≤ 1.5 s |
| Continuous chat throughput on the same device | ≥ 8 tok/s |
| App cold start to chat screen (biometric unlock included) | ≤ 4 s |
| Survival-mode fallback latency when thermal trips | ≤ 200 ms perceived |
| Desktop-offload round trip on local Wi-Fi (Qwen3 32B Q4_K_M) | ≤ 1 s TTFT, ≥ 25 tok/s |
| Network calls during normal use that hit a non-allowlisted host | 0 |

---

## 4. Scope — what 1.0 must do

### 4.1 Conversation
- Streaming chat with a local LLM, model selectable per role (chat, code,
  reasoning, creative, research, embedding).
- Persistent conversation history encrypted on-device (Room + SQLCipher).
- Voice input via on-device speech recognition (ML Kit), with a voice-only
  chat screen (`VoiceChatScreen` + animated orb).
- ReAct-style tool use (Thought → Action → Observation) with the agent
  capabilities listed in §4.4.

### 4.2 Brain Market (model hub)
- Browse a curated catalog of verified GGUF / LiteRT / ONNX models.
- Search HuggingFace for additional public GGUF models.
- Download with progress, pause/resume, SHA-256 verification (when set).
- Auto-assign roles based on device RAM and quantization size.
- Optional HuggingFace personal-access token for gated repos.

### 4.3 Multi-brain orchestration
The `InferenceOrchestrator` is the "consciousness router". It chooses the
right engine for every turn based on:
- **Intent** — chat vs code vs research, currently keyword-derived
  (`inferRole`).
- **Thermal / battery state** — `TawsGovernor` may force survival mode,
  throttle the primary, or offload to desktop.
- **NPU availability** — Gemma on Google AI Edge SDK when the device chip
  supports it, falling back to CPU llama.cpp otherwise.

### 4.4 Agent capabilities
File system, clipboard, web fetch, web research, app launch, calculator,
date/time, media-store file generation. Each capability declares parameters
and a permission expectation; ReAct can invoke any of them.

### 4.5 Desktop offload (optional)
- Python FastAPI server on the user's PC, running llama-cpp-python.
- MCP (Model Context Protocol) 2025-03-26 over JSON-RPC HTTP.
- Auth via `X-Mias-Token` shared secret.
- Reached over local LAN IP or Tailscale tunnel.

### 4.6 Security and privacy
- Biometric gate on every cold start (Class 3 strong biometric only).
- SQLCipher-encrypted database; AES-256-GCM for any auxiliary at-rest data.
- Android backup disabled by manifest.
- Allowlisted hosts for outbound traffic (HuggingFace + GitHub release CDNs
  only).
- No analytics, no crash reporters, no third-party AI SDKs.

### 4.7 Self-improvement (background)
The `core/evolution/` daemon, triggered by WorkManager when the device is
charging and idle, consolidates recent conversations into Hindsight memory
and prunes duplicates by cosine-similarity.

---

## 5. Non-goals

- Cloud sync across devices.
- Multi-user accounts.
- Enterprise admin / MDM controls.
- Plug-in marketplace for third-party agent capabilities.
- Real-time vision streaming (still photo + VLM only, when wired).
- iOS, web, or desktop-as-primary distribution.
- Anything that requires sending user data to a third party.

---

## 6. Release gates (in priority order)

These define what blocks 1.0. They are loosely ordered by impact.

1. **Model download works end-to-end** for the curated list and HF search,
   verified by SHA-256 when present.
2. **Orchestrator never crashes** on a missing model / thermal snapshot /
   MCP outage — every degraded path returns a useful response.
3. **MCP desktop offload** completes a real prompt against a real Qwen3 model
   over Tailscale.
4. **Vision pipeline wired** (`VisionWorker` → `VisionChatScreen`) using a
   small VLM, *or* the camera button removed.
5. **LoRA / Soul integration** — either runtime persona adjustment, or the
   sliders removed from the UI to match reality.
6. **Semantic intent router** replaces `inferRole` keyword matching.
7. **Settings screen surfaces** the HuggingFace token and desktop IP/port
   fields (backend already wired).
8. **Schema migrations** for `ModelHubDatabase` replace the current
   "destructive on change" fallback so users don't lose role assignments.

---

## 7. Open questions / decisions deferred

- **Persona configuration surface.** The Soul sliders exist; how do they
  map to the system prompt? Per-role personas? Per-conversation overrides?
- **Vision model choice.** Gemma 3n with vision adapter vs MobileVLM
  vs Qwen2-VL 2B — depends on which has a maintained GGUF / LiteRT build.
- **Hindsight retrieval strategy.** Currently flat cosine similarity over
  all memory; whether to introduce time-decay or episodic / semantic split.
- **Telemetry.** Are *any* opt-in local-only metrics acceptable to help
  the user understand their own usage? Default: no.
- **Distribution.** APK side-load only for v1, Play Store later, or never?

---

## 8. Glossary

| Term | Meaning |
|---|---|
| **Brain** | A loaded model in a specific role. |
| **Brain Market** | The in-app model catalog and download hub. |
| **TAWS** | Thermal-Aware Workload Steering — the governor that picks
            between primary / survival / desktop based on heat. |
| **ReAct** | The Thought → Action → Observation loop driving tool use. |
| **Hindsight** | The user's long-term memory store, embeddings-indexed. |
| **Soul** | The persona / tone layer — currently UI-only. |
| **MCP** | Model Context Protocol — the JSON-RPC dialect used to talk to
          the desktop offload server. |
