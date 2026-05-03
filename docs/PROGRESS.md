# Mias — Development Progress

This file tracks meaningful milestones, setbacks, and decisions over time. Updated manually when something real changes.

---

## Release #002 — "Mias Engine" (May 2026)

**What's new:**
- **VoiceChatScreen** — full voice-only UI with AnimatedOrb, live transcript, pulsing mic toggle. Wired to VoiceChatViewModel and navigable from Home screen Voice chip
- **Google AI Edge SDK** — NPU-accelerated Gemma inference via MediaPipe GenAI LlmInference on supported devices (Pixel, Samsung). InferenceOrchestrator now routes NPU → LlamaCpp → Desktop with automatic fallback
- **ReActEngine hardened** — max-step guard (7 iterations), tool name validation against ToolRegistry, tool output truncation at 2000 chars to prevent context overflow
- **MCP 2024-11 compliance** — full initialization handshake (initialize → capabilities → notifications/initialized), typed models, auto-initialization guard
- **HindsightMemory dedup** — cosine similarity check (threshold 0.92) against last 50 facts prevents redundant embeddings
- **Tailscale dependency check** — `discoverPeers()` now verifies Tailscale is installed before attempting local API calls, surfaces clear error instead of silent failure
- **Desktop server hardened** — `X-Mias-Token` shared secret authentication on `/rpc`, enhanced `/health` endpoint with model status and version, Docker healthcheck, `notifications/initialized` handler
- **Unit tests** — ReActEngine (max-step, truncation, tool validation), InferenceOrchestrator (engine selection routing), VectorUtils (serialization round-trip, cosine similarity), RegexIntentExtractor (extended coverage)
- **Repository hygiene** — GitHub issue templates (bug report, feature improvement), PR template, README "what doesn't work yet" section

**What works (cumulative):**
- Full Compose UI — dark theme, neural eye orb, splash → home → chat/agent/evolution/voice navigation
- Chat screen with streaming token output
- VoiceChatScreen with AnimatedOrb and live transcript
- LlamaCpp JNI bridge — CPU inference on-device
- Google AI Edge engine — NPU inference for Gemma models
- InferenceOrchestrator — routes between NPU, on-device CPU, and desktop offload
- Thermal governor (TawsGovernor) — drops model tier when device overheats
- Biometric gate + SQLCipher encrypted database
- HindsightMemory — long-term compressed memory with deduplication
- Agent capabilities: AppLaunch, Calculator, Clipboard, DateTime, FileSystem, WebFetch, WebResearch
- ReActEngine — hardened Thought → Action → Observation loop with safety guards
- EvolutionWorker — background 6-hour consolidation cycle
- Tailscale mesh client with dependency checking for desktop offload
- Desktop Python server (Docker + llama-cpp-python) with auth and health monitoring
- MCP client — 2024-11 spec compliant
- ModelHub — HuggingFace registry + download manager

**What's still incomplete:**
- VisionChatScreen UI missing (VisionWorker exists)
- RegexIntentExtractor — regex-based, not semantic; wrong model can be selected for task type
- LoRA blend policy exists but runtime merge is not implemented
- Thermal ML training data — TawsGovernor uses heuristics, not a trained model

---

## Release #001 — "Kid" (April 2026)

**What worked:**
- Full Compose UI — dark theme, neural eye orb, splash → home → chat/agent/evolution navigation
- Chat screen with streaming token output
- LlamaCpp JNI bridge — CPU inference on-device
- InferenceOrchestrator — routes between on-device and desktop offload
- Thermal governor (TawsGovernor) — drops model tier when device overheats
- Biometric gate + SQLCipher encrypted database
- HindsightMemory — long-term compressed conversation memory
- Agent capabilities: AppLaunch, Calculator, Clipboard, DateTime, FileSystem, WebFetch, WebResearch
- ReActEngine — Thought → Action → Observation loop
- EvolutionWorker — background 6-hour consolidation cycle
- Tailscale mesh client for desktop offload
- Desktop Python server (Docker + llama-cpp-python)
- ModelHub — HuggingFace registry + download manager

**What was incomplete:**
- VoiceChatScreen UI missing (ViewModel existed)
- VisionChatScreen UI missing (VisionWorker existed)
- NPU execution — llama.cpp runs CPU only; NPU required Google AI Edge SDK
- MCP client was basic HTTP, not spec-compliant
- ReActEngine had no max-step guard (infinite loop risk)
- HindsightMemory had no dedup (same facts written multiple times)

**Setbacks:**
- Initial icon design (maroon aggressive eyeball) replaced with teal neural orb matching splash canvas
- "Kid" release name removed from all user-visible text — was only ever an internal release label

---

## Decisions Log

| Decision | Rationale |
|---|---|
| Package: `io.mias.app` | Cross-platform forward-compatible. Not tied to Android Play Store conventions. |
| Keep `dev.kid` namespace internally | Changing 150+ files for cosmetic rename adds no user value. Namespace is not user-visible. |
| Release tags as `#001`, `#002` | Simple, sequential. Not semantic versioning — this isn't a library. |
| Multi-language architecture | C++ for inference kernel, Rust planned for desktop server, Go for MCP bridge. See `LANGUAGE_ARCHITECTURE.md`. |
| No Play Store | APK direct release via GitHub. Avoids Play Store review friction during early development. |
| NPU via Google AI Edge | MediaPipe GenAI chosen over raw NNAPI for Gemma model compatibility and delegate abstraction. |
| Cosine similarity threshold 0.92 | Empirically balances catching near-duplicates without falsely rejecting related but distinct facts. |
| MCP 2024-11 spec | Latest stable spec version; includes capability negotiation needed for future tool discovery. |

---

## Next Up

1. VisionChatScreen — camera button leads nowhere, VisionWorker is ready
2. Semantic intent router — replace RegexIntentExtractor with embedding-based classification
3. LoRA runtime merge — make soul sliders affect model output
4. Rust desktop server — start `desktop/mias-server/` to replace Python prototype
5. Performance baselines — document actual tokens/sec on real devices
