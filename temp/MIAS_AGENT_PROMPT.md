# Mias — Copilot Agent Prompt

Paste this entire block into GitHub Copilot Agent Mode (or your Claude Opus agent). It covers all changes from the latest review. Execute everything in sequence. No check-ins unless a decision is needed (those are marked **[DECISION]**).

---

## Context

Project: Mias — local Android AI assistant  
Root: `app/`, `core/`, `desktop/`, `scripts/`, `docs/`  
Package namespace: `dev.kid` (keep internally — cosmetic rename deferred)  
Application ID: already updated to `io.mias.app` in `app/build.gradle.kts`

---

## Session 1 — Branding + Cleanup (30 min)

These are already applied in the updated zip. Verify they're correct in your working tree, then move on.

**Verify these files have correct text:**

| File | String to confirm |
|---|---|
| `app/.../splash/SplashScreen.kt:252` | `text = "{Mias}"` |
| `app/.../splash/SplashScreen.kt:267` | `text = "Local AI. No cloud. Your data."` |
| `core/ui/.../components/KidInputBar.kt:43` | `placeholder: String = "Talk to Mias..."` |
| `app/.../evolution/EvolutionScreen.kt:168` | `"Mias evolves silently while idle — every 6 hours"` |
| `core/evolution/.../service/EvolutionService.kt:78` | `.setContentTitle("Mias")` |
| `core/inference/.../orchestrator/InferenceOrchestrator.kt:120` | `"You are Mias, a private AI assistant"` |
| `core/network/.../mcp/McpClient.kt:56` | `"Mias Android"` |
| `core/agent/.../capabilities/WebResearchCapability.kt:143` | `"Mias/1.0"` |
| `app/src/main/res/drawable/` | Files named `ic_mias_background.xml`, `ic_mias_foreground.xml` |

---

## Session 2 — VoiceChatScreen (highest priority, ~4 hours)

**The ViewModel exists. The screen does not. Build it.**

File to create: `app/src/main/kotlin/dev/kid/app/ui/voice/VoiceChatScreen.kt`

Requirements:
- Full-screen dark layout (background: `KidColors.Background`)
- Center: `AnimatedOrb` component — pulsing larger when `isListening = true`, color shift to brighter cyan when `isProcessing = true`
- Bottom third: live transcript text (scrollable, shows real-time STT output)
- Bottom: two states
  - **Idle/Processing**: single large circular mic button, tap to start listening
  - **Listening**: same button pulsing, tap again to stop
- Top: back arrow button (navigate up)
- Wire to `VoiceChatViewModel` — observe `uiState` flow, call `startListening()` / `stopListening()`
- No chat input bar, no keyboard — voice only

Add route to `KidNavHost.kt`:
```kotlin
composable(KidRoutes.VOICE) {
    VoiceChatScreen(onBack = { navController.navigateUp() })
}
```

Add `VOICE = "voice"` to `KidRoutes` object.

Wire the Voice button in `HomeScreen.kt` to navigate to `KidRoutes.VOICE`.

---

## Session 3 — ReActEngine Guards (critical stability, ~2 hours)

File: `core/inference/src/main/kotlin/dev/kid/core/inference/react/ReActEngine.kt`

**Add these three guards:**

### 3A. Max-step guard
```kotlin
private val MAX_STEPS = 7
```
At the top of the ReAct loop, add:
```kotlin
if (stepCount >= MAX_STEPS) {
    return ReActResult.Error("Max steps ($MAX_STEPS) reached. Task may be too complex.")
}
```

### 3B. Tool name validation
Before executing any tool call, validate it against `ToolRegistry`:
```kotlin
val tool = toolRegistry.findTool(toolName)
    ?: return ReActStep.Error("Unknown tool: '$toolName'. Available: ${toolRegistry.listTools()}")
```

### 3C. Tool output truncation
After receiving tool output, truncate if > 2000 chars:
```kotlin
val truncatedOutput = if (output.length > 2000) {
    output.take(2000) + "\n... [output truncated at 2000 chars]"
} else output
```

---

## Session 4 — Google AI Edge SDK (NPU path, ~3 hours)

**[DECISION]** This session makes the "runs on NPU" claim honest for Gemma models.

Add to `gradle/libs.versions.toml`:
```toml
mediapipe-genai = "0.10.22"
```

Add to `core/inference/build.gradle.kts`:
```kotlin
implementation(libs.mediapipe.genai)
```

Create new file: `core/inference/src/main/kotlin/dev/kid/core/inference/engine/GoogleAiEdgeEngine.kt`

Requirements:
- Implement `InferenceEngine` interface
- Use `com.google.mediapipe.tasks.genai.llminference.LlmInference`
- Constructor takes: `context: Context`, `modelPath: String`
- `generate(prompt, onToken)` — streams tokens via LlmInference's async callback
- `isAvailable()` — returns true only if model file exists at path

Update `InferenceOrchestrator.kt`:
- Add `GoogleAiEdgeEngine` as third engine option
- Priority: `GoogleAiEdgeEngine (Gemma) → LlamaCppEngine (Qwen/others) → DesktopEngine`
- Selection logic: if query doesn't contain code keywords AND Gemma model is available AND device has NPU flag → use GoogleAiEdgeEngine

---

## Session 5 — HindsightMemory Dedup (~2 hours)

File: `core/data/src/main/kotlin/dev/kid/core/data/hindsight/HindsightMemory.kt`

Before writing a new hindsight entry, check cosine similarity against recent entries:

```kotlin
private suspend fun isDuplicate(newEmbedding: FloatArray): Boolean {
    val recent = hindsightDao.getRecentEmbeddings(limit = 50)
    return recent.any { existing ->
        VectorUtils.cosineSimilarity(existing.embedding, newEmbedding) > 0.92f
    }
}
```

Add `getRecentEmbeddings(limit: Int)` to `HindsightDao` if missing.

Only write the entry if `!isDuplicate(embedding)`.

---

## Session 6 — Tailscale Dependency Check (~1 hour)

File: `core/network/src/main/kotlin/dev/kid/core/network/TailscaleMeshClient.kt`

Add check before attempting connection:
```kotlin
private fun isTailscaleInstalled(context: Context): Boolean {
    return try {
        context.packageManager.getPackageInfo("com.tailscale.ipn", 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}
```

If not installed, emit a `ConnectionState.Error("Tailscale not installed — desktop offload unavailable")` state.

Update `SettingsScreen.kt` / `HomeScreen.kt` to show this error in the SystemStatus component instead of silently failing.

---

## Session 7 — Desktop Server Hardening (~1 hour)

File: `desktop/server.py`

Add these if missing:

```python
@app.get("/health")
async def health():
    return {
        "status": "ready" if model_loaded else "loading",
        "model": model_name,
        "version": "release-001"
    }
```

Add a shared secret check for non-Tailscale environments:
```python
API_TOKEN = os.environ.get("MIAS_TOKEN", "")

def verify_token(request):
    if API_TOKEN and request.headers.get("X-Mias-Token") != API_TOKEN:
        raise HTTPException(status_code=401)
```

Add Docker healthcheck to `desktop/Dockerfile`:
```dockerfile
HEALTHCHECK --interval=30s --timeout=5s CMD curl -f http://localhost:8080/health || exit 1
```

---

## Session 8 — MCP Spec Compliance (~2 days)

File: `core/network/src/main/kotlin/dev/kid/core/network/mcp/McpClient.kt`

Full MCP 2024-11 initialization sequence:

```
Client → Server: initialize { protocolVersion, capabilities, clientInfo }
Server → Client: { result: { protocolVersion, capabilities, serverInfo } }
Client → Server: notifications/initialized
```

Then normal tool calls proceed.

Current implementation skips this handshake. Add:
1. `initialize()` suspend function that runs the handshake
2. `listTools()` — fetches available tools from server
3. `callTool(name, args)` — typed request/response
4. `McpCapability` sealed class for typed capability negotiation

See `core/network/src/main/kotlin/dev/kid/core/network/mcp/McpModels.kt` for existing model structures — extend those.

---

## Notes for Agent

- Don't change `dev.kid` package names or import paths. Application ID (`io.mias.app`) is separate from namespace.
- Don't rename class files (KidTheme, KidColors, etc.) — scheduled for a future major refactor when the codebase is stable.
- All new files: follow existing code style in the module they're added to.
- Tests: add a basic test to `src/test/` for any new logic you add (not UI).
