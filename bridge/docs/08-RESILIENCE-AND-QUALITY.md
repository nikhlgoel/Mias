# 08 — Resilience & Output Quality (deep)

The promise: **regardless of how weak, flaky, or intermittently-off the network
is on either device, the user loses near-zero output quality or precision.** This
doc shows, at the connection/channel/function level, exactly how that holds.

## 1. The core guarantee: quality is decoupled from the network

The single most important design fact:

> **The model runs on the PC and its output is fully produced and persisted there,
> independent of the link.** The network only decides *when* and *how smoothly*
> the user *sees* output — never *what* is produced. So content is never wrong,
> truncated, or degraded by a bad connection; the worst case is added latency or a
> brief visual stall, after which the exact, complete output is reconciled.

Everything below is about hiding latency/stalls and guaranteeing *exactly-once,
in-order, complete* delivery — not about protecting model quality, which the
architecture removes from the network's reach entirely.

Empirically (../poc): a single stream survived **6 random disconnects** and still
delivered **200/200 tokens, in order, zero loss/dup**, relay reading 0.

## 2. Transport ladder + redundancy (not just failover)

| Tier | Transport | Role |
|---|---|---|
| T2 | WebRTC DataChannel (ICE/STUN, TURN fallback) | **Primary when available** — lowest latency, direct |
| T1 | Relay over **WSS/443 (or HTTP/3)** | **Always-on fallback & warm standby** |
| T3 | Tailscale/WireGuard | Optional power-user direct path |

- **Make-before-break is cross-tier only (07-S38).** A warm standby makes sense
  between *different* tiers (e.g. keep relay warm while P2P is primary). It does
  **not** work relay↔relay: the relay allows one peer per `role`+`channel` and a
  second socket *reclaims* (evicts) the first (02 section 2), so a same-tier relay
  reconnect is unavoidably **break-before-make**, recovered losslessly by the
  resume cursor. Overlap during a *cross-tier* switch is safe because dedup is by
  **`frame_id`** (section 4) — a frame seen on both paths renders once — while
  ordering/resume stay **per logical stream** (07-S35).
- **443/TLS framing:** the relay speaks `wss://` on 443 so captive portals,
  campus, and corporate firewalls that only allow HTTPS still pass it.
- **HTTP/3 / WebTransport option:** QUIC gives **connection migration** (survives
  a Wi-Fi↔cellular switch without a new handshake) and per-stream flow control (no
  cross-stream HOL). Strong future default for the relay path; gated on RN support.

## 3. Multiple channels (avoid head-of-line blocking)

A single TCP/WSS connection suffers **TCP-level HOL blocking**: one lost packet
stalls *everything*, including interactive tokens, behind a bulk transfer. So:

- **Two physical connections** on the relay path: **(c1) interactive** (control +
  tokens, tiny, latency-critical) and **(c2) bulk** (files, images, model chunks).
  A stalled bulk transfer can never freeze token streaming. This requires the relay
  to route **labeled channels** under one `rendezvous_id` (keyed by `role`+`channel`,
  02 section 2) — a single multiplexed socket cannot defeat *TCP-level* HOL, only
  app-level fairness (07-S37). 04 section 6 must agree: app-priority is the
  within-channel scheduler; distinct channels are what actually remove HOL.
- On **WebRTC**, use **separate DataChannels** per class (control / tokens / bulk),
  optionally an **unreliable+unordered** channel for disposable telemetry.
- Within a channel, an application **priority scheduler** interleaves small bulk
  chunks so a big artifact yields to live tokens.

Logical streams (each with its own `stream_id` + cursor): `tokens`, `logs`,
`build`, `files`, `model-dl`, `telemetry`. They resume independently.

## 4. Exactly-once, in-order delivery (function-level)

- Every sender stamps a **session-global `frame_id`** (monotonic, for transport
  dedup across a make-before-break overlap) **and** a **per-stream `seq`**
  (ordering + resume). Do not conflate them (07-S35, 04 section 2): a transport
  switch must not reset ordering, but the interactive and bulk streams are
  unordered relative to each other and resume independently.
- Receiver keeps a `next_needed` cursor **per `stream_id`** and a small dedup set
  keyed on `frame_id`; a frame already seen is dropped (idempotent render).
- Sender holds a **bounded resend buffer**; on `ctrl:resume{per-stream cursors}` it
  replays the **exact persisted frames** from each cursor (never by regenerating
  model output — 04 section 5); acked frames are pruned per stream.
- Side-effecting `rpc` carry **idempotency keys** so a resent "run build" never
  double-runs (07-S12).

## 5. Connection state machine

```
        ┌─────────┐  loss/RTT spike   ┌──────────┐
        │CONNECTED │ ────────────────▶ │ DEGRADED │
        └────┬─────┘ ◀──────────────── └────┬─────┘
             │ link lost / app background     │ persistent failure
             ▼                                ▼
        ┌──────────────┐  link back   ┌──────────────┐
        │  SUSPENDED   │ ───────────▶ │ RECONNECTING │──┐ backoff+jitter
        │ (offline,    │              └──────┬───────┘  │ try T2→T1→T3
        │  queue local)│  ◀──────────────────┘          │
        └──────────────┘     resume(cursor)             ▼
                                              ┌──────────────┐
                                              │   RESUMED    │ → CONNECTED
                                              └──────────────┘
```

- **DEGRADED:** raise jitter-buffer depth, coalesce tokens, drop telemetry — keep
  interactivity.
- **SUSPENDED (either device offline / app backgrounded):** the **PC keeps
  working**; the phone **queues the user's outgoing instructions in a *durable*
  encrypted outbox** (persisted to disk, not the in-memory `OperationQueue` — see
  11 section B / 07-M14) and shows an offline badge. Two-way durability — neither
  direction loses intent, even if the OS kills the backgrounded app.
- **Distinguish "device offline" from "link dead" (07-M16).** `ConnectivityMonitor`
  tells you the *phone* has no network → wait for connectivity (SUSPENDED). A
  missed heartbeat while the phone *has* network means the *link/peer* is dead →
  go straight to RECONNECTING and race transports. They are different signals with
  different recovery; the state machine must consume both, not collapse them into
  one boolean (the current `isDesktopReachable` is neither — it is "configured").
- **RECONNECTING:** exponential backoff **with jitter** (avoid relay thundering
  herd); race transports (happy-eyeballs); on success → `resume`.
- **RESUMED:** replay from each stream cursor; if the gap exceeds the PC buffer,
  fetch the **persisted session log/artifact** (no loss, just a fetch).

## 6. Smoothness under jitter (perceived quality)

- **Client jitter buffer:** tokens are released to the UI at a smoothed cadence,
  so a burst arriving after a stall renders as steady typing, not a dump. (PoC
  models this; smoothed p95 ≤ raw p95.)
- **Coalescing on high-RTT/lossy links:** pack several tokens per frame to cut
  overhead while keeping perceived flow.
- **Optimistic UI:** user input shows instantly as pending; status reconciles.
- **Compression:** token/log text is highly compressible — deflate frames on
  metered/slow links.

## 7. Backpressure & resource safety

- Receiver advertises a window; sender paces to it.
- If the resend buffer fills **while a consumer is attached but slow**, the session
  server **throttles or pauses generation** rather than growing unbounded (07-S13).
- **Detached ≠ paused (07-S36).** When *no* phone is attached, do **not** pause —
  the headline promise is the job runs while the user is away. Spill evicted frames
  to a **persisted session log** on disk and keep generating to completion, bounded
  by the **detach TTL / resource budget** (wall-clock + disk), not by buffer size.
  The phone later resumes from its cursors, pulling from RAM buffer or the log
  transparently.
- **Phone health-aware:** the phone reports battery/thermal (reuse `core/thermal`);
  the PC throttles payload size to a hot/low-battery phone.

## 8. Heartbeats & dead-link detection

- **Adaptive heartbeat** (RTT-scaled, battery-aware): frequent on good Wi-Fi,
  sparse on cellular idle.
- **NAT keepalive:** UDP ~15–25 s for WebRTC, TCP keepalive + app ping for WSS, to
  stop NATs dropping idle mappings.
- **Half-open detection:** missed `pong` within budget → start RECONNECTING on the
  standby path immediately (make-before-break).

## 9. Network factors checklist (incl. the ones not yet on your radar)

| Factor | Effect | Handling |
|---|---|---|
| **VPN** (your note) | Mostly just longer routing/latency (hairpin through VPN PoP); may change egress IP → triggers migration/ICE restart; some block UDP | Connection-migration transport + relay/TURN over TCP/443 always works; expect higher TTFT, not failure |
| **CGNAT / symmetric NAT** (cellular) | P2P direct usually fails | Relay/TURN fallback (always present) |
| **Captive portal** (campus/hotel) | Traffic hijacked to a login page | Detect (probe), surface "sign in to Wi-Fi" UX, retry after |
| **Metered / capped data** | Cost, throttling | Metered-mode: no auto model downloads, compress, smaller payloads, warn before big transfers |
| **Corporate firewall** | Blocks non-443/UDP | WSS on 443 looks like HTTPS; TURN/443 |
| **IPv4/IPv6 dual stack** | Path selection | Happy-eyeballs; try both |
| **Packet loss / low bandwidth** | Stalls, retransmits | Coalesce, compress, jitter buffer, congestion-aware pacing |
| **Clock skew** | Code/lease TTL errors | Use server-issued TTLs + tolerance; don't trust client clock |
| **Reconnect storms** | Relay overload after an outage | Backoff with jitter, server-side rate limit |
| **Roaming Wi-Fi↔cellular** | IP change mid-stream | QUIC migration / ICE restart + resume cursor |

## 10. Module / function call graph (no cycles)

**Mobile (TS):**
```
UI (React)
  └─ SessionController            // state machine section 5, owns resume + global seq dedup
       ├─ TransportManager        // selects/holds transports, make-before-break section 2
       │    ├─ WebRtcTransport   ┐
       │    ├─ RelayTransport    ├─ implement BridgeTransport{connect,send,onMessage,close}
       │    └─ TailscaleTransport┘
       ├─ SecureChannel           // PAKE handshake (03) + AEAD seal/open (counter nonce)
       ├─ MessageRouter           // dispatch by frame type
       │    ├─ StreamRenderer     // jitter buffer section 6 → UI
       │    ├─ LiveUpdateHandler  // progress/status/file/build → UI
       │    └─ RpcClient          // request/await rpc_result; mints+persists idempotency keys (S39)
       ├─ HealthMonitor           // device-offline (ConnectivityMonitor) vs link-dead (heartbeat) — S/M16
       └─ DurableOutbox           // DISK-persisted encrypted instructions (NOT in-mem OperationQueue) — M14
```
**PC (extension/session server):**
```
SessionServer
  ├─ RelayConnector / WebRtcPeer  // dial out, BridgeTransport peer side
  ├─ SecureChannel                // PAKE + AEAD (mirror)
  ├─ MessageRouter
  │    ├─ InferenceWorkerClient   // → desktop worker / API adapter (09)
  │    ├─ WorkspaceToolHost       // server-side scope classify+enforce, path jail, biometric approval (02 section 8, S26/S27)
  │    ├─ SkillEngine             // (09)
  │    └─ MemoryStore             // hindsight + self-learning (09)
  ├─ ResendBuffer + JobRegistry   // section 4, section 7; detach → PersistedSessionLog (S36)
  ├─ IdempotencyCache             // dedup keys for life of session (S39)
  ├─ AuditLog                     // tamper-evident record of every exec/destructive op (S26)
  └─ PushNotifier                 // wake phone when backgrounded; push gateway is a 2nd outbound host (04 section 4, Q5)
```
Import rule: UI → SessionController → TransportManager → transports; nothing lower
imports upward. `SecureChannel` and `MessageRouter` are leaf utilities shared by
both ends via a common protocol package (reused after the RN migration).
