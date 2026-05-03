# Mias — Full Project Analysis
*Generated May 2026 · Based on source zip + screenshots + deployed page*

---

## 1. What You Actually Have (Honest State Assessment)

### Architecture — Genuinely Strong
12-module Android project with clean separation:

```
app/                    ← Nav host, screens, ViewModels
core/agent/             ← Tool calling, ReAct loop, capabilities
core/common/            ← Shared models, KidResult, dispatchers
core/data/              ← SQLCipher DB, conversation repo, Hindsight memory
core/evolution/         ← Background self-improvement engine
core/inference/         ← LlamaCpp JNI, EmbeddingEngine, InferenceOrchestrator
core/language/          ← Intent extraction (Regex-based currently)
core/model-hub/         ← HuggingFace registry, download manager, model DB
core/network/           ← Tailscale mesh client, MCP client
core/resilience/        ← Retry, checkpoint, connectivity, device health
core/security/          ← BiometricGate, ZkVault, GuardrailProcessor
core/soul/              ← Personality blend, sentiment analysis
core/speech/            ← STT + TTS engines
core/thermal/           ← TawsGovernor (thermal throttling)
core/ui/                ← Compose components, theme, glass system
desktop/                ← Python server (llama-cpp + Docker)
```

This is a legitimately well-structured project. Multi-module Gradle with DI is the right call for this scale.

### What's Built and Working
- Full Compose UI layer with coherent dark theme
- Animated neural eye orb (Canvas-drawn, properly animated)
- SplashScreen → HomeScreen → Chat/Voice/Agent navigation
- InferenceOrchestrator routing (NPU → CPU → Desktop fallback logic)
- LlamaCpp JNI bridge (CMakeLists + mias_jni_bridge.cpp)
- ThermalMonitor + TawsGovernor (thermal-aware model switching)
- BiometricGate + SQLCipher (security primitives)
- Tailscale mesh client (desktop offload path)
- HindsightMemory (long-term compressed memory)
- Agent capabilities: AppLaunch, Calculator, Clipboard, DateTime, FileSystem, WebFetch, WebResearch
- ReActEngine (Thought → Action → Observation loop)
- Evolution background worker (6-hour consolidation cycle)
- Desktop Python server with Docker + llama-cpp-python

### Where There Are Gaps (Critical Honesty)

| Component | Claimed | Reality |
|---|---|---|
| "Full power on NPU" | Gemma-4 via NPU | llama.cpp doesn't use Android NPU. Needs Google AI Edge SDK or QNN backend |
| "87 unit tests" | 87 tests | ~8 test files visible, likely mostly stubs |
| VideoChat | Button exists in HomeScreen | No VisionChatScreen file exists |
| VoiceChat | Button + ViewModel | VoiceChatScreen file missing from screens/ |
| LoRA blend | Soul sliders in UI | LoraBlendPolicy.kt exists but actual LoRA merging is not implemented at runtime |
| MCP client | Full MCP support | Basic client only, not spec-compliant with sampling/resources |
| Language intent | "Picks the right model" | RegexIntentExtractor — no semantic routing |

---

## 2. UI — Changes, Fixes, and Additions

### A. Immediate Fixes

**"Kid" removal from all visible text** (see Section 5 for exact file map):
- SplashScreen: `{Kid}` → `{Mias}`
- KidInputBar placeholder: `"Talk to Kid..."` → `"Talk to Mias..."`
- EvolutionScreen: `"Kid evolves silently"` → `"Mias evolves silently"`
- EvolutionService notification: `"Kid"` → `"Mias"` (shows in Android notification shade)
- InferenceOrchestrator system prompt: `"You are Kid"` → `"You are Mias"`
- McpClient: `"Kid Android"` → `"Mias Android"`
- WebResearchCapability user-agent: `"Kid/4.0"` → `"Mias/4.0"`

**App icon — critical fix:**
- `ic_kid_background.xml` and `ic_kid_foreground.xml` need renaming to `ic_mias_*`
- `ic_launcher.xml` references need updating
- The OLD icon (Image 1) is a dark red aggressive eyeball — wrong brand feel
- The NEW icon (Images 2, 6) — teal neural eye — is correct and should be what ships

**Icon design analysis (Images 1 vs 2/6):**
- Image 1 has a maroon-red "threat" aesthetic. The background.xml even has a comment `<!-- Outer threat ring -->` and `<!-- Razor-like spikes -->`. This was the "Mias" icon from the previous iteration.
- Images 2 and 6 show the `{Kid}` splash — which ironically has the BETTER icon: teal concentric rings with center dot, no aggression, pure tech aesthetic.
- **Recommendation:** The SplashScreen.kt Canvas code is the definitive icon spec. Export this exact design as adaptive icon layers. The current `ic_kid_foreground.xml` needs to be redrawn to match the SplashScreen orb (teal concentric rings, hex grid iris, center dot). The maroon eyeball in ic_kid_background.xml should be replaced with the dark navy radial gradient from the splash.

### B. Missing Screens (Must Build)

**VoiceChatScreen** — VoiceChatViewModel exists but no screen. This is the primary input mode for a voice-first AI app. The Chat button path works, Voice is broken.

**VisionChatScreen** — Video button in HomeScreen leads nowhere. If VideoChat uses the VisionWorker, a screen must exist.

**Minimal VoiceChat UI needed:**
```
- Full-screen animated orb (pulsing when listening, color shift when processing)
- Live transcript text (bottom third)
- Hold-to-speak or tap-to-toggle
- Back button only
```

### C. Design Additions Worth Making

**HomeScreen nudge cards** — Currently NudgeCard component exists but what populates it? Should show: active model name, last memory consolidation time, thermal status.

**ModelHub polish** — ModelCard component exists. The download flow needs: progress ring, storage warning (<2GB left), hash verification indicator.

**Settings → System Status** (Image 5 reference):
- Soul Blend sliders are all at 30% flat in the screenshot — the persistence isn't working or defaults aren't set
- The temperature reading (37°C) and battery (15%) are correctly shown
- Qwen3-Coder shows a cloud-off icon correctly when Tailscale is disconnected — this is good UX

---

## 3. Architecture & Framework

### What to Solidify

**NPU path is the most important fix.**
The current stack: `llama.cpp → JNI → Android` runs on CPU with possible GPU acceleration via OpenCL. It does NOT hit the NPU.

To actually use the NPU on modern Snapdragon devices (the "Full power on NPU" claim):

```
Option A: Google AI Edge SDK (best for Gemma)
- Google ships Gemma 3n specifically for AI Edge
- Uses MediaPipe LLM Inference API
- Automatically targets NPU via LiteRT + NNAPl delegation
- Drop-in for Gemma family: 1B, 4B variants

Option B: llama.cpp + QNN backend (Snapdragon only)
- llama.cpp has experimental QNN (Qualcomm Neural Networks) backend
- More control, harder to set up
- Only works on Snapdragon X Elite / 8 Gen 3+

Option C: ONNX Runtime + NNAPI delegate (MobileLLM path)
- MobileLLM from Meta has ONNX exports
- ONNX Runtime Android with NNAPI delegate
- This is the right path for MobileLLM-R1.5
```

**Recommendation:** Keep llama.cpp for CPU/Qwen3 path. Add Google AI Edge SDK as a second engine for Gemma. Two engines, one InferenceOrchestrator interface.

**Module structure is correct — don't change it.**
The `dev.kid.*` package name is fine to keep internally. Changing 150+ files for a cosmetic rename adds zero user value at this stage.

**Language module is weak.** `RegexIntentExtractor` is fine for MVP but "Picks the right model" is a strong claim. At minimum, add a keyword-weighted router:
- Code keywords → Qwen3 if available
- Quick math/datetime → lightweight model
- Default → Gemma

**MCP client needs spec alignment.**
The McpClient is a basic HTTP wrapper. The official MCP spec (2024-11 version) requires: initialization handshake, capability negotiation, typed resource/tool/prompt schemas. If you're positioning this as an MCP-capable app (which strengthens the portfolio story), bring McpClient to spec.

### Technical Debt Map

| Debt | Priority | Effort |
|---|---|---|
| NPU execution (AI Edge SDK) | High | 2-3 days |
| VoiceChatScreen (missing) | High | 1 day |
| VisionChatScreen (missing) | Medium | 1 day |
| MCP spec compliance | Medium | 2 days |
| RegexIntentExtractor → semantic router | Medium | 1 day |
| LoRA blend runtime implementation | Low | Complex, 1 week |
| Package rename dev.kid → dev.mias | Low | Mechanical, 2 hours |

---

## 4. Deep Research — What to Solidify for Robustness

### A. Google AI Edge SDK (Gemma NPU — the big one)

The gap between "NPU" in the UI and actual NPU execution is the highest-risk claim in the project. Google's AI Edge SDK:

- Ships `com.google.mediapipe:tasks-genai` 
- Handles Gemma 3n 1B and 4B specifically
- Auto-delegates to NPU via NNAPI on Pixel 9, Samsung Galaxy S25, and other NPU-equipped devices
- Provides streaming token callbacks compatible with your existing Compose flow

Your `InferenceEngine` interface already has the right shape for adding a second engine implementation.

### B. Tailscale Mesh — Robustness Gaps

`TailscaleMeshClient` connects to your desktop over Tailscale. Current gap: assumes Tailscale app is installed and logged in on the Android device. This dependency needs:
- Check if `com.tailscale.ipn` package is installed
- If not: graceful degradation to CPU-only with a clear UI message in SystemStatus
- WireGuard keepalive handling for long-lived connections
- Timeout + retry with exponential backoff (ResilienceModule exists — use it here)

### C. Desktop Server — Production Hardening

`desktop/server.py` runs llama-cpp-python in Flask/FastAPI. For actual reliability:
- Add `/health` endpoint with model warmup status (the landing page already shows this curl command — make sure it works)
- Add auth token (shared secret in Tailscale network only)
- Streaming response via SSE (server-sent events) — check if implemented
- Docker healthcheck in Dockerfile

### D. SQLCipher + Hindsight — Memory Integrity

`HindsightMemory` compresses conversations into long-term memory. Gap: no deduplication. If the same fact appears in 10 conversations, it'll be consolidated 10 times. Add cosine similarity check before writing new hindsight entries — your `VectorUtils.kt` and `EmbeddingEngine` are already there for this.

### E. ReAct Loop — Real-World Issues

`ReActEngine` runs the Thought/Action/Observation cycle. Known production issues with this pattern:
- Infinite loops (model keeps calling the same tool) → add max-step guard (5-7 steps)
- Tool output too large for context → truncate to 2000 chars with ellipsis marker
- Model hallucinates tool names → validate against ToolRegistry before executing

Check if these guards are in `ReActEngine.kt` — if not, they're critical for stable operation.

---

## 5. Minor Changes — Exact File Map

### A. .gitignore Updates Needed

Current .gitignore has these **issues**:
1. `.github/` is ignored — this means GitHub Actions workflows won't push. If you want CI/CD, this should NOT be ignored. Remove the `.github/` line.
2. `docs/IMPLEMENTATION_BLUEPRINT.py` — the filename reveals AI-assisted planning. **Add to gitignore.**
3. `docs/PRD.docx` and `docs/DOC#001.docx` — internal planning docs. **Add to gitignore.**
4. `docs/DEPLOYMENT_STATUS.md` — keep this, it's user-facing.
5. `docs/V4_ARCHITECTURE.md` — keep this, it's technical documentation.

Add to .gitignore:
```
# Internal planning docs (not for public repo)
docs/*.docx
docs/IMPLEMENTATION_BLUEPRINT.py
docs/DEPLOYMENT_STATUS.md
```

And remove:
```
.github/
```

### B. "Kid" Removal — Exact File + Line Map

**User-visible text to change NOW:**

| File | Line | Change |
|---|---|---|
| `app/.../splash/SplashScreen.kt` | 252 | `"{Kid}"` → `"{Mias}"` |
| `core/ui/.../components/KidInputBar.kt` | 43 | `"Talk to Kid..."` → `"Talk to Mias..."` |
| `app/.../evolution/EvolutionScreen.kt` | 168 | `"Kid evolves silently"` → `"Mias evolves silently"` |
| `core/evolution/.../service/EvolutionService.kt` | 78 | `.setContentTitle("Kid")` → `"Mias"` |
| `core/evolution/.../service/EvolutionService.kt` | 89 | `"Kid Background Thinking"` → `"Mias Background Thinking"` |
| `core/evolution/.../service/EvolutionService.kt` | 92 | `"Kid evolves"` → `"Mias evolves"` |
| `core/inference/.../orchestrator/InferenceOrchestrator.kt` | 120 | `"You are Kid"` → `"You are Mias"` |
| `core/network/.../mcp/McpClient.kt` | 56 | `"Kid Android"` → `"Mias Android"` |
| `core/agent/.../capabilities/WebResearchCapability.kt` | 143 | `"Kid/4.0"` → `"Mias/4.0"` |

**App icon rename:**
```
app/src/main/res/drawable/ic_kid_background.xml → ic_mias_background.xml
app/src/main/res/drawable/ic_kid_foreground.xml → ic_mias_foreground.xml
app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml → update references inside
```

**Class/package names** (don't touch yet — no user impact, creates noise):
`KidApplication`, `KidNavHost`, `KidTheme`, `KidColors`, etc. → schedule for v5.0 when package is stable. The `dev.kid` package ID, once on Play Store, can never change anyway.

### C. App Icon Design Spec (What to Build)

Based on the SplashScreen Canvas code (which is the correct aesthetic):

```
Foreground layer (ic_mias_foreground.xml):
- Background fill: transparent
- Outer glow rings: 3 concentric, NeonCyan (#00FFFF) at 4/8/12% alpha
- Iris circle: 58dp radius, NeonCyan stroke at 50% alpha, 1.5dp width
- Hex grid: 6 lines connecting iris ring points, 30% alpha
- Radial spokes: 6 lines center→iris, 20% alpha  
- Pupil: radial gradient #00FFFF → #00CCCC → #006666, 28dp radius
- Inner dark: Background color (#0A0A0F) at 85% alpha, 16dp radius
- Center dot: #00FFFF solid, 4dp radius

Background layer (ic_mias_background.xml):
- Solid fill: #0D1525 (slightly lighter than pure black for icon shelf visibility)
- No rings, no spikes, no red — clean dark navy
```

The adaptive icon (foreground + background) will look correct on both circular and squircle launchers.

---

## 6. Priority Action List

**This week:**
1. Apply all "Kid" → "Mias" text replacements (30 min, see table above)
2. Fix .gitignore (remove `.github/`, add doc ignores)
3. Rename icon drawables, redraw foreground to match splash aesthetic

**Next sprint:**
4. Add Google AI Edge SDK as second inference engine for Gemma (this makes the NPU claim honest)
5. Build VoiceChatScreen (ViewModel is ready, just needs UI)
6. Add max-step guard and tool validation to ReActEngine

**Later:**
7. MCP spec compliance
8. Semantic intent router replacing RegexIntentExtractor
9. HindsightMemory dedup via embedding similarity
