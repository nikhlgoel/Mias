# Mias — Architecture Overview

## What is V4?

- **V1** = Chat (text in, text out)
- **V2** = Generative AI (creates content)
- **V3** = AI Agent (takes actions via tools)
- **current release (Release #001) — an AI that:
  - **Chooses its own brain** (downloads & switches models for different roles)
  - **Evolves itself** (learns from every interaction, improves in background)
  - **Survives anything** (network loss, crashes, power cuts — auto-resumes)
  - **Acts autonomously** (file ops, web scraping, scheduling, code execution)
  - **Has a living UI** (every pixel responds to its cognitive state)

## New Modules

### core:model-hub — AI Model Marketplace
- Browse available models from HuggingFace (GGUF/ONNX format)
- Download models with progress tracking & resume support
- Manage installed models (delete, update, set roles)
- Auto-select best model per task based on hardware capability
- Model roles: CHAT, CODE, RESEARCH, CREATIVE, SURVIVAL

### core:agent — Autonomous Agent Framework
- File system operations (create, read, edit, delete files)
- Web content capture (fetch pages, extract data, summarize)
- Clipboard monitoring and action
- Notification interception and smart routing
- App launching and inter-app communication
- Task queue with priority and dependency tracking

### core:evolution — Self-Improvement Engine
- Background learning from conversation history
- Instruction following improvement via reinforcement
- Pattern recognition across user interactions
- Automatic Hindsight Memory consolidation
- Performance metrics tracking and optimization

### core:resilience — Fault Tolerance Layer
- Internet connectivity monitoring with smart retry
- Operation queue that survives app kills
- Checkpoint-based conversation recovery
- Graceful degradation across all subsystems
- Auto-resume for interrupted model downloads & operations

---

## Module Dependency Graph

```
:app
 ├── :core:common          (KidResult, CognitionState, BrainState, DI dispatchers)
 ├── :core:data            (Room DB, ConversationRepository, HindsightMemory)
 ├── :core:inference       (ONNX/MediaPipe runtime, InferenceEngine)
 ├── :core:network         (Ktor client, Tailscale P2P discovery)
 ├── :core:thermal         (CPU/GPU thermal monitoring, throttle decisions)
 ├── :core:soul            (SoulEngine, personality traits, SomaticState)
 ├── :core:security        (biometric auth, encrypted storage)
 ├── :core:ui              (Compose components, KidTheme, KidMotion)
 ├── :core:model-hub       (ModelManager, ModelDownloadManager, CuratedModelRegistry)
 ├── :core:agent           (AgentOrchestrator, 7 capabilities, tool multibinding)
 ├── :core:evolution       (EvolutionEngine, EvolutionWorker, EvolutionService)
 └── :core:resilience      (ConnectivityMonitor, CheckpointManager, RetryExecutor)
```

---

## Key Data Flows

### Inference Request
```
User Input → ChatViewModel → InferenceEngine
  → SoulEngine (injects personality)
  → ModelManager (picks model for role)
  → AgentOrchestrator (if ReAct tool use needed)
    → Capability.execute() (file/web/calc/etc)
  → HindsightMemory (stores output)
  → ChatViewModel (updates UI state)
```

### Model Download
```
User taps "Download" in Brain Market
  → ModelHubViewModel.downloadModel(card)
  → ModelManager.installModel(card)
  → ModelDownloadManager.startDownload(card)
    → Ktor streaming download with SHA-256 verification
  → modelDao.upsert(entity) on completion
  → StateFlow<Map<String, DownloadState>> → UI updates
```

### Background Evolution
```
EvolutionWorker (WorkManager, every 6h, on charging + battery-not-low)
  OR
EvolutionService (ForegroundService, always-on mode)
  → EvolutionEngine.runFullCycle()
    → KnowledgeConsolidator → HindsightMemory tiering
    → ConversationAnalyzer → BehaviorPattern detection
    → SelfOptimizer → SoulEngine trait weight adjustment
```

---

## Navigation Routes

| Route | Screen | Description |
|---|---|---|
| `splash` | SplashScreen | Animated Neural Eye orb, phase-based |
| `home` | HomeScreen | Central orb, nudges, V4 nav icons |
| `chat` | ChatScreen | Full-screen conversation with ReAct |
| `settings` | SettingsScreen | Privacy, persona, inference config |
| `modelhub` | ModelHubScreen | Browse/download/manage AI brains |
| `agent` | AgentScreen | Live agent feed, manual tool execution |
| `evolution` | EvolutionScreen | Self-improvement toggle + history |

---

## Agent Capabilities (V4.0)

| Tool Name | Capability | Description |
|---|---|---|
| `calculator` | CalculatorCapability | Math expression evaluator |
| `clipboard` | ClipboardCapability | Read/write Android clipboard |
| `datetime` | DateTimeCapability | Current time, timezone, date arithmetic |
| `filesystem` | FileSystemCapability | Read/write files in app sandbox |
| `web_fetch` | WebFetchCapability | Fetch raw URL content |
| `web_research` | WebResearchCapability | Deep HTML extraction, focus filtering |
| `app_launch` | AppLaunchCapability | Launch URLs and deep links |

---

## Evolution Pipeline (V4.0)

### Task Types (EvolutionTaskType)
| Type | Description | Interval |
|---|---|---|
| CONSOLIDATE_MEMORIES | Tier 1→2→3 HindsightMemory promotion | 6h |
| ANALYZE_PATTERNS | Detect recurring topics/emotional arcs | 12h |
| OPTIMIZE_PERSONALITY | Adjust SoulEngine trait weights | 12h |
| PRUNE_OLD_FACTS | Remove stale memory to stay lean | 7d |
| REFLECT_ON_ERRORS | Review low-confidence responses | 6h |
| UPDATE_MODEL_PREFERENCE | Re-evaluate model performance per role | 12h |

---

## Zero-Cloud Guarantee

All inference is local. All data stays on device.
The only external call allowed is `model download` over HTTPS (HuggingFace CDN).
All runtime AI inference uses local ONNX/GGUF models via ONNX Runtime Mobile or llama.cpp.
No telemetry. No crash reporting. No analytics. No cloud auth. No Firebase.

Network access is ONLY used for:
1. Model downloads (one-time, user-initiated, SHA-256 verified)
2. Web research via AgentCapability (user-triggered tool calls)
3. Tailscale P2P mesh (local LAN inference from desktop)

---

## Data Isolation And Manual Consent

- All fed files and local memory live in app-private storage (`/data/data/dev.kid.app/...`) and are not readable by other apps.
- Android backup and transfer channels are blocked in manifest and XML rules:
  1. `allowBackup=false`
  2. `fullBackupContent=@xml/backup_rules`
  3. `dataExtractionRules=@xml/data_extraction_rules`
- Any future data egress path (share/export/desktop transfer/backup) must pass through `ManualAccessConsentGate`.
- Consent model:
  1. Owner approves manually in UI (biometric + explicit confirm)
  2. App issues short-lived one-time token
  3. Export call consumes token for the exact operation
  4. Reuse, mismatch, or expired token is denied
