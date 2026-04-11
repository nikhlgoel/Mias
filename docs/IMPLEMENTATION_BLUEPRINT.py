
# ══════════════════════════════════════════════════════════════════════════════
# PROJECT #001 {KID} — MASTER IMPLEMENTATION BLUEPRINT
# "Divine Buddy" Sovereign AI Ecosystem
# ══════════════════════════════════════════════════════════════════════════════
#
# This is the executable implementation plan. Every section maps directly
# to code modules, data flows, and build targets. No fluff. No theory.
# Each subsystem has: WHY it exists, HOW it works internally, WHAT files
# implement it, and how it CONNECTS to the others.
#
# Hardware:
#   Mobile  → Realme Narzo 80 Pro (Dimensity 7400, 12GB RAM, NPU)
#   Desktop → Dell G15 (i5-13th, 16GB RAM, RTX 6GB VRAM)
#
# Models:
#   Primary Brain    → Gemma-4-e4b (4B effective params, NPU-accelerated)
#   Survival Brain   → MobileLLM-R1.5 (949M params, CPU thermal fallback)
#   Desktop Brain    → Qwen3-Coder-Next (80B total / 3B active MoE)
#
# Zero-Cloud: Everything below runs on hardware we own. Period.
# ══════════════════════════════════════════════════════════════════════════════


# ╔══════════════════════════════════════════════════════════════════════════╗
# ║  SECTION 0 — THE NEURAL HIERARCHY (How the "Multiple Brains" Connect)  ║
# ╚══════════════════════════════════════════════════════════════════════════╝
#
# The system operates like a biological nervous system with three tiers:
#
#  ┌─────────────────────────────────────────────────────────────────────┐
#  │                     CONSCIOUSNESS ROUTER                          │
#  │         (Kotlin orchestrator — decides which brain fires)          │
#  │                                                                    │
#  │  Input Stimulus ──► Classify Complexity ──► Route to Brain         │
#  │                                                                    │
#  │  Routing Logic:                                                    │
#  │    SoC temp < 42°C + simple query    → Gemma-4-e4b (NPU)         │
#  │    SoC temp ≥ 42°C OR battery < 15%  → MobileLLM-R1.5 (CPU)      │
#  │    Multi-file code / deep research   → Qwen3-Coder-Next (Desktop)│
#  │    Desktop offline                   → Queue + local best-effort  │
#  │                                                                    │
#  │  The switch is SEAMLESS. The user never sees "switching models."  │
#  │  The UI just keeps flowing. Like having multiple lobes in one     │
#  │  brain — the frontal cortex (Gemma) handles conversation, the    │
#  │  cerebellum (MobileLLM) handles reflexes, and the extended       │
#  │  cortex (Qwen3) handles deep reasoning via the spinal cord      │
#  │  (Tailscale mesh).                                                │
#  └─────────────────────────────────────────────────────────────────────┘
#
#  Data Flow Between Brains:
#
#  ┌──────────┐    ReAct JSON     ┌──────────────┐   MCP JSON-RPC    ┌──────────────┐
#  │ MobileLLM│◄──(constrained)──►│  Gemma-4-e4b │◄──(Tailscale)───►│Qwen3-Coder   │
#  │  R1.5    │   thermal swap    │  (Primary)   │   offload         │  Next (PC)   │
#  │  (CPU)   │                   │  (NPU)       │                   │  (GPU/CPU)   │
#  └──────────┘                   └──────┬───────┘                   └──────┬───────┘
#                                        │                                  │
#                                        ▼                                  ▼
#                                 ┌──────────────┐                  ┌──────────────┐
#                                 │  Hindsight   │◄────sync────────►│  Hindsight   │
#                                 │  Memory (DB) │   (Tailscale)    │  Memory (DB) │
#                                 │  (Mobile)    │                  │  (Desktop)   │
#                                 └──────────────┘                  └──────────────┘
#
#  KEY INSIGHT: All three models share the SAME Hindsight Memory and the
#  SAME ReAct action schema. This means any brain can pick up mid-thought
#  where another left off. The consciousness is in the MEMORY, not the model.


# ╔══════════════════════════════════════════════════════════════════════════╗
# ║  SECTION 1 — PROJECT STRUCTURE (Complete Module Map)                   ║
# ╚══════════════════════════════════════════════════════════════════════════╝
#
# ### Revised Module Architecture
#
#  settings.gradle.kts includes:
#
#    :app                        → Android shell (UI, navigation, DI root)
#    :core:common                → Result types, dispatchers, constants
#    :core:inference             → Multi-brain inference orchestration
#    :core:inference:litert      → Gemma-4-e4b via LiteRT-LM + NPU dispatch
#    :core:inference:mobilellm   → MobileLLM-R1.5 thermal fallback engine
#    :core:inference:react       → ReAct loop engine (shared across models)
#    :core:network               → Tailscale mesh client + MCP bridge
#    :core:network:mcp           → MCP Host/Client protocol implementation
#    :core:network:handoff       → Android 17 Handoff + WoL proxy
#    :core:data                  → Room DB + DataStore + file I/O
#    :core:data:hindsight        → Hindsight Memory (episodic knowledge graph)
#    :core:soul                  → Soul Engine (LoRA personality + sentiment)
#    :core:security              → Zero-Knowledge Vault + biometric gate
#    :core:thermal               → TAWS governor + hardware telemetry
#    :core:ui                    → Liquid Glass design system components
#
#  desktop/                      → Python model server (Qwen3-Coder-Next)
#    server.py                   → FastAPI + llama.cpp inference endpoint
#    mcp_server.py               → MCP server (registered with Windows ODR)
#    wol_proxy.py                → Wake-on-LAN listener for the mesh
#    soul_sync.py                → LoRA weight sync receiver
#    auditor.py                  → Self-auditing GitHub agent
#
#  scripts/                      → Build, init, validation, MCP bridge
#  tests/                        → Cross-cutting integration + contract tests


# ╔══════════════════════════════════════════════════════════════════════════╗
# ║  SECTION 2 — CORE:INFERENCE — The Triple Brain Engine                  ║
# ╚══════════════════════════════════════════════════════════════════════════╝

# ── 2A. InferenceOrchestrator ────────────────────────────────────────────
#
# File: core/inference/src/main/kotlin/dev/kid/core/inference/InferenceOrchestrator.kt
#
# This is the "consciousness router." It owns zero model logic itself.
# It reads hardware state from TAWS and routes to the appropriate engine.
#
# Interface:
#   suspend fun process(stimulus: Stimulus): Flow<ReActStep>
#
# The orchestrator maintains a state machine:
#
#   enum class BrainState {
#       GEMMA_NPU,          // Normal operation — Gemma-4-e4b on NPU
#       MOBILELLM_SURVIVAL, // Thermal/battery fallback — MobileLLM on CPU
#       QWEN_DESKTOP,       // Heavy offload — Qwen3-Coder-Next via MCP
#       QWEN_WAKING,        // WoL sent, waiting for desktop handshake
#       DEGRADED,           // All options constrained — minimal responses
#   }
#
# Transition triggers (from TAWS):
#   GEMMA_NPU → MOBILELLM_SURVIVAL:
#     when ThermalState.socTemp >= 42°C OR BatteryState.level < 15%
#   GEMMA_NPU → QWEN_DESKTOP:
#     when task.complexity > ComplexityThreshold.OFFLOAD (multi-file code,
#     large context > 32K tokens, research requiring web tools)
#   MOBILELLM_SURVIVAL → GEMMA_NPU:
#     when ThermalState.socTemp < 38°C AND BatteryState.level > 25%
#     (hysteresis gap prevents oscillation)
#   QWEN_WAKING → QWEN_DESKTOP:
#     when MeshClient.isPeerOnline("desktop-g15") == true
#   Any → DEGRADED:
#     when all engines report Error AND desktop unreachable
#
# CRITICAL DESIGN DECISION:
#   The orchestrator never exposes which model is running to the UI layer.
#   The UI sees a single `Flow<ReActStep>` stream. Model swaps happen
#   mid-stream with context carried over via Hindsight Memory snapshot.
#   The user experiences ONE continuous consciousness.

# ── 2B. Gemma-4-e4b NPU Engine (LiteRT-LM) ─────────────────────────────
#
# File: core/inference/litert/src/main/kotlin/dev/kid/core/inference/litert/GemmaLiteRtEngine.kt
# JNI:  core/inference/litert/src/main/cpp/litert_jni_bridge.cpp
#
# Architecture (from research):
#   - Per-Layer Embeddings (PLE): Each decoder layer has its own embed table.
#     Lookup-based, not matmul-based → massive compute savings on NPU.
#   - Alternating Attention: local sliding-window + global full-context
#     layers. Dual RoPE (standard for local, proportional for global).
#   - Shared KV Cache: Final layers reuse KV states from earlier layers →
#     eliminates redundant projections, reduces memory ~30%.
#
# Deployment Pipeline:
#   1. Obtain Gemma-4-e4b checkpoint (Kaggle/HuggingFace, Apache 2.0)
#   2. Quantize to INT4 per-channel using LiteRT-LM quantization tool:
#      $ litert-lm quantize --model gemma-4-e4b --bits 4 --output gemma4e4b.litertlm
#   3. Build LiteRT-LM runtime against MediaTek NeuroPilot SDK:
#      $ cd litert-lm && mkdir build && cd build
#      $ cmake .. -DBACKEND=mediatek -DANDROID_NDK=$NDK_HOME \
#                 -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-35
#      $ make -j$(nproc) litert_lm dispatch_api_so
#      Output: liblitert_lm.so + libdispatch_api_mtk.so
#   4. Bundle into Android app as JNI native library:
#      app/src/main/jniLibs/arm64-v8a/liblitert_lm.so
#      app/src/main/jniLibs/arm64-v8a/libdispatch_api_mtk.so
#   5. Model payload stored in app-internal storage (not assets — too large):
#      /data/data/dev.kid.app/files/models/gemma4e4b.litertlm
#
# JNI Bridge Design:
#   The Kotlin layer NEVER touches raw model memory. The JNI bridge exposes:
#     - nativeLoadModel(path: String, backend: String): Long  → returns session handle
#     - nativeGenerate(handle: Long, prompt: String, maxTokens: Int,
#                      constrainedSchema: String?): String     → returns JSON
#     - nativeUnload(handle: Long): Unit
#     - nativeGetMetrics(handle: Long): String  → returns JSON with tok/s, memory, temp
#
#   CONSTRAINED DECODING (critical for ReAct):
#     The `constrainedSchema` parameter accepts a JSON Schema string.
#     LiteRT-LM uses it to force the model output into valid JSON matching
#     the schema. This eliminates hallucinated syntax in ReAct actions.
#     Example schema for ReAct output:
#     {
#       "type": "object",
#       "properties": {
#         "thought": {"type": "string"},
#         "action": {"type": "string", "enum": ["search_file", "check_battery",
#                    "send_notification", "query_hindsight", "offload_desktop",
#                    "set_reminder", "open_app", "respond_user"]},
#         "action_input": {"type": "object"},
#         "is_final": {"type": "boolean"}
#       },
#       "required": ["thought", "action", "action_input", "is_final"]
#     }
#
# Performance Targets:
#   - First token latency: < 200ms (NPU warm)
#   - Generation speed: 25-40 tok/s on NPU
#   - Memory footprint: ~2.5GB for INT4 quantized model
#   - Power draw: < 3W sustained (vs ~8W on GPU, ~12W on CPU)

# ── 2C. MobileLLM-R1.5 Survival Engine ──────────────────────────────────
#
# File: core/inference/mobilellm/src/main/kotlin/dev/kid/core/inference/mobilellm/MobileLlmEngine.kt
#
# Architecture (from Meta research paper):
#   - Deep-and-thin: 22 layers, dim=1536, hidden_dim=6144
#   - SwiGLU activation (smoother gradient flow, less heat per operation)
#   - Embedding sharing (input/output embed matrices are identical)
#   - Grouped-Query Attention (GQA) — 8 KV heads shared across 32 Q heads
#   - Only 949M params → fits in ~600MB INT4 quantized
#
# WHY this model specifically:
#   Despite being <1B params, MobileLLM-R1.5 achieves AIME score of 39.9
#   (advanced math reasoning). Its deep-and-thin architecture generates
#   LESS HEAT per token than shallow-and-wide alternatives because:
#   - Fewer wide matrix multiplications (the primary heat source)
#   - More sequential narrow operations (better cache locality, less
#     simultaneous transistor switching → lower dynamic power)
#
# Deployment:
#   - Runtime: ONNX Runtime Mobile (already in our deps)
#   - Format: INT4 ONNX model
#   - Execution: CPU only (NPU is throttled when TAWS triggers this)
#   - Context window: 4K tokens (sufficient for conversational continuity)
#   - Speed target: 15-20 tok/s on CPU (A78 cores of Dimensity 7400)
#
# Capability Boundaries (what it CAN'T do — delegated to Gemma or Qwen3):
#   - Cannot handle context > 4K tokens
#   - Cannot do multi-step code generation
#   - Cannot process complex tool chains
#   - CAN handle: casual conversation, quick Q&A, reminders, basic math,
#     emotional support, status checks, notification triage

# ── 2D. ReAct Engine (Shared Logic) ──────────────────────────────────────
#
# File: core/inference/react/src/main/kotlin/dev/kid/core/inference/react/ReActEngine.kt
#
# The ReAct (Reasoning + Acting) loop is MODEL-AGNOSTIC. It wraps any
# InferenceEngine implementation and drives the observe→think→act cycle.
#
# data class Stimulus(
#     val type: StimulusType,     // USER_MESSAGE, NOTIFICATION, CALENDAR,
#                                  // GEOFENCE, TIMER, SYSTEM_EVENT
#     val content: String,
#     val metadata: Map<String, Any>,
#     val timestamp: Long,
# )
#
# sealed interface ReActStep {
#     data class Thought(val reasoning: String) : ReActStep
#     data class Action(val tool: String, val input: Map<String, Any>) : ReActStep
#     data class Observation(val result: String) : ReActStep
#     data class FinalAnswer(val response: String) : ReActStep
#     data class ModelSwitch(val from: BrainState, val to: BrainState) : ReActStep
# }
#
# Loop Mechanics:
#   1. Receive Stimulus
#   2. Query Hindsight Memory for relevant context (last 5 interactions +
#      related mental models + user preferences)
#   3. Build prompt: system_prompt + hindsight_context + stimulus
#   4. Send to active InferenceEngine with constrained decoding schema
#   5. Parse response as ReActStep
#   6. If Action → execute tool → get Observation → feed back to step 3
#   7. If FinalAnswer → emit to UI + persist to Hindsight
#   8. Max iterations: 7 (prevent infinite loops)
#
# Tool Registry (actions the ReAct loop can take):
#   - search_file: Search local files via Room FTS
#   - check_battery: Read battery/thermal state
#   - send_notification: Post Android notification
#   - query_hindsight: Query the episodic knowledge graph
#   - offload_desktop: Route current task to Qwen3 via MCP
#   - set_reminder: Create alarm/calendar entry
#   - open_app: Launch Android app via intent
#   - respond_user: Deliver final text to UI
#   - execute_code: Run Python snippet on desktop via MCP
#   - git_operation: Git commands on desktop via MCP
#   - web_search: Search via desktop browser tool (MCP)


# ╔══════════════════════════════════════════════════════════════════════════╗
# ║  SECTION 3 — CORE:THERMAL — The TAWS Governor                         ║
# ╚══════════════════════════════════════════════════════════════════════════╝
#
# File: core/thermal/src/main/kotlin/dev/kid/core/thermal/TawsGovernor.kt
#
# TAWS = Thermal-Aware Workload Scheduler
#
# This is NOT a simple threshold check. It is a lightweight RL scheduler
# that continuously optimizes: maximize(tokens/second) subject to
# thermal equilibrium.
#
# ── Hardware Telemetry Sources ────────────────────────────────────────────
#
# Android provides thermal data via:
#   1. PowerManager.ThermalStatusListener (API 29+)
#      → Thermal status: NONE, LIGHT, MODERATE, SEVERE, CRITICAL, EMERGENCY, SHUTDOWN
#   2. HardwarePropertiesManager.getDeviceTemperatures()
#      → Reads individual thermistor data: CPU, GPU, NPU, Battery, Skin
#   3. BatteryManager extras: EXTRA_TEMPERATURE, EXTRA_VOLTAGE
#
# data class ThermalSnapshot(
#     val socTemp: Float,           // °C — SoC junction temperature
#     val skinTemp: Float,          // °C — external chassis temperature
#     val batteryTemp: Float,       // °C — battery cell temperature
#     val npuTemp: Float,           // °C — NPU-specific (via NeuroPilot if avail)
#     val batteryLevel: Int,        // 0-100%
#     val isCharging: Boolean,
#     val thermalStatus: Int,       // PowerManager thermal status enum
#     val ambientEstimate: Float,   // Estimated ambient temp (derived)
#     val timestamp: Long,
# )
#
# ── TAWS Decision Logic ──────────────────────────────────────────────────
#
# The RL model is deliberately TINY — a single-layer MLP with 8 inputs,
# 16 hidden units, 4 outputs. Runs as plain Kotlin math, no framework.
# Trained offline on thermal simulation data and fine-tuned on-device
# using temperature trace logs.
#
# Inputs (8):
#   [socTemp, skinTemp, batteryTemp, batteryLevel, npuLoad,
#    tokensInFlight, ambientEstimate, timeSinceLastThrottle]
#
# Outputs (4 action probabilities):
#   [CONTINUE_GEMMA, THROTTLE_GEMMA, SWITCH_MOBILELLM, OFFLOAD_DESKTOP]
#
# The key innovation for Punjab heat (45°C+ ambient):
#   - Standard thermal governors react AFTER throttling starts
#   - TAWS predicts thermal trajectory 30 seconds ahead using the
#     thermal inertia model: T(t+Δt) = T(t) + (Q_gen - Q_dissipate) × Δt / C_thermal
#   - If predicted T exceeds threshold, it pre-emptively switches to
#     MobileLLM BEFORE the user experiences any lag
#   - Hysteresis band: switch TO MobileLLM at 42°C, switch BACK at 38°C
#     (4°C gap prevents oscillation in borderline conditions)
#
# ── Punjab-Specific Optimizations ────────────────────────────────────────
#
# 1. Ambient Temperature Estimation:
#    Android has no direct ambient sensor. We estimate it from:
#    T_ambient ≈ T_skin - (P_total × R_thermal_chassis)
#    Where R_thermal_chassis is calibrated per-device during first boot.
#
# 2. Power Cut Mode:
#    When battery < 10% AND not charging AND ambient > 40°C:
#    → Emergency mode: Kill ReAct loop, switch to pure MobileLLM,
#      reduce context to 1K tokens, disable Liquid Glass animations,
#      switch to 60Hz display, send user a notification:
#      "Bhai, heat + low battery. Survival mode on. Plug in when you can."
#
# 3. Night Recovery Window:
#    TAWS learns that Punjab nights (10PM-6AM) are cooler. It schedules
#    heavy background tasks (Hindsight compaction, LoRA fine-tuning,
#    model preloading) to this window automatically.


# ╔══════════════════════════════════════════════════════════════════════════╗
# ║  SECTION 4 — CORE:NETWORK — Tailscale Mesh + MCP + WoL + Handoff     ║
# ╚══════════════════════════════════════════════════════════════════════════╝
#
# This is the "spinal cord" connecting the mobile brain to the desktop brain.

# ── 4A. Tailscale Mesh Client ────────────────────────────────────────────
#
# File: core/network/src/main/kotlin/dev/kid/core/network/TailscaleMeshClient.kt
#
# Tailscale runs as a system-level VPN on both devices. Our app doesn't
# manage Tailscale itself — it discovers peers and communicates over the
# encrypted WireGuard tunnel that Tailscale maintains.
#
# Implementation:
#   - Tailscale exposes a local API at http://127.0.0.1:41112 on Android
#   - We query: GET /localapi/v0/status → returns peer list with IPs
#   - Desktop peer identified by hostname match (configurable)
#   - All MCP traffic routes to desktop's Tailscale IP (100.x.y.z)
#   - Ktor CIO client used for all HTTP communication (no OkHttp to
#     avoid transitive Google Play Services dependency)
#
# Peer Discovery:
#   @Inject class TailscaleMeshClient(
#       private val httpClient: HttpClient,
#       @IoDispatcher private val dispatcher: CoroutineDispatcher,
#   ) : MeshClient {
#       private val tailscaleApi = "http://127.0.0.1:41112"
#       private val desktopHostname = "desktop-g15"  // from DataStore prefs
#
#       override suspend fun discoverPeers(): KidResult<List<PeerNode>> =
#           withContext(dispatcher) {
#               // Query Tailscale local API for peer status
#               // Parse JSON response into PeerNode list
#               // Filter for online peers with Kid MCP server tag
#           }
#   }

# ── 4B. MCP Host/Client (Android Side) ──────────────────────────────────
#
# File: core/network/mcp/src/main/kotlin/dev/kid/core/network/mcp/McpClient.kt
#
# The Android app is BOTH an MCP Host (orchestrating tool calls) and an
# MCP Client (connecting to the desktop MCP server).
#
# Protocol: JSON-RPC 2.0 over HTTP (over Tailscale WireGuard tunnel)
#
# Capabilities the Android app exposes TO the desktop (as MCP resources):
#   - Device telemetry (battery, thermal, location)
#   - Notification stream
#   - Calendar events
#   - Contact list (local, encrypted)
#   - Camera/microphone trigger
#
# Tools the Android app invokes ON the desktop (as MCP tool calls):
#   - generate: Send prompt to Qwen3-Coder-Next
#   - execute_code: Run Python in sandboxed environment
#   - git_status / git_commit / git_push: Git operations
#   - file_read / file_write: Access desktop filesystem
#   - web_search: Controlled browser automation
#   - audit_code: Trigger self-auditing agent
#
# Message flow for a desktop offload:
#   1. ReAct loop decides: action = "offload_desktop"
#   2. InferenceOrchestrator calls McpClient.callTool("generate", params)
#   3. McpClient serializes JSON-RPC request
#   4. Ktor sends POST to http://100.x.y.z:8401/rpc (desktop Tailscale IP)
#   5. Desktop MCP server processes with Qwen3-Coder-Next
#   6. Response flows back through Tailscale → Ktor → McpClient → ReAct
#   7. ReAct treats the response as an Observation and continues the loop

# ── 4C. Windows ODR Integration (Desktop Side) ───────────────────────────
#
# File: desktop/mcp_server.py
#
# The desktop MCP server registers with Windows ODR for discoverability:
#   $ odr mcp add kid-mcp-manifest.json
#
# kid-mcp-manifest.json:
#   {
#     "id": "dev.kid.desktop-server",
#     "name": "Kid Desktop Nervous System",
#     "description": "Local Qwen3-Coder-Next inference + tools for Kid ecosystem",
#     "transport": { "type": "sse", "url": "http://localhost:8401/sse" },
#     "tools": [
#       { "name": "generate", ... },
#       { "name": "execute_code", ... },
#       { "name": "git_operation", ... },
#       { "name": "file_access", ... }
#     ]
#   }
#
# MCP Server Containment:
#   Windows ODR runs MCP servers in a contained agent session by default.
#   For Kid, we use package identity via MSIX to grant full access to:
#   - Local filesystem (user's code repos)
#   - Git CLI
#   - Python runtime (sandboxed)
#   We explicitly DO NOT grant: network access beyond Tailscale, registry
#   modification, or admin privileges.

# ── 4D. Wake-on-LAN Proxy ────────────────────────────────────────────────
#
# File: desktop/wol_proxy.py (runs on always-on local device, e.g., router)
#
# Problem: WoL uses Layer 2 (MAC broadcast). Tailscale is Layer 3 (IP).
#          You can't send a WoL packet over Tailscale.
#
# Solution: A tiny Python script on a flashed OpenWrt router (or Raspberry Pi)
# on the same LAN as the Dell G15. This proxy:
#   1. Listens on Tailscale IP for HTTP POST /wake
#   2. Receives { "mac": "AA:BB:CC:DD:EE:FF" } from Android
#   3. Broadcasts WoL magic packet on local LAN (Layer 2)
#   4. Dell G15 BIOS receives magic packet → wakes from S3/S4/S5
#   5. Windows boots → Tailscale auto-starts → Kid MCP server auto-starts
#   6. Android detects desktop peer online via periodic polling
#
# Fallback: If no WoL proxy exists, the user must manually wake the PC.
# The app shows: "Desktop asleep. Wake it up or I'll handle it locally."

# ── 4E. Android 17 Handoff (Cross-Device Continuity) ─────────────────────
#
# File: core/network/handoff/src/main/kotlin/dev/kid/core/network/handoff/HandoffManager.kt
#
# Android 17 introduces native handoff APIs for transferring app state
# between nearby devices. When the user walks to their desktop:
#
# 1. Android detects proximity to the Dell G15 (Bluetooth LE + Tailscale)
# 2. Notification: "Continue on Desktop?"
# 3. User confirms → app packages:
#    - Current ReAct trace (last 10 steps)
#    - Hindsight Memory snapshot (last 50 interactions)
#    - Active conversation state
#    - Soul Engine LoRA blend coefficients
#    - UI scroll position and input draft
#    Into a HandoffActivityData object (encrypted, signed)
# 4. State routes to Windows ODR via Tailscale
# 5. Desktop client app receives state → reconstructs exact context
# 6. Qwen3-Coder-Next picks up the conversation mid-thought
#
# Reverse handoff (desktop → mobile) works identically.
# The user experiences ZERO context loss. It's one consciousness.


# ╔══════════════════════════════════════════════════════════════════════════╗
# ║  SECTION 5 — CORE:DATA:HINDSIGHT — Biomimetic Episodic Memory        ║
# ╚══════════════════════════════════════════════════════════════════════════╝
#
# File: core/data/hindsight/src/main/kotlin/dev/kid/core/data/hindsight/HindsightMemory.kt
#
# Standard RAG (vector search over document chunks) fails for a personal
# AI because it has NO CONCEPT OF TIME or CHANGE. If you tell Kid an idea
# on Monday and revise it Wednesday, RAG returns BOTH as equally relevant.
#
# Hindsight Memory solves this with a 3-tier knowledge hierarchy:
#
#  ┌─────────────────────────────────────────────────────────────────────┐
#  │ TIER 3: MENTAL MODELS (highest abstraction)                       │
#  │   "User prefers Kotlin over Java"                                  │
#  │   "User's project #002 evolved from chat-app → full AI platform"  │
#  │   "User is stressed about college deadlines in May"               │
#  │   Stored as: nodes in a local knowledge graph (Room + FTS)         │
#  │   Updated by: reflect() operation after every 10 interactions      │
#  ├─────────────────────────────────────────────────────────────────────┤
#  │ TIER 2: OBSERVATIONS (pattern recognition)                        │
#  │   "User asked about thermal throttling 3 times this week"         │
#  │   "User's code style is moving toward Clean Architecture"         │
#  │   Stored as: derived facts with confidence scores + timestamps    │
#  │   Updated by: periodic background analysis (Night Recovery Window)│
#  ├─────────────────────────────────────────────────────────────────────┤
#  │ TIER 1: RAW FACTS (ground truth)                                  │
#  │   "User said 'I want to build a local AI' on 2026-04-11"         │
#  │   "User pushed commit abc123 to Project #001 at 22:30"           │
#  │   Stored as: timestamped entries in Room (with FTS5 indexing)     │
#  │   Updated by: every ReAct interaction automatically               │
#  └─────────────────────────────────────────────────────────────────────┘
#
# ── Graph-of-Thought Reasoning ───────────────────────────────────────────
#
# When the ReAct loop queries Hindsight, it doesn't do flat vector search.
# It does GRAPH TRAVERSAL:
#
#   1. Text query → FTS5 search on Raw Facts → candidate facts
#   2. Each fact has edges to Observations it contributed to
#   3. Each Observation has edges to Mental Models it supports
#   4. Walk the graph upward: facts → observations → models
#   5. Return the subgraph as structured context for the LLM
#   6. Include TEMPORAL MARKERS: "This fact was updated 2 days ago"
#                                "This model was formed over 15 interactions"
#
# This means Kid understands HOW ideas evolved, not just what they are.
#
# ── reflect() Operation ─────────────────────────────────────────────────
#
# Every 10 interactions (configurable), the ReAct loop runs a meta-step:
#   1. Collect last 10 Raw Facts
#   2. Query existing Observations and Mental Models
#   3. Prompt Gemma/MobileLLM with constrained schema:
#      "Given these new facts and existing knowledge, what observations
#       should be updated? What mental models should change?"
#   4. Output schema:
#      {
#        "new_observations": [{"text": "...", "confidence": 0.85}],
#        "updated_models": [{"id": "...", "revision": "..."}],
#        "deprecated_facts": ["fact_id_1", "fact_id_2"]
#      }
#   5. Apply changes to the knowledge graph
#
# ── Multi-User (Circle of Trust) ────────────────────────────────────────
#
# Hindsight supports multiple user identities:
#   data class HindsightUser(
#       val id: String,
#       val alias: String,          // "Jas" (friend), "Me" (owner)
#       val trustLevel: TrustLevel, // OWNER, INNER_CIRCLE, ACQUAINTANCE
#       val relationWeight: Float,  // 0.0-1.0, affects memory priority
#   )
#
# Facts are tagged with source user. Query results are filtered by
# trust level. OWNER facts always included. INNER_CIRCLE facts included
# in shared project contexts. ACQUAINTANCE facts excluded by default.
#
# ── Database Schema (Room) ───────────────────────────────────────────────
#
# @Entity(tableName = "raw_facts")
# data class RawFact(
#     @PrimaryKey val id: String,
#     val content: String,
#     val sourceUserId: String,
#     val timestamp: Long,
#     val conversationId: String,
#     val embedding: ByteArray?,    // Optional: for hybrid search
#     val isDeprecated: Boolean = false,
# )
#
# @Entity(tableName = "observations")
# data class Observation(
#     @PrimaryKey val id: String,
#     val content: String,
#     val confidence: Float,
#     val factIds: List<String>,    // TypeConverter for JSON list
#     val createdAt: Long,
#     val updatedAt: Long,
# )
#
# @Entity(tableName = "mental_models")
# data class MentalModel(
#     @PrimaryKey val id: String,
#     val content: String,
#     val observationIds: List<String>,
#     val strength: Float,          // How many facts support this
#     val createdAt: Long,
#     val updatedAt: Long,
#     val version: Int,             // Tracks how many times revised
# )
#
# Virtual FTS5 table for full-text search across all tiers.


# ╔══════════════════════════════════════════════════════════════════════════╗
# ║  SECTION 6 — CORE:SOUL — Personality Evolution Engine                  ║
# ╚══════════════════════════════════════════════════════════════════════════╝
#
# File: core/soul/src/main/kotlin/dev/kid/core/soul/SoulEngine.kt
#
# The Soul Engine makes Kid feel like a PERSON, not a tool. It manages
# dynamic personality through LoRA weight blending.
#
# ── LoRA Adapter System ──────────────────────────────────────────────────
#
# Instead of one monolithic personality, Kid maintains a set of specialized
# ~50MB LoRA adapter blocks stored locally:
#
#   lora_empathy.bin      — Supportive, understanding tone
#   lora_humor.bin        — Witty, playful responses
#   lora_utility.bin      — Direct, task-focused, minimal chat
#   lora_technical.bin    — Deep technical explanations
#   lora_punjabi.bin      — Punjabi linguistic patterns, Ludhiana slang
#   lora_hype.bin         — High energy, motivational
#
# ── Dynamic LoRA Merging ─────────────────────────────────────────────────
#
# Before each inference call, the Soul Engine computes blend coefficients:
#
#   W_soul = W_base + Σ(αᵢ × ΔWᵢ)
#
# Where:
#   W_base = frozen Gemma-4-e4b base weights (NPU)
#   αᵢ = blend coefficient for LoRA i (0.0 to 1.0)
#   ΔWᵢ = LoRA delta weights for adapter i
#
# The coefficients are computed by a tiny sentiment classifier:
#   1. Analyze last 3 user messages for emotional valence
#   2. Cross-reference with Hindsight Mental Models (is user stressed?)
#   3. Factor in time-of-day (late night → more empathy, less hype)
#   4. Factor in task type (code review → utility+technical, casual → humor+punjabi)
#
# Example blend for "user stressed about exam at midnight":
#   α_empathy = 0.8, α_utility = 0.6, α_punjabi = 0.4
#   α_humor = 0.1, α_hype = 0.0, α_technical = 0.2
#
# ── Emotional Mirroring ──────────────────────────────────────────────────
#
# The Soul Engine doesn't just detect emotion — it MIRRORS appropriately:
#   - User excited → Kid matches energy (but doesn't exceed it)
#   - User frustrated → Kid acknowledges, then pivots to solutions
#   - User in flow state → Kid becomes minimal (short, direct answers)
#   - User nostalgic → Kid references shared Hindsight memories
#
# Implementation: A lightweight sentiment model (~5MB ONNX) runs on CPU
# before every Gemma/MobileLLM inference. Its output feeds the LoRA
# blend policy network.
#
# ── Punjabi/Ludhiana Localization ────────────────────────────────────────
#
# The lora_punjabi adapter is fine-tuned on:
#   - Punjabi-English code-switching patterns
#   - Ludhiana-specific slang (Kant/ਕੈਂਟ, Mizaaj/ਮਿਜ਼ਾਜ)
#   - Contextually appropriate formality shifting
#   - Cricket metaphors for technical explanations
#
# This is NOT translation. Kid speaks the way the user actually talks —
# mixing English and Punjabi naturally in a single sentence.
# Example: "Bro, tere code di efficiency vaddi hai, par ithe ik edge case
#           miss ho gya — iss null check nu handle kar pehlan."


# ╔══════════════════════════════════════════════════════════════════════════╗
# ║  SECTION 7 — CORE:SECURITY — Zero-Knowledge Vault                     ║
# ╚══════════════════════════════════════════════════════════════════════════╝
#
# File: core/security/src/main/kotlin/dev/kid/core/security/ZkVault.kt
#
# Kid holds deeply personal data — thoughts, code, plans, relationships.
# The security model treats the AI's identity as seriously as a human's.
#
# ── Biometric Gate ───────────────────────────────────────────────────────
#
# On app launch:
#   1. Android BiometricPrompt (fingerprint/face) → unlocks "Private Soul"
#   2. Without biometric → "Public Face" mode (generic assistant, no
#      personality, no Hindsight, no personal tools)
#   3. Safe Word fallback: A specific phrase in chat (e.g., "jaago kiddo")
#      unlocks the Private Soul without biometric (for hands-free use)
#
# ── Exclusivity Lock ────────────────────────────────────────────────────
#
# If unauthorized access detected (3 failed biometric + no safe word):
#   1. Soul Engine drops all LoRA adapters → generic personality
#   2. Hindsight Memory becomes read-locked (no queries return results)
#   3. MCP bridge disconnects from desktop
#   4. App visually transforms to a bland default assistant
#   5. Silent alert sent to owner's secondary device via Tailscale
#
# ── Data Encryption ─────────────────────────────────────────────────────
#
# - Room database: SQLCipher (AES-256-CBC) with key from Android Keystore
# - LoRA adapters: AES-256-GCM encrypted at rest, decrypted into memory
# - Hindsight exports: NaCl (libsodium) sealed boxes for cross-device sync
# - All Tailscale traffic: Already WireGuard encrypted (ChaCha20-Poly1305)
# - Key derivation: HKDF-SHA256 from biometric-bound Android Keystore key
#
# ── Zero-Knowledge Principle ────────────────────────────────────────────
#
# When the ReAct loop needs to reference sensitive data (e.g., passwords
# stored in Hindsight), it uses a ZK accessor:
#   1. ReAct requests: "Does user have a GitHub token?"
#   2. ZK Vault responds: "Yes" (boolean) — NOT the token itself
#   3. ReAct requests: "Use the GitHub token to push commit"
#   4. ZK Vault executes the git push INTERNALLY, returns only the result
#   5. The LLM NEVER sees the raw secret in its context window
#
# This prevents model context leakage — even if someone extracts the
# model's attention weights, secrets were never in the token stream.


# ╔══════════════════════════════════════════════════════════════════════════╗
# ║  SECTION 8 — CORE:UI — Liquid Glass Design System                     ║
# ╚══════════════════════════════════════════════════════════════════════════╝
#
# File: core/ui/src/main/kotlin/dev/kid/core/ui/liquidglass/
#
# ── The Design Philosophy ────────────────────────────────────────────────
#
# The UI is NOT a chat app with fancy colors. It's a LIVING MATERIAL.
# Every pixel responds to:
#   1. What Kid is thinking (ReAct state)
#   2. How Kid feels (Soul Engine blend coefficients)
#   3. What the hardware is doing (TAWS thermal state)
#   4. What the user is doing (scroll, touch, idle)
#
# ── Implementation via Compose + RuntimeShader ───────────────────────────
#
# Android 17's RenderEffect and RuntimeShader (AGSL) allow per-pixel
# shader effects without leaving Compose:
#
# Glassmorphism Stack:
#   Layer 1: Content (text, images, cards)
#   Layer 2: Frosted Glass Overlay (RuntimeShader with Gaussian blur)
#   Layer 3: Refraction Layer (displacement map shader)
#   Layer 4: Ambient Glow (radial gradient tied to Soul Engine)
#
# @Composable
# fun LiquidGlassPanel(
#     modifier: Modifier,
#     blurRadius: Dp = 20.dp,
#     tint: Color = Color.White.copy(alpha = 0.15f),
#     refractionStrength: Float = 0.02f,  // 0.0 = flat, 0.1 = heavy distortion
#     cognitionGlow: CognitionState,      // From ReAct engine
# ) {
#     // RuntimeShader for frosted glass effect
#     // RenderEffect.createBlurEffect for backdrop blur
#     // Custom AGSL shader for light refraction simulation
#     // Animate glow color based on cognitionGlow:
#     //   THINKING  → slow pulse blue
#     //   ACTING    → steady green
#     //   WAITING   → soft gold
#     //   STRESSED  → warm amber (thermal warning)
#     //   OFFLOADING → subtle purple (desktop active)
# }
#
# ── Proactive Nudge Widgets ──────────────────────────────────────────────
#
# Kid doesn't wait to be asked. It surfaces thoughts as ambient widgets:
#
# data class Nudge(
#     val type: NudgeType,          // SUGGESTION, REMINDER, INSIGHT, GREETING
#     val content: String,
#     val priority: Float,          // 0.0-1.0
#     val expiresAt: Long,
#     val sourceModel: BrainState,  // Which brain generated this
# )
#
# Nudges appear as soft, pulsing cards at the bottom of the home screen.
# They dissolve when acknowledged or expired. Low priority nudges stack
# silently; high priority nudges cause a gentle haptic pulse.
#
# Example nudges:
#   "Ludhiana 36°C right now. Battery at 72%. We're good for deep work."
#   "Your Project #002 hasn't been touched in 3 days. Want to revisit?"
#   "You usually study at this time. Want me to quiz you on yesterday's notes?"
#
# ── Adaptive Performance ────────────────────────────────────────────────
#
# When TAWS enters SURVIVAL mode:
#   1. Disable RuntimeShader effects (huge GPU savings)
#   2. Reduce to static frosted overlay (pre-rendered bitmap)
#   3. Drop to 60Hz refresh
#   4. Simplify animations to opacity-only transitions
#   5. Visual indicator: glass tint shifts to warm amber
#   The user SEES that Kid is in a constrained state — it's honest.


# ╔══════════════════════════════════════════════════════════════════════════╗
# ║  SECTION 9 — DESKTOP:QWEN3 — The Deep Reasoning Engine               ║
# ╚══════════════════════════════════════════════════════════════════════════╝
#
# File: desktop/server.py (enhanced from current scaffolding)
#
# ── Model Architecture (from research) ───────────────────────────────────
#
# Qwen3-Coder-Next:
#   - 80B total params, 3B activated per token (MoE)
#   - 48 layers with hybrid layout:
#     12 × (3 × GatedDeltaNet→MoE + 1 × GatedAttention→MoE)
#   - GatedDeltaNet: Linear attention with learned forget gate
#     → O(1) memory per token for long sequences (vs O(n) for full attention)
#     → 32 linear attention heads for V, 16 for QK, head_dim=128
#   - GatedAttention (every 4th layer): Full attention for precision
#     → 16 Q heads, 2 KV heads, head_dim=256
#     → RoPE with dim=64
#   - MoE: 512 experts, 10 activated + 1 shared expert per token
#     → Expert intermediate dim = 512
#   - Context length: 262,144 tokens natively (256K)
#
# WHY this is perfect for the Dell G15 (16GB RAM + 6GB VRAM):
#   - Only 3B params active per token → fits in 6GB VRAM with INT4
#   - GatedDeltaNet layers use linear memory → 256K context without OOM
#   - KV cache is TINY (only attention layers every 4th, and only 2 KV heads)
#   - Quantized GGUF format: ~5GB on disk, ~8GB runtime with context
#
# Deployment:
#   $ ollama pull qwen3-coder-next          (easiest)
#   OR
#   $ llama-cpp-python with Qwen3-Coder-Next-GGUF (Q4_K_M quantization)
#
# Server configuration (in desktop/server.py):
#   - llama.cpp backend with n_gpu_layers=-1 (offload all to RTX)
#   - n_ctx=65536 (start conservative, scale up if VRAM allows)
#   - Flash attention enabled
#   - Mmap enabled for fast model loading
#   - Temperature: 1.0, top_p: 0.95, top_k: 40 (from Qwen3 best practices)
#
# ── Self-Auditing GitHub Agent ───────────────────────────────────────────
#
# File: desktop/auditor.py
#
# When triggered (manually or by MCP tool call), the auditor:
#   1. Reads the git log for recent commits
#   2. Diffs each commit against the parent
#   3. Feeds the diff + project context (from Hindsight) to Qwen3-Coder-Next
#   4. Prompt (constrained JSON output):
#      "Analyze this diff for:
#       - Unhandled edge cases (Negative Space analysis)
#       - Missing error handling
#       - Security vulnerabilities (OWASP Top 10)
#       - Missing tests
#       - Style violations
#       - Documentation gaps
#       Output your findings as structured JSON."
#   5. Results stored in Hindsight Memory as Observations
#   6. If critical issues found → draft a GitHub Issue
#   7. Optionally: auto-generate CHANGELOG.md and propose "Genetic Upgrades"
#      for the next release
#
# This runs asynchronously. The user doesn't wait for it.
# They just see: "I reviewed your last 3 commits. Found 2 edge cases
# in the MeshClient error handling. Want me to fix them?"


# ╔══════════════════════════════════════════════════════════════════════════╗
# ║  SECTION 10 — 4-WEEK IMPLEMENTATION SPRINT                            ║
# ╚══════════════════════════════════════════════════════════════════════════╝
#
# ┌─────────────────────────────────────────────────────────────────────────┐
# │ WEEK 1: SUBSTRATE FOUNDATION                                          │
# │ Goal: Model running on NPU. ReAct loop executing. Tests passing.      │
# ├─────────────────────────────────────────────────────────────────────────┤
# │                                                                        │
# │ Day 1-2: LiteRT-LM Build Pipeline                                     │
# │   ☐ Download Gemma-4-e4b weights (Apache 2.0 license)                 │
# │   ☐ Install Android NDK r27+ and MediaTek NeuroPilot SDK              │
# │   ☐ Clone LiteRT-LM and build against MediaTek backend:               │
# │     cmake -DBACKEND=mediatek -DANDROID_ABI=arm64-v8a                   │
# │   ☐ Quantize model: INT4 per-channel → gemma4e4b.litertlm             │
# │   ☐ Write JNI bridge (litert_jni_bridge.cpp)                          │
# │   ☐ Test: Load model, generate 10 tokens, verify NPU dispatch         │
# │   ☐ Benchmark: Measure tok/s, memory, thermal baseline                │
# │                                                                        │
# │ Day 3-4: ReAct Engine + Constrained Decoding                          │
# │   ☐ Implement ReActEngine with tool registry                          │
# │   ☐ Define constrained JSON schema for ReAct output                   │
# │   ☐ Wire GemmaLiteRtEngine to ReAct as first InferenceEngine          │
# │   ☐ Implement 3 basic tools: respond_user, check_battery, search_file │
# │   ☐ Test: Full ReAct loop — stimulus → thought → action → observation │
# │   ☐ Test: Constrained decoding produces valid JSON 100% of the time   │
# │                                                                        │
# │ Day 5: InferenceOrchestrator + Basic Routing                          │
# │   ☐ Implement BrainState state machine                                │
# │   ☐ Wire PowerManager.ThermalStatusListener for basic thermal reading │
# │   ☐ Test: Orchestrator routes to Gemma when healthy                   │
# │   ☐ Integration test: End-to-end user message → ReAct → response     │
# │                                                                        │
# │ WEEK 1 EXIT CRITERIA:                                                  │
# │   ✓ Gemma-4-e4b runs on Narzo 80 Pro NPU at 25+ tok/s               │
# │   ✓ ReAct loop executes 3+ tools with constrained JSON output        │
# │   ✓ All unit tests pass, zero lint warnings                          │
# │   ✓ ./scripts/init.sh --check succeeds                               │
# │                                                                        │
# ├─────────────────────────────────────────────────────────────────────────┤
# │ WEEK 2: COGNITIVE IGNITION                                            │
# │ Goal: Memory works. Soul has personality. Survival mode functional.    │
# ├─────────────────────────────────────────────────────────────────────────┤
# │                                                                        │
# │ Day 1-2: Hindsight Memory                                             │
# │   ☐ Create Room schema: raw_facts, observations, mental_models        │
# │   ☐ Add FTS5 virtual table for full-text search                       │
# │   ☐ Implement HindsightMemory.store(fact) and .query(text)            │
# │   ☐ Implement Graph-of-Thought traversal (facts→obs→models)           │
# │   ☐ Implement reflect() operation with constrained output schema      │
# │   ☐ Wire Hindsight into ReAct loop (step 2: query before inference)   │
# │   ☐ Test: Store 20 facts, reflect, verify observations emerge         │
# │   ☐ Test: Query returns temporally-ordered subgraph                   │
# │                                                                        │
# │ Day 3: Soul Engine v1                                                  │
# │   ☐ Train lightweight sentiment classifier (distilled, ~5MB ONNX)     │
# │   ☐ Create initial LoRA adapters (empathy, utility, punjabi stubs)    │
# │   ☐ Implement blend coefficient computation from sentiment            │
# │   ☐ Integrate Soul Engine into inference pipeline (pre-inference step)│
# │   ☐ Test: Different user sentiments produce different blend weights   │
# │                                                                        │
# │ Day 4: MobileLLM-R1.5 + TAWS v1                                      │
# │   ☐ Quantize MobileLLM-R1.5 to INT4 ONNX                            │
# │   ☐ Implement MobileLlmEngine with ONNX Runtime                      │
# │   ☐ Implement TawsGovernor with basic threshold logic                 │
# │   ☐ Wire thermal swap: Gemma → MobileLLM at 42°C, back at 38°C      │
# │   ☐ Test: Simulated thermal event triggers model swap                 │
# │   ☐ Test: Conversation continuity maintained across swap              │
# │                                                                        │
# │ Day 5: Multi-User Memory (Circle of Trust)                            │
# │   ☐ Add HindsightUser entity with trust levels                       │
# │   ☐ Tag facts with source user, filter queries by trust              │
# │   ☐ Test: Owner facts always returned, acquaintance facts filtered   │
# │                                                                        │
# │ WEEK 2 EXIT CRITERIA:                                                  │
# │   ✓ Hindsight stores, reflects, and queries across 3 tiers           │
# │   ✓ Soul Engine modulates personality based on user sentiment         │
# │   ✓ TAWS swaps Gemma↔MobileLLM based on temperature                 │
# │   ✓ Context preserved across model swaps                             │
# │   ✓ Multi-user memory respects trust boundaries                      │
# │                                                                        │
# ├─────────────────────────────────────────────────────────────────────────┤
# │ WEEK 3: SURVIVAL & DESKTOP MESH                                       │
# │ Goal: Desktop connected. MCP working. WoL operational.                │
# ├─────────────────────────────────────────────────────────────────────────┤
# │                                                                        │
# │ Day 1-2: Tailscale Mesh + MCP Client (Android)                        │
# │   ☐ Implement TailscaleMeshClient (local API at :41112)              │
# │   ☐ Implement McpClient (JSON-RPC 2.0 over Ktor)                    │
# │   ☐ Implement tool registry for desktop tools (generate, git, file)  │
# │   ☐ Add "offload_desktop" action to ReAct tool registry              │
# │   ☐ Test: Mock MCP server, verify JSON-RPC round-trip                │
# │   ☐ Test: ReAct loop correctly routes heavy tasks to desktop         │
# │                                                                        │
# │ Day 3: Desktop Server (Qwen3-Coder-Next)                              │
# │   ☐ Download Qwen3-Coder-Next-GGUF (Q4_K_M, ~5GB)                   │
# │   ☐ Enhance desktop/server.py with llama-cpp-python                  │
# │   ☐ Implement MCP server (desktop/mcp_server.py) with tool handlers  │
# │   ☐ Register with Windows ODR: odr mcp add kid-mcp-manifest.json    │
# │   ☐ Test: Android McpClient → Tailscale → Desktop MCP → Qwen3       │
# │   ☐ Benchmark: End-to-end latency for offloaded generation           │
# │                                                                        │
# │ Day 4: Wake-on-LAN + TAWS RL                                          │
# │   ☐ Deploy wol_proxy.py on local always-on device                    │
# │   ☐ Wire Android: detect desktop offline → send WoL → poll for wake  │
# │   ☐ Upgrade TAWS from threshold-based to MLP policy network          │
# │   ☐ Train MLP on collected thermal traces from Week 1-2              │
# │   ☐ Test: WoL wake + handshake + inference within 60 seconds         │
# │   ☐ Test: TAWS MLP outperforms static thresholds on trace data       │
# │                                                                        │
# │ Day 5: Android 17 Handoff                                             │
# │   ☐ Implement HandoffManager with onHandoffActivityRequested()       │
# │   ☐ Package: ReAct trace + Hindsight snapshot + Soul coefficients    │
# │   ☐ Desktop receiver: reconstruct state in Qwen3 context             │
# │   ☐ Test: Start conversation on phone, continue on desktop seamlessly│
# │                                                                        │
# │ WEEK 3 EXIT CRITERIA:                                                  │
# │   ✓ Android ↔ Desktop inference offloading works via MCP             │
# │   ✓ WoL wakes Dell G15 from Android command                         │
# │   ✓ TAWS RL scheduler active and logging decisions                   │
# │   ✓ Handoff transfers full state between devices                     │
# │   ✓ Windows ODR registers Kid MCP server                             │
# │                                                                        │
# ├─────────────────────────────────────────────────────────────────────────┤
# │ WEEK 4: DIVINE SYNC & RELEASE                                         │
# │ Goal: Ship it. Beautiful, secure, audited.                             │
# ├─────────────────────────────────────────────────────────────────────────┤
# │                                                                        │
# │ Day 1-2: Liquid Glass UI                                               │
# │   ☐ Implement LiquidGlassPanel composable with RuntimeShader          │
# │   ☐ Implement CognitionGlow (blue/green/gold/amber state colors)     │
# │   ☐ Implement Nudge widgets with pulsing animations                  │
# │   ☐ Wire UI to ReAct state stream (shows thinking/acting/waiting)    │
# │   ☐ Implement adaptive performance degradation for SURVIVAL mode     │
# │   ☐ Test: 60fps maintained with glass effects on Narzo 80 Pro        │
# │   ☐ Test: Graceful degradation when TAWS triggers survival           │
# │                                                                        │
# │ Day 3: Zero-Knowledge Vault                                            │
# │   ☐ Integrate BiometricPrompt with Android Keystore                  │
# │   ☐ Implement SQLCipher encryption for Room database                 │
# │   ☐ Implement Exclusivity Lock (3 failed attempts → go dark)         │
# │   ☐ Implement ZK accessor for sensitive Hindsight data               │
# │   ☐ Implement Safe Word unlock fallback                              │
# │   ☐ Test: Unauthorized access triggers dark mode correctly           │
# │   ☐ Test: LLM context NEVER contains raw secrets                    │
# │                                                                        │
# │ Day 4: Self-Auditing Agent + Polish                                    │
# │   ☐ Implement desktop/auditor.py with Negative Space analysis        │
# │   ☐ Wire auditor to MCP so Android can trigger reviews               │
# │   ☐ Generate CHANGELOG.md for release from Hindsight data            │
# │   ☐ Run full test suite: ./scripts/init.sh --check                   │
# │   ☐ Run dependency audit: bash scripts/audit_deps.sh                 │
# │   ☐ Fix all lint warnings and test failures                          │
# │                                                                        │
# │ Day 5: GitHub Release #001                                             │
# │   ☐ Final integration testing on physical Narzo 80 Pro               │
# │   ☐ Final integration testing on Dell G15                            │
# │   ☐ Cross-device test: full handoff + offload + return               │
# │   ☐ Record demo video showing all three brains in action             │
# │   ☐ Tag release: v0.1.0-kid                                         │
# │   ☐ Push to GitHub with full README + architecture diagram           │
# │                                                                        │
# │ WEEK 4 EXIT CRITERIA:                                                  │
# │   ✓ Liquid Glass UI renders at 60fps with cognitive state colors     │
# │   ✓ Biometric lock + Safe Word + Exclusivity Lock all functional     │
# │   ✓ Self-auditing agent reviews commits and finds real issues        │
# │   ✓ GitHub Release #001 {Kid} tagged and published                   │
# │   ✓ Zero cloud dependencies. Zero external API calls.               │
# │   ✓ All tests green. All audits clean.                               │
# └─────────────────────────────────────────────────────────────────────────┘


# ╔══════════════════════════════════════════════════════════════════════════╗
# ║  SECTION 11 — DEPENDENCY MAP (What Depends On What)                    ║
# ╚══════════════════════════════════════════════════════════════════════════╝
#
# Build order (leaves first):
#
#   core:common           → (no deps)
#   core:security         → core:common
#   core:thermal          → core:common
#   core:data             → core:common, core:security
#   core:data:hindsight   → core:data, core:common
#   core:soul             → core:common, core:data:hindsight
#   core:inference:react  → core:common, core:data:hindsight
#   core:inference:litert → core:common, core:inference:react
#   core:inference:mobilellm → core:common, core:inference:react
#   core:inference        → core:common, core:thermal, core:soul,
#                           core:inference:litert, core:inference:mobilellm,
#                           core:inference:react
#   core:network:mcp      → core:common, core:network
#   core:network:handoff  → core:common, core:network, core:data:hindsight
#   core:network          → core:common
#   core:ui               → core:common, core:thermal, core:soul
#   app                   → ALL core modules
#
# This DAG ensures:
#   - No circular dependencies
#   - Each module is independently testable
#   - Heavy modules (inference) can be mocked in UI tests
#   - Security module has no downstream deps (it's a leaf service)


# ╔══════════════════════════════════════════════════════════════════════════╗
# ║  SECTION 12 — TESTING STRATEGY (Per-Module)                            ║
# ╚══════════════════════════════════════════════════════════════════════════╝
#
# Every module follows the same test pattern:
#
# 1. CONTRACT TESTS (interface compliance)
#    Test that implementations satisfy their interface contract.
#    Use MockK to verify InferenceEngine, MeshClient, etc.
#
# 2. UNIT TESTS (logic correctness)
#    Test internal logic: TAWS decision function, LoRA blend computation,
#    Hindsight graph traversal, ReAct loop iteration, ZK accessor.
#
# 3. INTEGRATION TESTS (cross-module)
#    Test module boundaries: ReAct + Hindsight, Orchestrator + TAWS,
#    McpClient + MeshClient, Soul + Hindsight.
#
# 4. PROPERTY TESTS (invariants)
#    - TAWS: for all thermal inputs, output is one of the 4 valid actions
#    - ReAct: loop always terminates within maxIterations
#    - Hindsight: reflect() never deletes OWNER facts
#    - ZK Vault: raw secrets never appear in InferenceEngine input
#    - Soul Engine: all blend coefficients sum to <= 4.0 (bounded)
#
# 5. THERMAL SIMULATION TESTS
#    Replay recorded thermal traces from the Narzo 80 Pro against TAWS
#    and verify it never enters SHUTDOWN state.
#
# Test file locations:
#   core/inference/src/test/       → InferenceOrchestrator, ReAct, Engines
#   core/thermal/src/test/         → TAWS policy, thermal math
#   core/data/hindsight/src/test/  → Graph traversal, reflect(), queries
#   core/soul/src/test/            → Blend computation, sentiment classifier
#   core/security/src/test/        → ZK accessor, exclusivity lock
#   core/network/mcp/src/test/     → JSON-RPC serialization, tool dispatch
#   core/ui/src/test/              → Compose UI tests, state rendering
#   tests/integration/             → Cross-module flows
#   tests/contract/                → MCP protocol compliance, ODR manifest
#   tests/thermal/                 → Recorded trace replay tests


# ╔══════════════════════════════════════════════════════════════════════════╗
# ║  SECTION 13 — CRITICAL PATH & RISK MITIGATIONS                        ║
# ╚══════════════════════════════════════════════════════════════════════════╝
#
# Risk 1: LiteRT-LM MediaTek build fails
#   Mitigation: Fallback to ONNX Runtime Mobile with MediaPipe GenAI task.
#   Impact: ~15% slower inference, but NPU still used via NNAPI delegate.
#   Files affected: core/inference/litert/ → swap to core/inference/mediapipe/
#
# Risk 2: Gemma-4-e4b INT4 quantization degrades ReAct JSON quality
#   Mitigation: Use GPTQ instead of naive per-channel quantization.
#   Additional: Tighten constrained decoding schema to reduce hallucination.
#   Test: JSON validity rate must be >99.5% across 1000 samples.
#
# Risk 3: Qwen3-Coder-Next OOM on Dell G15 (16GB RAM + 6GB VRAM)
#   Mitigation: Use Q4_K_M quantization (~5GB), reduce n_ctx to 32768,
#   offload only 20 layers to GPU (rest on CPU with mmap).
#   Benchmark: If latency > 10s/token, switch to Qwen3-30B-A3B (smaller MoE).
#
# Risk 4: Tailscale not installed / VPN broken
#   Mitigation: App detects missing Tailscale and guides installation.
#   Fallback: All heavy tasks queued locally, processed when mesh reconnects.
#   UI shows: "Desktop connection unavailable. Working locally."
#
# Risk 5: Android 17 Handoff API not available on target device
#   Mitigation: Manual handoff via QR code (Hindsight state as encrypted
#   JSON, displayed as QR, scanned by desktop companion app).
#
# Risk 6: 45°C Punjab summer destroys sustained inference
#   Mitigation: TAWS already handles this. But additionally:
#   - Ship with a "Summer Mode" toggle that pre-emptively caps Gemma
#     to 15 tok/s (reducing power draw 40%)
#   - Schedule heavy Hindsight reflect() to 2-5 AM window
#   - UI shows ambient temperature and adjusts expectations


# ╔══════════════════════════════════════════════════════════════════════════╗
# ║  END OF MASTER IMPLEMENTATION BLUEPRINT                                ║
# ║  Project #001 {Kid} — "Divine Buddy" Sovereign AI Ecosystem            ║
# ║  Zero-Cloud · Local-First · Private · Alive                            ║
# ╚══════════════════════════════════════════════════════════════════════════╝
