# Connectivity PoC — Method & Results (v3, fourth-pass)

> **Graduated to production (stage S5).** This Python PoC has served its purpose;
> the architecture it validated is now production TypeScript:
> - the relay → `/relay` (`@mias/relay`) — same zero-knowledge forwarder, plus
>   slot-token reclaim (07-S30), room TTL + rate limits, labeled channels;
> - the PC host → `/session-server` (`@mias/session-server`) — dials the relay,
>   terminates E2EE, proxies to the MCP worker, streams tokens;
> - the E2EE → `@mias/bridge-protocol` `SecureChannel` (real X25519 forward
>   secrecy + counter-nonce AES-256-GCM + channel binding + key confirmation +
>   a **real balanced PAKE — CPace over audited @noble ristretto255**, replacing
>   the code-KDF stand-in so a short code is offline-uncrackable);
> - the phone-side crypto → `nobleCryptoProvider` (pure-JS @noble), so the phone
>   runs this exact protocol with no native crypto module — a node↔noble interop
>   test proves an RN client pairs with a Node PC.
>
> The `/session-server` integration test reproduces the PoC's headline results
> against the production code: pairing over the relay, **relay reads 0 frames**
> (zero-knowledge), a streamed offload turn, wrong-code burn, and
> unauthenticated-reclaim rejection. The Python PoC below is kept as the original
> reference.

`relay_poc.py` is a 3-party simulation that validates the architecture (02) and
the security/reliability corrections (07) before any production code. Independent
**relay**, **PC host**, and **mobile** endpoints, **end-to-end encrypted**, with
an injected one-way WAN delay to emulate distance.

## How to run
```bash
pip install websockets cryptography            # (Windows/store Python: add --user)
RELAY_PORT=9123 WAN_MS=40  N_TOKENS=200 python -u relay_poc.py   # national
RELAY_PORT=9124 WAN_MS=150 N_TOKENS=300 python -u relay_poc.py   # intercontinental
RELAY_PORT=9125 WAN_MS=5   N_TOKENS=200 python -u relay_poc.py   # ~LAN
```
`WAN_MS` = one-way latency per relay→peer hop. `RELAY_PORT` lets parallel runs use
distinct ports (use a **fresh/random port per run**; do **not** `pkill python`).
`BUF_CAP` (default 32) is the RAM resend-buffer size — deliberately small so a long
detach forces replay from the persisted log. Each scenario uses a fresh
`rendezvous_id` + code.

## What it faithfully models
- **Outbound-from-PC + relay**, routed on a **high-entropy `rendezvous_id`**, with
  the human **code used only for key derivation** (never for relay routing) — the
  07-S1 fix.
- **Zero-knowledge relay:** it tries to JSON-parse every forwarded frame and counts
  successes; E2EE keeps that count **0**.
- **Counter-nonce AEAD** (07-S6) via a per-(key,direction) `Sealer`.
- **Generation is decoupled from sending (07-S34/S36).** The host advances a
  monotonic `gen_ptr` that **never rewinds** — each token is produced **exactly
  once** into a bounded RAM buffer **and** a full persisted log. Resume **replays
  the stored frames**; it can never "regenerate" model output. Generation keeps
  running while the client is detached (the job doesn't pause), spilling to the log.
- **Buffered/log resume** (07-S11): the mobile force-closes mid-stream, reconnects
  with **exponential backoff + jitter** (07-S18), and resumes from `next_needed`;
  the host serves each missing frame from RAM, or from the **persisted log** once
  the RAM buffer has evicted it.
- **Idempotent side-effecting rpc** (07-S12/S39): `run_build` is sent, the result is
  lost to a disconnect, and the **same idempotency key** is resent on reconnect —
  the host executes the side effect **exactly once**.
- **Burn-on-fail is pairing-only** (07-S31): a mid-session decrypt failure is
  dropped + counted, never fatal.

## What it still simplifies (do NOT copy to prod)
- Key = `HKDF(code, salt=rendezvous_id)` as a **stand-in for a balanced PAKE
  (CPace/SPAKE2) + channel binding + ECDH** (real design = 03). No forward secrecy here.
- `ws://` loopback with injected latency, not real `wss://` over a real WAN.
- The relay's **slot-reclaim token** (07-S30) and **labeled channels** (07-S37) are
  passthrough/no-ops here — modeled in the docs, not enforced in the PoC.
- Backpressure for an *attached-but-slow* consumer (07-S13) is described in 04/08;
  the PoC exercises the *detached* path (spill-to-log), which is the one that
  previously contradicted "the job runs while you're away."

## Results (representative; numbers vary slightly per run)

National (WAN one-way 40 ms, 200 tokens, buf_cap 32):

| Scenario | TTFT | Median gap | Integrity | Replayed from log | Relay reads |
|---|---|---|---|---|---|
| A clean | 123 ms | 15.6 ms | 200/200 in order | 0 | 0 |
| B drop+resume | 119 ms | 15.5 ms | **200/200, no loss/dup** | 0 | 0 |
| C 6 random flaps | 106 ms | 15.5 ms | **200/200, no loss/dup** | 76 | 0 |
| D long detach | 119 ms | 15.5 ms | **200/200, no loss/dup** | **147** | 0 |
| E idempotent rpc | — | — | **1 execution** for 2 sends | — | 0 |

Intercontinental (WAN one-way 150 ms, 300 tokens):

| Scenario | TTFT | Integrity | Replayed from log | Relay reads |
|---|---|---|---|---|
| A clean | 336 ms | 300/300 in order | 0 | 0 |
| B drop+resume | 325 ms | **300/300, no loss/dup** | 0 | 0 |
| C 6 random flaps | 337 ms | **300/300, no loss/dup** | 329 | 0 |
| D long detach | 335 ms | **300/300, no loss/dup** | **247** | 0 |
| E idempotent rpc | — | **1 execution** for 2 sends | — | 0 |

## Conclusions
1. **Distance affects TTFT (~2× one-way latency), not smoothness** — token flow is
   paced by generation, not the network (frames pipeline). Relay path alone is
   smooth even intercontinentally; P2P (T2) is a TTFT optimization.
2. **The relay is provably zero-knowledge** (0 readable frames at every distance),
   and routes on `rendezvous_id` with no access to the code.
3. **Resume replays stored frames — it never regenerates (07-S34).** Scenarios C/D
   serve **76–329 frames from the persisted log** after the RAM buffer evicted
   them, and the host's `gen_ptr` reaches the full count while the client is
   detached — the earlier PoC's deterministic-token regeneration (which a real
   model cannot do) is gone.
4. **The detached job runs to completion without pausing (07-S36).** In Scenario D
   the phone is gone for 0.6 s; generation continues and spills to the log; on
   return the phone catches up with **zero loss/dup**.
5. **Side effects are exactly-once across reconnects (07-S12/S39).** Scenario E
   sends `run_build` twice (losing the first result) and the host executes it
   **once**, returning the deduped result.
6. **Reconnect uses exponential backoff + jitter (07-S18)** — the per-client half
   of reconnect-storm control (the other half is relay-side rate limiting).

## Engineering notes from building it
- `websockets` 16.x removed `connection.closed`; use try/except on send, not `.closed`.
- `CancelledError` is a `BaseException`, not `Exception`; await cancelled tasks via
  `asyncio.gather(task, return_exceptions=True)`.
- Don't `pkill -f python` in a shared sandbox — it kills the harness too; use a
  fresh `RELAY_PORT` per run instead.
- On Windows store Python, `pip install --user`; the console may render em-dashes
  as `?` (cp1252) — cosmetic, the source is UTF-8.

## What changed from v2 (the fourth-pass corrections this PoC now backs)
- **v2 bug:** the host's resend buffer was built but never read; resume rewound a
  cursor and **regenerated** `tok{i}` text. That masked the real failure mode
  (model output is not reproducible). **v3** replays from buffer→log and raises if a
  needed seq is unavailable. (07-S34)
- **New Scenario D** (long detach) proves generation doesn't pause and the log
  serves evicted frames. (07-S36)
- **New Scenario E** proves idempotent side-effecting rpc across a reconnect. (07-S12/S39)
- **Backoff+jitter** and a **mid-session-decrypt-drop counter** added. (07-S18/S31)
