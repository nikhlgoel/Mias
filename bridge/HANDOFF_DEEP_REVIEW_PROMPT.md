# Session Handoff — Deep Review Pass (Layer 2: bugs + wiring hardening)

> Paste this into a fresh session opened at the Mias repo root, with codebase
> access. Goal: a NEW, adversarial review layer over the "Mias Bridge" design to
> make the connections and end-to-end working **more solid** — find bugs, gaps,
> inconsistencies, and especially **wiring problems** (how components connect,
> import, and hand off across the whole flow), then fix them **in place**.

---

## 0. What you are reviewing (context)

**Mias** is a privacy-first, fully on-device AI assistant. Today it's
Kotlin/Jetpack Compose on Android; it will be migrated to **React Native**
(Android first, then iOS). The new workstream is **Mias Bridge**: the "PC part"
— remote PC offload + control from the phone via an **IDE extension** (VS Code
first; a native Mias IDE later), working **over any network and distance,
smoothly, end-to-end encrypted**. The phone keeps work running on a home PC,
pushes new instructions, and sees live updates from anywhere.

The architecture + plan already exist as docs and a runnable PoC (see section 2).
**This was produced in one prior research session and has had three internal
review passes already** (threat log IDs S1–S25, Q1–Q4, M1–M13, U1). Your job is
the next, deeper, independent pass — assume the prior author missed things
(earlier passes found major issues like relay-routing-on-the-secret and remote
RCE sitting in the design, so more likely remain).

## 1. Locked decisions (don't relitigate without a concrete reason)

- **Outbound-from-PC + zero-knowledge rendezvous relay** is the default transport
  (like VS Code Tunnels / Cloudflare Tunnel); the PC dials out, the phone dials
  out, the relay only forwards ciphertext. P2P (WebRTC) and Tailscale are optional
  upgrades carrying the same protocol.
- **Quality is decoupled from the network:** the model runs + persists on the PC,
  so the link only affects when/how-smoothly output is seen, never its content.
- **Pairing:** balanced PAKE (CPace/SPAKE2) seeded by a short one-shot code;
  high-entropy `rendezvous_id` is the relay routing key and is separate from the
  code; **PC displays the QR, phone scans it**; saved devices reconnect via pinned
  keys + rotating derived rendezvous id.
- **Delivery:** global per-session sequence + cumulative ack + bounded resend
  buffer; exactly-once, in-order, resumable across reconnects/transport switches.
- **Mobile build strategy:** wrap the working Kotlin `core/*` as native modules,
  rewrite only the UI in RN/TS, behind a `BridgeTransport` abstraction.

You MAY challenge any of these, but only with a specific, justified flaw — and if
you do, update the docs to reflect the new decision + rationale.

## 2. Read these first (the artifacts), in order

- `bridge/README.md` — index + one-paragraph decision.
- `bridge/docs/00-OVERVIEW.md` … `11-MOBILE-APP-GAPS.md` — the full design:
  00 overview · 01 research/options · 02 architecture (ADR) + remote-auth/sandbox
  · 03 pairing & security · 04 protocol & live updates · 05 RN migration impact ·
  06 phased roadmap · **07 threat-review-and-fixes (the running flaw log)** ·
  08 resilience & quality (+ call graph) · 09 PC extension capabilities ·
  10 UX/UI · 11 mobile-app gaps vs the current Kotlin app.
- `bridge/poc/relay_poc.py` + `bridge/poc/RESULTS.md` — the connectivity PoC
  (relay + PC host + mobile; E2EE; streaming; multi-flap zero-loss resume).
- `docs/RN_MIGRATION_PROMPT.md` — the Kotlin→RN migration brief.
- Ground every claim against the real code: `app/`, `core/inference`,
  `core/network` (`mcp/McpClient.kt`, `mesh/`), `core/data`, `core/model-hub`,
  `core/resilience`, `core/security`, `desktop/server.py`. (Note `core/neural` and
  `core/neurocore` are NOT in `settings.gradle.kts` — out of scope.)

## 3. Open decisions still pending (resolve or flag, don't assume)

1. Opt-in PC-side **cloud API** passthrough: keep it (fenced) or drop it for strict-local?
2. **Transport scope for v1:** relay-only first, or include WebRTC P2P + STUN/TURN ops now?
3. **Relay hosting default:** ship a self-host image, run a default blind relay, or both?
4. **Memory source-of-truth:** PC owns project memory / phone owns personal — confirm.
5. **Push without Google:** foreground-service + UnifiedPush/ntfy on Android; APNs (content-free) on iOS — confirm.

## 4. YOUR TASK — deep layer-2 review (bugs + wiring)

Do an independent, adversarial pass. Emphasize **wiring** — the seams where
things connect are where this gets fragile. Apply every lens below:

1. **End-to-end flow tracing.** Walk a full turn (phone sends prompt → relay → PC
   session server → inference worker/skill → streamed tokens + live events →
   phone render), AND the failure flows (drop mid-stream, transport switch,
   app backgrounded, PC busy, code expired, relay down). At every hop verify the
   interface contract, ownership, and error path. Find places where a state or
   message can be lost, duplicated, reordered, deadlocked, or mis-routed.
2. **Wiring / interfaces / import graph.** Check the module + function call graph
   in `08` for: cycles, missing back-pressure, unclear ownership, leaky
   abstractions, who-creates/who-closes a transport, how `MCP` messages map onto
   Bridge frames, how capability scopes are actually enforced at the call site,
   how the RN `BridgeTransport` boundary stays clean. Make the contracts explicit
   and consistent across docs.
3. **Crypto & pairing correctness.** PAKE choice/transcript/channel-binding, key
   confirmation, forward secrecy (ECDH), AEAD nonce strategy + rekey, key
   storage, code TTL/burn/rate-limit, QR/link validation, mutual approval, replay.
4. **Resilience & state machine.** The CONNECTED/DEGRADED/SUSPENDED/RECONNECTING/
   RESUMED machine: every transition, timer, race (make-before-break overlap,
   reconnect storms, half-open detection), buffer overflow → generation
   backpressure, detach TTL, offline outbox durability + idempotency.
5. **Protocol soundness.** Frame schema, global seq vs per-stream cursors, ack
   semantics, resume math (off-by-one!), idempotency keys on side-effecting rpc,
   versioning/negotiation, head-of-line handling, compression, chunking.
6. **Concurrency.** Coroutine/async ownership, cancellation (note: prior PoC bug —
   `CancelledError` is not `Exception`), double-send on resume, shared mutable
   state across reconnects.
7. **Security beyond crypto.** Remote-tool RCE scoping/path-jail/approval,
   skill sandboxing, relay abuse/DoS/rate-limits, metadata leakage, cloud-API
   privacy fence, allowlist (exactly one relay host), fail-closed everywhere.
8. **Codebase reality mapping.** Re-verify `11`'s gap claims against current code
   (e.g., `McpClient.generate()` is single-shot; `isDesktopReachable` is
   "configured" not healthy; `fallbackToDestructiveMigration(dropAllTables=true)`;
   no QR scanner). Find gaps `11` missed. Confirm what's reusable
   (`core/resilience/OperationQueue`, foreground-service + notification perms).
9. **Cross-doc consistency.** No contradictions between docs; every "section N"
   reference and every `[link](...)` resolves; terms used consistently.
10. **UX-flow integrity.** Does the UX (10) actually cover every state the protocol
    can be in (degraded, offline-queued, reconnecting, approval-needed, scope
    escalation, error)? Any state with no screen is a bug.

## 5. Method

1. **Read** all artifacts (section 2); build a written mental model of the flow.
2. **Hunt** adversarially with the lenses in section 4. For each finding record:
   flaw → concrete risk → fix → exact file/section to change.
3. **Validate empirically** where feasible: extend `bridge/poc/relay_poc.py`
   (e.g., transport-switch dedup, reconnect-storm backoff, buffer-overflow
   backpressure, idempotent-rpc-on-resume) and capture numbers in `RESULTS.md`.
4. **Fix in place** — edit the existing files; fold corrections into the relevant
   sections so the spec stays authoritative.
5. **Log** every finding in `bridge/docs/07-THREAT-REVIEW-AND-FIXES.md` under a new
   dated "Fourth pass" heading, continuing the ID scheme: security **S26+**,
   product/privacy **Q5+**, mobile **M14+**, UX **U2+**. Each entry:
   `[BLOCKER]/[HARDEN]` + flaw → fix → "Applied: <files/sections>".

## 6. Rules of engagement (important)

- **Update files in place; never create duplicates.** New file only for genuinely
  new content. Each doc keeps its one canonical path.
- **Keep cross-references + markdown links valid** after edits (verify at the end).
- Use the word "section" (not the `§` glyph) in references — match existing style.
- If you run the PoC in a sandbox: use a **fresh/random port per run**, add a hard
  timeout, and **do NOT `pkill -f python3`** (it can kill the host harness).
- Stay within the privacy invariant: no new cloud dependencies; outbound allowlist
  gains at most the one relay host; E2EE + fail-closed always.
- Be concise and high-density in your own output; put depth in the docs.

## 7. Deliverables

1. Edited docs with fixes folded in (in place).
2. New "Fourth pass" entries in `07` (S26+/Q5+/M14+/U2+) mapping flaw→fix→file.
3. Extended PoC + refreshed `RESULTS.md` for any newly validated behavior.
4. A prioritized **wiring-fix task list** (the concrete connect/contract changes
   needed to make the system solid), added to `06`'s phases or a short new section.
5. A brief end-of-pass report: top findings by severity + what changed.

## 8. Definition of done

The design is internally consistent, every end-to-end and failure flow has a
defined, race-free, fail-closed path with clear component ownership; all new
findings are fixed-in-place and logged; links/section refs resolve; and the PoC
still passes (clean + multi-flap) with any new resilience checks green.
