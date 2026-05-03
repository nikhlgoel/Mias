# Contributing to Mias

Mias is open source. Contributions are welcome — but they need to be focused and clean.

There are exactly **two valid contribution types**.

---

## Type 1 — Bug Fix

Found something broken? Fix it.

**Rules:**
- One commit per bug
- Commit message format: `fix: <what broke> — <what you changed>`
- Include what the bug was, what caused it, and what your fix does
- Keep the diff surgical — don't reformat unrelated code

**Example commit message:**
```
fix: ReActEngine infinite loop on unknown tool name — added ToolRegistry validation guard
```

---

## Type 2 — Feature Improvement

Pick **one single feature** and make it meaningfully better.

**Rules:**
- Open an issue first describing what you want to improve and why
- One PR per feature area — don't bundle multiple improvements
- The PR description must explain: what you improved, how, and what tradeoffs you made
- No half-finished work. If it's not in a working state, don't submit it

**Example:**
```
improvement: VoiceChatScreen — added tap-to-toggle vs hold-to-speak mode selection
```

---

## What We Do Not Accept

- Refactors that don't fix anything
- Cosmetic changes (formatting, whitespace)  
- Adding dependencies without strong justification
- Anything that increases APK size without clear user-facing value
- Generated code dumps

---

## Language Scope

Mias intentionally uses multiple languages, each chosen for what it's best at:

| Language | Where | Why |
|---|---|---|
| Kotlin | Android UI, ViewModels, modules | Native Android, Compose |
| C++ | JNI bridge, llama.cpp inference kernel | Performance, direct memory |
| Rust | Desktop inference server (planned) | Memory safety, speed, no GC |
| Python | Model scripts, benchmarks, data prep | ML ecosystem |
| Go | MCP bridge server (planned) | Fast, small binary, great networking |
| ARM64 Assembly | SIMD matrix ops in inference path | Raw CPU throughput |
| Swift | iOS port (future) | Native iOS |
| TypeScript | Web companion (future) | Browser ecosystem |

If you want to contribute to a language area that isn't Kotlin, make sure you're doing it in the right module and with the right build system integration.

---

## Release Tagging

Releases are tagged `#001`, `#002`, etc. — not semantic version strings. Each tag represents a meaningful working state of the project.

The first release (#001, internal codename "Kid") established the base architecture. Tags are created by the maintainer.

---

## Setup

See [QUICK_START.md](QUICK_START.md) to get the project building locally.
