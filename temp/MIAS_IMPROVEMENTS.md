# Mias — Professional Project Improvements

This document covers what the project needs to be taken seriously as an open source project, beyond the code itself.

---

## 1. What's Genuinely Strong (Don't Break It)

- **Multi-module Gradle architecture** — correct for this scale, DI is set up right
- **Thermal governor** — this is actually sophisticated, most hobby projects skip it
- **HindsightMemory concept** — long-term compressed memory is differentiated
- **ReAct agent loop** — the structure is correct even if guards are missing
- **SQLCipher + biometrics** — real security primitives, not a fake lock screen

Keep these exactly as they are. Build on them.

---

## 2. Repository Hygiene

**Status: Needs work**

### What needs to happen

**README.md** — currently clean and honest. Keep it short. Do NOT add badges, shields.io counters, or "built with" grids. They look desperate. The current README earns trust by being straightforward.

**What to add to README:**
- Hardware tested on (which Android device, which model ran successfully)
- Honest "what doesn't work yet" section — it already partially exists, make it explicit
- Link to `docs/PROGRESS.md` for detailed status

**Docs folder:**
- `PROGRESS.md` — ✓ created
- `LANGUAGE_ARCHITECTURE.md` — ✓ created
- `CONTRIBUTING.md` — ✓ created
- Remove or gitignore: `IMPLEMENTATION_BLUEPRINT.py`, `PRD.docx`, `DOC#001.docx` — these reveal AI-assisted planning and add no value publicly

**`.github/` folder:**
- `.gitignore` was ignoring `.github/` — fixed. Now add actual GitHub content:
  - `ISSUE_TEMPLATE/bug_report.md` — structured bug report template
  - `ISSUE_TEMPLATE/feature_improvement.md` — improvement request template  
  - `pull_request_template.md` — ensures PRs have proper descriptions

---

## 3. GitHub Issue Templates (create these)

### `.github/ISSUE_TEMPLATE/bug_report.md`
```markdown
---
name: Bug Report
about: Something is broken
---
**Module affected:** (e.g., core/inference, ReActEngine)
**What happens:** 
**What should happen:**
**Steps to reproduce:**
**Device + Android version:**
**Model being used:**
```

### `.github/ISSUE_TEMPLATE/feature_improvement.md`
```markdown
---
name: Feature Improvement
about: Make one thing better
---
**Feature being improved:** (one specific component)
**Current behavior:**
**Proposed improvement:**
**Why this is better:**
```

### `.github/pull_request_template.md`
```markdown
## Type
- [ ] Bug fix
- [ ] Feature improvement

## What changed
## Why
## How to test
## Tradeoffs/limitations
```

---

## 4. Technical Debt Priority Map

| Item | What breaks without it | Effort | Do when |
|---|---|---|---|
| VoiceChatScreen | Voice button does nothing | 1 day | Now |
| ReActEngine max-step guard | Agent loops forever on bad tool calls | 2 hours | Now |
| ReActEngine tool validation | Agent hallucinates tool names, crashes | 2 hours | Now |
| Google AI Edge SDK (NPU) | NPU claim in UI is false | 2-3 days | Sprint 2 |
| Tailscale dependency check | Silent failure if app not installed | 1 hour | Sprint 2 |
| MCP spec compliance | MCP tools don't actually work per spec | 2 days | Sprint 3 |
| HindsightMemory dedup | Same facts written 10× over time | 2 hours | Sprint 3 |
| Semantic intent router | Wrong model selected for task type | 1 day | Sprint 3 |
| LoRA runtime merge | Soul sliders do nothing at runtime | 1 week | Later |
| VisionChatScreen | Camera button does nothing | 1 day | Later |

---

## 5. Cross-Platform Expansion Plan

Package `io.mias.app` was chosen for cross-platform reasons. Here's the realistic path:

### Phase 1 — Android Stable (current)
Get all working modules actually working. VoiceChatScreen, NPU, MCP compliance.

### Phase 2 — Desktop (6-12 months)
The Python server prototype becomes a proper desktop app.
- Rust backend: `desktop/mias-server/` — Axum + llama.cpp FFI
- Electron/Tauri frontend OR just a CLI — **[DECISION]** depends on user need
- Same Tailscale mesh architecture already in place

### Phase 3 — iOS (12-18 months)  
llama.cpp already supports iOS via Metal. The main work is SwiftUI port.
- Shared inference library (C++ static lib, same CMakeLists.txt approach)
- SwiftUI screens (no Compose on iOS)
- No cross-platform UI framework — native is the right call here

### Phase 4 — Web Companion
TypeScript/React dashboard for managing models, viewing memory state, and remote control via Tailscale. This is the "command your PC from your phone" use case expanded.

---

## 6. What "Multi-Language" Actually Means in Practice

The user's instinct to use multiple languages is correct. But assembly language and exotic choices need grounding:

**Use these now:**
- Kotlin — Android, no change
- C++ — inference kernel, existing
- Python — desktop prototype, existing

**Add next:**
- Rust — when replacing Python desktop server. Start with `cargo new mias-server`. The inference path is `axum → tokio → llama-cpp-rs`. This is maybe 3-4 weeks of focused work.
- Go — MCP bridge replacement. `go new mcp-bridge`. The payoff is a single binary that handles concurrent connections without virtualenv.

**Add later (with purpose):**
- ARM64 Assembly — only for specific NEON SIMD inner loops inside `CMakeLists.txt`. Not general-purpose assembly. Needs benchmarks to justify.
- Swift — iOS port only
- TypeScript — web dashboard only

**Do not add just to have the language.** Every language in the repo adds maintenance surface. The test for adding one: "does this language do something here that an existing language in the project cannot?"

---

## 7. Release Process

Current setup: APK released directly via GitHub Releases. This is correct.

### Tag format: `#001`, `#002`, `#003`
Each tag represents a meaningful working state. Not semantic versioning.

### GitHub Release checklist (for each release):
1. APK builds without errors: `./gradlew assembleRelease`
2. Core functionality tested on physical device (not emulator)
3. `docs/PROGRESS.md` updated with what's new and what's still broken
4. Git tag created: `git tag #002 -m "Release #002 — VoiceChatScreen + ReAct guards"`
5. APK attached to GitHub Release draft
6. Release notes: what works, what changed, minimum Android version

### What not to do:
- Don't claim features work if you haven't tested them on a real device
- Don't write changelog in marketing language — just state what changed

---

## 8. Testing Reality

Current claimed: 87 tests. Actual: ~8 test files, likely mostly stubs.

**Don't inflate this number.** Tests are worth writing for:
- `ReActEngine` — unit test the max-step guard and tool validation
- `HindsightMemory` — test dedup logic with mock embeddings  
- `InferenceOrchestrator` — test routing logic (which engine for which input)
- `RegexIntentExtractor` — already has a test file, extend it

Skip UI tests for now. Compose UI tests are slow, brittle, and not worth the investment at this stage.

---

## 9. Performance Baselines (establish these)

Before claiming "runs on your phone", document what actually runs:

| Model | Device | RAM | Tokens/sec | Notes |
|---|---|---|---|---|
| (fill in) | (fill in) | (fill in) | (fill in) | (fill in) |

Add this table to README. Real numbers, even if they're slow, are more credible than vague capability claims.

---

## 10. The One Thing That Matters Most

Right now the most important thing is: **VoiceChatScreen.**

This is a voice-first AI app. The voice button leads nowhere. Everything else in the roadmap is less important than making the primary interaction mode actually work.

Do Session 2 from the agent prompt first.
