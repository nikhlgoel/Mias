# Mias — Design Document

This is how the pieces fit together. It describes what's in the repo today,
not what we wish were there. If you find a divergence, fix the code or fix
this document.

---

## 1. Layered architecture

```
┌─────────────────────────────────────────────────────────────┐
│  app/                                                        │
│  Compose screens · ViewModels · Navigation · Permissions    │
└───────────────────────┬─────────────────────────────────────┘
                        │ Hilt-injected use cases
┌───────────────────────▼─────────────────────────────────────┐
│  core/inference/InferenceOrchestrator                        │
│  ──────────────────────────────────                          │
│  picks an engine, runs ReAct loop, emits ReActStep flow      │
│                                                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │ GoogleAiEdge │  │  LlamaCpp    │  │   McpClient      │  │
│  │  (NPU/CPU)   │  │  (JNI/CPU)   │  │  (HTTP→desktop)  │  │
│  └──────────────┘  └──────────────┘  └──────────────────┘  │
└──────────────────────────────┬──────────────────────────────┘
                               │ tool calls
┌──────────────────────────────▼──────────────────────────────┐
│  core/agent/ToolRegistry + capabilities (file, web, …)      │
└─────────────────────────────────────────────────────────────┘

Supporting:
- core/data       Room + SQLCipher (conversations, hindsight, models)
- core/security   BiometricGate, GuardrailProcessor, ZkVault
- core/thermal    TawsGovernor (picks survival vs primary vs desktop)
- core/resilience DeviceHealthMonitor, connectivity, retry
- core/evolution  Background daemon for hindsight consolidation
- core/network    Ktor clients, MCP, Tailscale mesh
```

---

## 2. The request lifecycle

A single user turn travels this path:

1. **UI capture** — `ChatViewModel` (or `VoiceChatViewModel`) packages the
   user's text into a `Stimulus`.
2. **Safety gate** — `InferenceOrchestrator.process()` first hands the prompt
   to `GuardrailProcessor.evaluateInput()`. A `Rejected` verdict short-circuits
   with `ReActStep.FinalAnswer(suggestedResponse)`.
3. **Engine selection** — `TawsGovernor.latestSnapshot` is consulted:
   - `null` → degrade to survival path immediately (no synthesized snapshot).
   - `CONTINUE_PRIMARY` / `THROTTLE_PRIMARY` → infer the role from the prompt
     and pick NPU Gemma when available, else CPU primary.
   - `SWITCH_SURVIVAL` → tiny model (currently Qwen2.5 0.5B).
   - `OFFLOAD_DESKTOP` → MCP client when configured, else fall back to NPU/CPU.
4. **Model load on demand** — `ensureModelLoaded()` asks `ModelManager` for the
   best `InstalledModel` for the role and calls `engine.loadModel(path)`.
5. **ReAct loop** — `ReActEngine.execute()` streams `TokenChunk` events as the
   model generates, parses each completed JSON object as
   `{ thought, action, action_input, is_final }`, emits a `Thought` step,
   then either:
   - terminates with `FinalAnswer` if `is_final` or `action == "respond_user"`,
   - or executes the named tool via `ToolRegistry`, emits an `Observation`,
     and continues for up to 7 iterations.
6. **Persistence** — `ConversationRepository.append()` writes the user turn
   and the assistant's final answer to the Room DB; `HindsightDao` records
   any new long-term memory facts derived during the turn.
7. **UI rendering** — the ViewModel collects the `Flow<ReActStep>` and updates
   state; `CognitionState` (`THINKING`/`ACTING`/`WAITING`/`IDLE`) drives any
   visual indicator.

---

## 3. Key contracts

### `InferenceEngine` (core/inference)
```
suspend fun loadModel(path: String): MiasResult<Unit>
fun generateStream(prompt: String, maxTokens: Int): Flow<MiasResult<String>>
suspend fun unloadModel(): MiasResult<Unit>
fun isModelLoaded(): Boolean
```
**Streaming contract:** each `Success` emission is an **incremental delta**
(new tokens only), not the cumulative response. Engines unable to stream true
deltas should emit the full response as a single chunk.

### `AgentCapability` (core/agent)
Each tool declares `name`, `description`, `parameters: List<ToolParameter>`,
and a `suspend fun execute(input: Map<String, String>): MiasResult<String>`.
The `ToolRegistry` exposes them by name to ReAct.

### MCP protocol (core/network/mcp)
JSON-RPC 2.0 over HTTP, protocol version negotiated at `initialize`. The
client supports `2025-03-26` and accepts the server's negotiated value. Auth
header `X-Mias-Token` is sent when configured. `tools/call` results conform to
the MCP spec envelope: `{ content: [{ type: "text", text: "…" }], isError }`.

### `MeshClient` (core/network)
Discovers peers and forwards request payloads. Today implemented by
`TailscaleMeshClient` against the desktop local API; the Android local-API
path does not exist, so users currently set the desktop IP manually in
Settings — the orchestrator reaches it directly via `McpClient`.

---

## 4. State model

The orchestrator owns two `StateFlow`s that the UI observes:

| State | Values |
|---|---|
| `BrainState` | `GEMMA_NPU`, `MOBILELLM_SURVIVAL`, `QWEN_DESKTOP`, `QWEN_WAKING`, `DEGRADED` |
| `CognitionState` | `IDLE`, `THINKING`, `ACTING`, `WAITING` |

Transitions are emitted as `ReActStep.ModelSwitch(from, to)` so the UI can
animate a "brain swap" banner.

---

## 5. Storage

| Database | Purpose | Key tables |
|---|---|---|
| `mias_conversations.db` (Room + SQLCipher) | Chat history, derived facts | `conversations`, `messages`, `hindsight_*` |
| `mias_model_hub.db` (Room) | Installed model metadata, download queue | `installed_models`, `download_queue` |

Model binaries are not stored in the DBs — they live on disk under
`${context.filesDir}/models/`. Partial downloads live in `.partial/`.

**Migration policy:** `ModelHubDatabase` currently uses
`fallbackToDestructiveMigration` — this is **temporary**. Before 1.0, real
migrations must replace it so users keep their role assignments across
schema bumps. (The model files on disk persist either way.)

---

## 6. Security model

| Threat | Mitigation |
|---|---|
| Someone picks up the unlocked phone | `BiometricGate` (Class 3 / STRONG) at every cold start. |
| Disk dump of a stolen phone | SQLCipher on the conversations DB; EncryptedSharedPreferences for the optional HF token. |
| Malicious model download URL | `ModelSourcePolicy.validate()` enforces HTTPS + host allowlist (`huggingface.co`, `cdn-lfs.huggingface.co`, `github.com`, `objects.githubusercontent.com`) and validates SHA-256 metadata format. |
| Tampered model file | Optional per-card SHA-256; download is deleted and the install fails on mismatch. |
| Prompt injection from web-fetch tool output | `GuardrailProcessor` runs on user input; observation truncation in ReAct (`MAX_TOOL_OUTPUT_LENGTH = 2000`). Per-tool sanitization is a known gap. |
| Cloud data exfiltration | No SDK in the project makes a cloud call. Outbound is HF (model downloads only), GitHub releases (model downloads only), Tailscale-internal IPs, and the user's own desktop. |
| Backup-based extraction | `android:allowBackup="false"` in the manifest. |

---

## 7. Concurrency

- All inference, IO, and DB work runs on `@IoDispatcher` (Dispatchers.IO).
- The download manager owns its own `CoroutineScope(SupervisorJob() + ioDispatcher)`
  so callers can fire-and-forget multi-GB transfers.
- ViewModels expose `StateFlow`s collected with `collectAsStateWithLifecycle`.
- The ReAct loop is a single cold `Flow` per turn — back-pressure is
  irrelevant; the model produces tokens slower than any consumer.

---

## 8. Background work

| Job | Trigger | Scheduler |
|---|---|---|
| `EvolutionWorker` | Idle + battery-not-low | WorkManager periodic (6 h) |
| `EvolutionService` | User opts into always-on | Foreground service |
| `ModelDownloadManager` resume | App start | Manual call from app init |

---

## 9. Open architectural debts

These are the architectural choices we know are wrong and intend to fix.
Logged here so they're not silently inherited by future contributors.

1. **TailscaleMeshClient on Android.** Queries a desktop-only local API.
   Either drop auto-discovery and use manual IP entry exclusively, or
   integrate the Tailscale IPN binder API. Manual entry already works.
2. **Keyword intent routing.** `InferenceOrchestrator.inferRole()` matches
   substrings like `"code"`, `"poem"`. Should be embeddings + nearest-role
   classifier using the already-shipped `EmbeddingEngine`.
3. **ReAct over models that natively support tool calls.** For Qwen3 / Gemma
   3 the JSON-wrapping prompt is a tax on TTFT. Detect the model's
   native tool-call format and bypass the wrapper when possible.
4. **Streaming over MCP.** The desktop offload returns one big response.
   Move to SSE or `Streamable HTTP` so the UI can render tokens as they
   arrive on offload too.
5. **Soul / LoRA wiring.** The persona sliders feed nothing today. Either
   wire them into the system prompt (cheap) or do real runtime LoRA merge
   (hard) — but do one of them, don't keep them as decoration.
6. **Vision pipeline.** `VisionWorker` exists in isolation; no UI, no model.
   Pick a VLM with a maintained GGUF or LiteRT build and wire end-to-end.
7. **Hindsight retrieval.** Flat cosine over all memories. Will not scale
   past a few thousand entries; needs time-decay and possibly an episodic
   / semantic split.
8. **`ModelHubDatabase` migrations.** `fallbackToDestructiveMigration` is a
   data-loss bug waiting to happen on first schema change.
9. **DownloadQueueEntity loses ModelCard fields.** Non-curated (HF-discovered)
   models can't be auto-resumed after a process kill because we only persist
   URL + size. Add a JSON blob column.

---

## 10. Where to make changes

| You're changing… | …work in |
|---|---|
| A new screen | `app/src/main/kotlin/dev/mias/app/ui/<feature>/` |
| A new agent tool | `core/agent/src/main/kotlin/dev/mias/core/agent/capabilities/` and register in `AgentModule` |
| Model selection logic | `core/inference/InferenceOrchestrator` and possibly `TawsGovernor` |
| Desktop offload behaviour | `core/network/mcp/McpClient.kt` + `desktop/server.py` (both sides) |
| What models appear in Brain Market | `core/model-hub/registry/CuratedModelRegistry.kt` (see header comment for rules) |
| Persona / tone | `core/soul/` (note: today only feeds the UI) |
| Thermal behaviour | `core/thermal/TawsGovernor.kt` |
| Background self-improvement | `core/evolution/EvolutionEngine.kt` |
