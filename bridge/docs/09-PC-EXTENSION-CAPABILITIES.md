# 09 — PC Extension Capabilities

The IDE extension's session server is not just a relay endpoint — it's the **power
worker**: where the big models, the project context, the skills, and the long-term
memory live. The phone is the remote control + viewport; the PC does the heavy work.

## 1. AI access on the PC (models *and* APIs)

A single **InferenceWorkerClient → ProviderAdapter** abstraction so the PC can use
whatever the user has, selected per role (chat / code / reasoning / embedding /
vision):

| Provider | Notes |
|---|---|
| **llama.cpp server** (today's `desktop/server.py`) | Local GGUF; extend to streaming (Streamable HTTP/SSE) |
| **Ollama** | Popular local runner; OpenAI-compatible + native API |
| **vLLM / TGI** | High-throughput local serving for capable GPUs |
| **LM Studio / Jan** | Local OpenAI-compatible endpoints |
| **Any OpenAI-compatible local URL** | Generic adapter |
| **Cloud API passthrough (opt-in)** | OpenAI/Anthropic/OpenRouter — **breaks the no-cloud default** |

**Cloud passthrough is a privacy boundary (recheck flaw Q1):** it is **off by
default**, gated behind an explicit, clearly-labeled toggle in the extension; the
API key is stored **encrypted on the PC only, never synced to the phone**; the app
must visibly indicate "this turn used <cloud provider>"; the privacy copy and host
allowlist must reflect the chosen provider host. Default product stance stays
fully local; the option exists because you asked for it, fenced so it can't leak
silently.

**Provider selection & routing:** role→provider/model map; auto-pick by task
(reuse the `RoleClassifier` concept), **VRAM/RAM-aware** load, **thermal-aware**
throttle (reuse `core/thermal`), graceful fallback if a provider is down.

## 2. Model management (on the PC, drivable from the phone)

- Browse/search (curated + HuggingFace), **download with pause/resume + SHA-256
  verify** (reuse `core/model-hub` logic, server-side), quantization picker.
- "**Download this on my PC**" initiated from the phone's Brain Market; progress
  streams back as `live` events; respects **metered-network** rules on the phone
  side (don't trigger huge pulls over cellular without consent).
- Multi-model registry, hot-swap, per-role assignment, disk/VRAM budget view.
- Integrity + source allowlist enforced (reuse `ModelSourcePolicy`).

## 3. Skills engine (deep, real skills)

A first-class skill system on the PC, mirroring how capable assistants ship skills:

- **Skill = manifest + handler.** Manifest declares `name`, `description`,
  `triggers`, `inputs`, and a **required capability scope** (read/edit/exec, 02 section 8)
  + host allowlist. The handler runs on the PC with project context.
- **Registry + discovery:** the phone lists available skills and invokes them;
  results/streams come back as normal `stream`/`live` frames.
- **Permissions:** every skill runs under the session's **server-enforced** scope
  (never the scope the phone claims — 07-S27); `exec`/destructive skills require an
  explicit human approval that **works with nobody at the PC** (per-op biometric
  confirm on the phone + per-project allowlist, 07-S26), not just an IDE prompt;
  all file access is path-jailed; web access is allowlisted. `GuardrailProcessor`
  scans free-text inputs/outputs as an **NL content heuristic — it is not a command
  or path sanitizer** (07-S29), so it never substitutes for the jail/scope/approval.
- **Concrete starter skills** (project-aware, because the PC has the repo open):
  explain-codebase, code-review, run-tests, debug, refactor, generate-docs,
  write-commit/PR, deploy-checklist, search-code, run-build, summarize-doc,
  web-research (allowlisted), file-organize. (Several mirror engineering skills
  already proven useful.)
- **User/custom skills:** drop a skill folder in the project (`.mias/skills/`) or a
  global dir; hot-loaded. Versioned, sandboxed.

## 4. Instructions, context & personas

- **Project instructions:** a repo file (e.g., `.mias/instructions.md`, repo-instructions-
  style) the session server loads as system context; plus a **global** instruction
  set. Editable from phone or extension.
- **Project context indexing:** the PC builds a code/doc index (RAG) so answers are
  grounded in the actual project (reuse the on-device RAG work already in the app).
- **Personas / tone:** reuse `core/soul`; per-project or per-session persona;
  surfaces as a quick picker on the phone.

## 5. Memory notes & self-learning (Hindsight on the PC)

The PC has the compute and storage to host the serious memory layer:

- **Long-term memory store:** embeddings-indexed notes/facts/preferences, scoped
  **per project** and **global**; reuse the `Hindsight` concept with proper
  **ANN indexing (HNSW)** and **time-decay + episodic/semantic split** (fixes the
  flat-cosine scaling debt noted in the app's own DESIGN.md).
- **Self-learning / evolution:** the `core/evolution` daemon's job, run on the PC
  when idle — consolidate sessions, dedup by cosine, extract reusable
  patterns/snippets, grow the project knowledge base. This is the natural home for
  the otherwise-unwired `core/neural` `GrowthEngine` and the **planned LoRA
  fine-tuning pipeline** (train adapters where the GPU is), feeding learned
  preferences back into prompts/personas.
- **Explicit memory notes:** the user can tell the assistant "remember X for this
  project"; stored, retrievable, editable, and **viewable/redactable from the
  phone** (transparency).
- **Sync & conflict (recheck flaw Q2/Q6):** confirmed source-of-truth — project
  memory = **PC**, personal/device memory = **phone**. Resolve concurrent edits with
  a **per-field logical clock (Lamport counter / version vector), not wall-clock
  last-writer-wins** — device clocks skew (07-S20), so timestamp LWW can silently
  drop the wrong edit; a visible conflict resolver handles the rare true clash.
  Memory is **encrypted at rest** on the PC and never leaves without E2EE.

## 6. Live project signals (push to phone)

File watcher → `file_event`; build/test runner → `build_event` (pass/fail + tail);
long-job `progress`; PC `status` (CPU/GPU/VRAM/temp, model loaded, queue depth);
`notify` (job done / needs input / build failed) → mobile push even when
backgrounded (content-free wake, detail pulled after reconnect; 04 section 4).

## 7. Feature catalog (major + mini)

**Major:** remote chat/offload; project-aware skills; run build/tests/terminal
(scoped); file read/patch; multi-model + multi-provider AI; model download on PC;
long-term memory + self-learning; live build/file/job updates; push notifications;
saved devices + one-tap reconnect; offline instruction queue; session history &
resume-days-later; multi-project switching; multi-device management + revoke.

**Mini (small things that make it feel great):** connection-quality + latency
chip; current-transport badge (P2P/relay/Tailscale); PC vitals widget;
one-tap model switch; pause/resume/cancel job; copy/share output & code blocks;
command palette on phone; "wake/start session on PC boot"; QR re-pair; usage/cost
meter (if cloud used); per-project default persona/model; "continue this on my
phone locally" handoff when offline; redact-a-memory; export session transcript;
quiet-hours for notifications; bandwidth guard prompt before large transfers;
"why did it pick this model" explainer.

## 8. Where this reuses the existing codebase
`core/model-hub` (downloads/registry/policy), `core/inference` (engines/
orchestrator/ReAct), `core/agent` (tools→skills), `core/data`+Hindsight (memory),
`core/evolution` (self-learning), `core/soul` (personas), `core/thermal` (health),
`desktop/server.py` (worker). The Bridge mostly **exposes existing capability
remotely + adds the skill/memory/provider depth**, rather than inventing from zero.
