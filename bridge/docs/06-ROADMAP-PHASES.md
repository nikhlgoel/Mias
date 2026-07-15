# 06 — Roadmap (Phases & Stages)

Incremental, testable, each phase shippable on its own. **P0 = research (this
folder, done).** The RN migration of the Android core is interleaved as **R1**
because the Bridge client should be born in RN (see 05).

Legend: **Deliverable** = what exists at the end · **Exit test** = how we know it's done.

> **Security gate:** every phase must satisfy the relevant **[BLOCKER]** items in
> [07-THREAT-REVIEW-AND-FIXES.md](07-THREAT-REVIEW-AND-FIXES.md) before it ships.
> The section "Quick checklist to start building" in 07 is the canonical task list.

---

## R1 — RN migration of the Android app core  *(prerequisite, runs first)*
Migrate chat + offload UI to React Native (Android), keeping current LAN MCP
offload working through a new transport abstraction.
- **Deliverables:** RN Android app at feature-parity for chat/offload;
  `BridgeTransport` TS interface with a `WSS-to-LAN` impl talking to today's
  `desktop/server.py`.
- **Exit test:** RN Android app does a real LAN offload turn (prompt → desktop
  model → streamed answer) with the same numbers as the Kotlin app.
- **Why first:** de-risks RN + proves the transport abstraction before any relay.

---

## P1 — Relay MVP (T1), code pairing, single offload turn over the internet
The headline capability, minimal form.
- **Build:**
  1. **Relay** service (self-hostable, ~stateless, zero-knowledge forwarder).
  2. **Session server** as a standalone process (CLI first, no IDE yet): dials
     relay, terminates E2EE, proxies to local `desktop/server.py`.
  3. **Balanced-PAKE pairing** (CPace/SPAKE2) with a Base32 ≥30-bit code, channel
     binding, key confirmation, one-shot + burn-on-fail; relay routes on a
     high-entropy `rendezvous_id` (≠ code); per-session X25519 ECDH; counter/
     XChaCha20 AEAD. (07-S1,S2,S3,S6,S7,S8)
  4. **RN Bridge client**: enter code → connect → send prompt → stream answer.
- **Deliverables:** phone on cellular completes a real offload against a home PC,
  E2EE, no router config.
- **Exit tests:**
  - Cross-network turn succeeds (phone LTE ↔ home PC).
  - Wireshark/relay logs show **only ciphertext** at the relay; relay routes on
    `rendezvous_id` and holds no code; wrong code burns after one guess.
  - TTFT and inter-token gap within target (see section Targets) at national distance.

## P2 — Persistent sessions, live updates, resume
Make it a *control plane*, not a one-shot.
- **Build:** seq/ack + **bounded resend buffer with backpressure-to-generation**
  and **detach TTL** (07-S13); `live` push; `ctrl:resume`; **adaptive heartbeats**
  (07-S15); cancel; **idempotency keys** on side-effecting rpc (07-S12).
- **Deliverables:** start a long job, lock the phone, walk between networks,
  reopen → stream resumes; job never died on the PC.
- **Exit tests:**
  - Kill the phone socket mid-stream → reconnect → **zero lost/dup** frames,
    served by **replaying the buffer/persisted log, not by regenerating** (07-S34).
  - Background the app 5 min on a real device → resume cleanly.
  - **Detached long job runs to completion** while the phone is fully offline (spill
    to persisted log, generation does **not** pause), then the phone catches up on
    return (07-S36).
  - **Force-kill the app** with an instruction queued offline → it still sends once
    on reconnect (durable outbox + idempotency, no double-run) (07-M14/S39).
  - PC-initiated "build failed" appears on the phone without polling.

## P3 — IDE extension (VS Code) + workspace tools
Wrap the session server in the actual product surface.
- **Build:** VS Code extension hosting/sidecar-spawning the session server;
  **Start/Stop session** UI with code + **verified-link/QR**; expose workspace
  tools behind **default-deny capability scopes (read/edit/exec)** with
  **PC-side approval for exec**, **workspace path jail**, and GuardrailProcessor
  (07-S4, 02 section 8); inference worker auto-launch.
- **Deliverables:** from the phone, run a build / edit a file / ask the desktop
  model **in the context of the open project**, live.
- **Exit tests:** end-to-end "fix this from my phone" loop on a real repo;
  extension survives IDE reload (sidecar) without dropping the session; **exec
  requires explicit approval; path-jail escape attempts are rejected**.

## P4 — Saved devices & frictionless reconnect
The daily-driver UX.
- **Build:** device identity keypairs + pinning on first pair; encrypted
  device store on the phone; relay "latest live session for this PC key" lookup;
  one-tap mutual-auth reconnect; "forget device" / revoke from the extension.
- **Deliverables:** open app → tap saved PC → connected to its latest session,
  fully authenticated, no code.
- **Exit tests:** reconnect with no code; revoked device is refused; biometric
  gate enforced before keys unlock.

## P5 — P2P upgrade (T2, WebRTC) + Tailscale (T3)
Latency + privacy-max options, transparent to the app.
- **Build:** WebRTC DataChannel via relay-as-signaling, ICE/STUN, TURN fallback,
  `react-native-webrtc`; seamless tier swap; revive `TailscaleMeshClient` as an
  optional T3 the client prefers when present.
- **Deliverables:** automatic direct connection where the network allows; relay
  bandwidth drops; Tailscale users skip the relay entirely.
- **Exit tests:** P2P established between two real NATs; on symmetric/CGNAT it
  falls back to relay with no user-visible break; T3 path verified.

## P6 — Hardening & ops
Production polish.
- **Build:** relay rate-limiting/abuse controls, code brute-force lockout, key
  rotation, observability (no-content metrics), self-host relay docker image +
  docs, privacy-copy update for the one new allowlisted host.
- **Exit tests:** security review; load test the relay;
  documented self-host path reproducible from scratch.

## (Later) P7 — Mias-native IDE
Reuse the exact Bridge protocol behind a purpose-built editor. No protocol change
expected — that's the payoff of an editor-agnostic design.

---

## Targets (carry from PRD-style gates; verify per phase)
| Metric | Target |
|---|---|
| TTFT, national distance, relay path | ≤ ~150 ms over base model latency |
| Inter-token gap once streaming | bounded by generation, not network |
| Reconnect → resume after a drop | < 2 s, zero lost/dup frames |
| Frames readable by relay | **0** (must stay zero every phase) |
| New outbound hosts beyond allowlist | **1** (the relay) with all opt-ins off; **+1 push gateway** when background push is on (07-Q5); **+1 provider host** when cloud passthrough is on (07-Q1) |

## Dependency order
`P0 ✅ → R1 → P1 → P2 → P3 → P4 → P5 → P6 → (P7)`
P1 technically needs only R1's transport abstraction; P3 needs P1–P2; P4 needs
P1's key exchange; P5 is independent once P1 exists and can be parallelized.

## Wiring-fix task list (from the fourth pass — the seams that must be solid)
Concrete connect/contract changes, ordered by the phase they gate. Each ties to a
fourth-pass finding in 07. These are *contracts between components*, not features.

**Gate P1 (relay + pairing + first turn):**
1. **Relay channel contract:** route on `rendezvous_id` **+ labeled channel**
   (`role`+`channel`); issue an **opaque slot token** on first attach and require
   it for reclaim; room TTL + per-IP limits. *(S30, S37, S21)*
2. **Worker isolation contract:** session server binds `desktop/server.py` to
   `127.0.0.1` with a random per-launch token; constant-time token compare. *(S28)*
3. **Pairing transcript** binds `rendezvous_id` + ephemeral pubkeys + relay origin
   **+ protocol-version offers**; `device_key`/`resume_token` only inside AEAD;
   only `rendezvous_id`+slot token in cleartext. *(S32)*
4. **Biometric→key binding:** saved-device keys are Keystore `CryptoObject`-gated,
   not behind a boolean prompt. *(M15)*

**Gate P2 (sessions + resume + live):**
5. **Frame-counter contract:** `frame_id` (transport dedup) vs per-stream
   `seq`/ack-cursor — fix the schema and every send/recv site. *(S35)*
6. **Resume contract:** replay exact frames from buffer → persisted log; never
   regenerate; fail loudly on unavailable seq. *(S34)*
7. **Detached-job contract:** spill to persisted log + keep generating (detach TTL
   budget); pause only for an attached-but-slow consumer. *(S36)*
8. **Idempotency contract:** phone mints key at compose time → durable outbox;
   PC dedup cache persists for the session. *(S39, M14)*
9. **Durable outbox:** disk-persisted encrypted store under `OperationQueue`
   dispatch. *(M14)*
10. **Health contract:** state machine consumes device-offline (`ConnectivityMonitor`)
    **and** link-dead (heartbeat) as distinct signals. *(M16)*
11. **Burn-on-fail scope:** handshake-only; mid-session AEAD failures drop+count. *(S31)*

**Gate P3 (IDE extension + workspace tools):**
12. **Scope-enforcement contract:** scope is server-side; PC classifies each op and
    rejects fail-closed; frame scope is a *request* only. *(S27)*
13. **Remote-approval contract:** exec = granted scope **+** per-op phone biometric
    **+** per-project allowlist (+ optional IDE prompt when present); audit log. *(S26)*
14. **Guardrail boundary:** `GuardrailProcessor` only on free-text in/out, never as
    a command/path sanitizer; jail + scope + approval are the real controls. *(S29)*

**Gate P6 (hardening/ops):**
15. **Allowlist truth:** relay (+ push gateway when push on, + cloud host when
    opt-in) reflected in policy + privacy copy. *(Q5, Q1)*
16. **Make-before-break scope:** cross-tier only; relay↔relay is break-before-make
    via cursor — encode in `TransportManager`. *(S38)*
