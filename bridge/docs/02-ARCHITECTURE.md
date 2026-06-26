# 02 — Architecture (ADR)

**Status:** Proposed · **Date:** 2026-06-26 · **Supersedes:** LAN-only manual-IP MCP offload

## 1. Decision

Adopt an **outbound-dial + zero-knowledge rendezvous relay** as the default
transport, carrying an **end-to-end-encrypted Bridge protocol** between a
PC-side **session server** (hosted by the IDE extension) and the **mobile
client**. Add an optional **P2P (WebRTC) upgrade** and an optional **Tailscale**
transport, both carrying the identical encrypted protocol. The existing
`desktop/server.py` MCP worker is reused locally behind the session server.

> **Security baseline:** all transports are `wss://`/DTLS and **fail closed**
> (never downgrade to plaintext). See [07-THREAT-REVIEW-AND-FIXES.md](07-THREAT-REVIEW-AND-FIXES.md)
> for the full review folded into this spec.

## 2. Components

```
        PC (home, behind NAT)                 INTERNET                MOBILE (anywhere)
 ┌───────────────────────────────┐                              ┌────────────────────────┐
 │ IDE (VS Code) + Mias extension │                              │  Mias app (RN)         │
 │  ├─ Session Server (local)     │                              │   ├─ Bridge Client     │
 │  │   • holds the job/session   │   outbound WSS    ┌────────┐ │   │   • pairing UI     │
 │  │   • E2EE endpoint           │◀────────────────▶│ RELAY  │◀┼───┤   • live updates    │
 │  │   • seq/ack + resend buffer │                  │(blind  │ │   │   • stream renderer │
 │  └─ talks locally to ▼         │   outbound WSS    │ relay) │ │   └─ E2EE endpoint     │
 │     Inference Worker           │                  └────────┘ │                        │
 │     (desktop/server.py, MCP)   │       (optional P2P/WebRTC: relay only does signaling,│
 │     + file/build/tool access   │        then phone⇄PC connect directly via ICE/STUN)   │
 └───────────────────────────────┘                              └────────────────────────┘
```

- **Mias IDE extension (PC).** Starts/stops sessions, shows the pairing code/QR,
  exposes the editor's workspace (files, terminal/build output, the local
  inference worker) to the session server. VS Code first; the protocol is
  editor-agnostic so a future Mias IDE reuses it.
- **Session server (PC, local process).** The brain of the PC side. Owns session
  state, dials out to the relay, terminates E2EE, runs the seq/ack + resend
  buffer for resumable streams, and brokers requests to the inference worker and
  workspace tools. Survives phone disconnects so the **job keeps running**.
- **Inference worker (PC).** Today's `desktop/server.py` (llama.cpp + MCP). The
  session server calls it over localhost and **must bind it to `127.0.0.1` with a
  random per-launch token** (it defaults to `0.0.0.0` today) so it is never a
  second remote entry point behind the Bridge (07-S28). Unchanged in spirit; gains
  streaming.
- **Relay / rendezvous (public, tiny).** Accepts outbound WSS from both sides,
  matches them by a **high-entropy `rendezvous_id`** (never the human pairing
  code; see 03/07-S1) and forwards opaque frames. A **relay-issued opaque slot
  token** (handed to each peer on first attach) is required to **reclaim** a slot
  on reconnect, so knowing the `rendezvous_id` alone can't evict a live peer
  (07-S30). The relay can host **multiple labeled byte-channels** under one
  `rendezvous_id` (keyed by `role`+`channel`) so the interactive and bulk classes
  don't share one TCP stream (07-S37); rooms have a TTL and per-IP creation limits
  (07-S21). **Stateless to content, zero-knowledge.** Self-hostable (frp/rathole/
  Pangolin-class, or our own ~200-line service). Default: user self-hosts or uses
  a Mias-run blind relay; either way it cannot read traffic.
- **Bridge client (mobile).** Pairing UX, saved devices, transport selection
  (relay → P2P upgrade → Tailscale if present), live-update rendering, resume.

## 3. Transport tiers (auto-selected, transparent to the app)

| Tier | When used | Latency | Needs |
|---|---|---|---|
| **T1 Relay (WSS)** | Always available; default & fallback | +1 relay hop | A reachable relay |
| **T2 P2P (WebRTC)** | After relay signaling succeeds *and* ICE finds a path | Direct (lowest) | STUN; TURN as its own fallback |
| **T3 Tailscale** | Power users who installed it on both devices | Direct over WireGuard | Tailscale on both |

The client negotiates the best tier but **all carry the same E2EE protocol** (04),
so upgrades/downgrades are seamless mid-session.

## 4. Request lifecycle (a remote turn)

1. Phone is paired (03). It has an E2EE channel to the session server (via current tier).
2. User sends a prompt/command → encrypted Bridge message → relay → session server.
3. Session server calls the local inference worker (MCP `tools/call`) and/or
   workspace tools (run build, read/patch files).
4. Output **streams** back as sequenced `token`/`log`/`diff` frames; the session
   server buffers them against acks so a phone drop can resume.
5. Phone renders live. If it backgrounds/drops, the **job continues on the PC**;
   on return the phone reconnects and replays from its last ack.

## 5. Why this satisfies the goals

- **G1 any network:** both sides dial out; relay needs no inbound at the PC. ✔
- **G2 smooth:** streaming pipelines; distance only delays TTFT (tested). ✔
- **G3 easy pairing:** code/link/saved-device, no account. ✔ (03)
- **G4 privacy:** relay is zero-knowledge; E2EE end-to-end; relay self-hostable. ✔
- **G5 live/bidirectional:** persistent duplex channel, server push. ✔ (04)
- **G6 resilient:** session server owns the job; seq/ack resume. ✔
- **G7 RN/iOS:** T1 is plain WebSocket (pure JS); T2 via react-native-webrtc. ✔ (05)

## 6. Rejected / deferred

- **Phone-dials-PC inbound:** impossible off-LAN. Rejected.
- **Tailscale as default:** too much setup for the common case. Deferred to T3.
- **Third-party tunnel in the data path without our E2EE:** violates G4. Rejected.
- **Bare short code as a server password:** guess-able / relay-trusting.
  Replaced by SPAKE2+ (03).

## 7. Open architecture questions (track to closure)

1. Relay hosting default: Mias-run blind relay vs. user self-host first? (privacy
   optics vs. zero-setup). Leaning: ship a self-host image **and** a default blind relay.
2. TURN ownership for T2: self-host coturn vs. skip P2P until v2.
3. Where the session server runs: inside the extension host process vs. a
   sidecar the extension spawns (sidecar is more robust to IDE reloads).
4. Multiple phones per PC / multiple PCs per phone in v1? (default: 1↔1, design keys to allow N later).

## 8. Remote authorization & sandboxing (RCE control)

Exposing workspace tools (run build, terminal, patch files, local inference) to a
remote phone is **remote code execution on the home PC** if pairing is ever
bypassed or the phone is compromised. The session server enforces, by default:

- **Capability scopes are a server-side session property, never client-asserted
  (07-S27).** The granted scope (`read` → browse/inference only, `edit` → file
  writes in-jail, `exec` → run commands) lives on the session server, raised by an
  explicit, biometric-confirmed user action. **Default = read.** Each inbound `rpc`
  is **classified on the PC** to the capability it requires and **rejected
  fail-closed** if it exceeds the granted scope. A scope tag in the `rpc` frame is
  only a *request* that may trigger an escalation prompt — it can never *grant*
  capability (04 section 1).
- **Approval works with nobody at the PC (07-S26).** The product premise is the
  user is *away* from the home PC, so "click Approve in the IDE" cannot be the
  authority for a remote turn. For remote sessions, an exec/destructive op is
  authorized only when **all** hold: (a) the session is in `exec` scope (granted
  earlier by a biometric-gated action), (b) the specific op is **per-op
  biometric-confirmed on the phone** (the human is the human, whichever device),
  and (c) it matches a **per-project command allowlist** — anything outside the
  allowlist needs a one-time biometric override. An interactive IDE prompt is used
  **additionally** only when a human is actually present at the PC. Every exec is
  logged to a tamper-evident session audit log.
- **Workspace path jail:** all file ops resolve to a canonical real path that must
  be contained under the opened project root; `..` and symlink escapes are
  rejected (re-implement the containment check the Android `StorageGuard` already
  does, server-side — that code is mobile-only and not directly reusable).
- **Guardrails are a content heuristic, not a command sandbox (07-S29).**
  `GuardrailProcessor` is an NL jailbreak/harmful-content filter; it does **not**
  sanitize shell commands or paths. Use it on free-text prompts and on tool
  *output* fed back to the model; do **not** rely on it to make `exec`/file ops
  safe — the jail + scopes + per-op approval + allowlist are the real controls.
  Truncate tool output (existing `MAX_TOOL_OUTPUT_LENGTH`).
- **The local inference worker is localhost-only (07-S28).** `desktop/server.py`
  defaults to binding `0.0.0.0` with the `X-Mias-Token` enforced only when
  `MIAS_TOKEN` is set. The session server **must** launch/use it bound to
  `127.0.0.1` with a random per-launch token, so the *only* remote path is the
  E2EE Bridge — never a second, unauthenticated LAN entry point behind the Bridge.
- **Idempotency:** side-effecting `rpc` carry an idempotency key (minted by the
  phone at compose time, persisted with the outbox) so a resent frame after
  reconnect — or after either device restarts — never double-runs a build or
  re-applies a write (04 section 5, 07-S39).

This is the highest-severity area of the review (07-S4, S26-S28); treat scope=exec
as a privileged feature gated behind explicit, per-session, biometric-confirmed
user consent.
