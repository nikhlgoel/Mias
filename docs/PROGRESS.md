# Mias — Development Progress

This file tracks meaningful milestones, setbacks, and decisions over time. Updated manually when something real changes.

---

## Release #001 — "Kid" (April 2026)

**What works:**
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

**What's incomplete:**
- VoiceChatScreen UI missing (ViewModel exists)
- VisionChatScreen UI missing (VisionWorker exists)
- NPU execution — llama.cpp runs CPU only; NPU requires Google AI Edge SDK (planned)
- MCP client is basic HTTP, not spec-compliant
- RegexIntentExtractor — no semantic routing
- LoRA blend policy exists but runtime merge is not implemented
- ReActEngine has no max-step guard (infinite loop risk)
- HindsightMemory has no dedup (same facts written multiple times)

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

---

## Next Up

1. VoiceChatScreen — ViewModel is ready, just needs UI (1 day work)
2. Google AI Edge SDK — makes NPU claim honest for Gemma models  
3. ReActEngine max-step guard — prevents infinite agent loops
4. MCP spec compliance — initialization handshake + capability negotiation
5. Rust desktop server prototype — start `desktop/mias-server/`
