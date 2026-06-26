# 04 — Protocol & Live Updates

The Bridge protocol is a thin, **bidirectional, sequenced, encrypted** envelope
that carries MCP JSON-RPC plus a few Bridge-native message types for live push
and resume. It is transport-agnostic (same bytes over relay WSS, WebRTC
DataChannel, or Tailscale).

## 1. Layering

```
┌──────────────────────────────────────────────┐
│ Application semantics: MCP 2025-xx JSON-RPC     │  prompts, tools/call, results
│  + Bridge control msgs (session, live, resume)  │
├──────────────────────────────────────────────┤
│ Bridge frame: {seq, ack, type, nonce, ct}       │  sequencing, acks, resume
├──────────────────────────────────────────────┤
│ AEAD (AES-GCM / XChaCha20) with session keys    │  E2EE; relay sees only this
├──────────────────────────────────────────────┤
│ Transport: WSS relay | WebRTC DC | Tailscale    │  pick best, swap seamlessly
└──────────────────────────────────────────────┘
```

We **reuse MCP** because the app already speaks it (`McpClient.kt`,
`desktop/server.py`) and MCP's 2025 **Streamable HTTP** already standardizes
streaming + `MCP-Session-Id`. The Bridge keeps MCP semantics but moves them onto
a persistent duplex channel so the **server can push** (MCP-over-HTTP can't
natively push without a held SSE stream; our channel always can).

## 2. Frame shape (conceptual)

```jsonc
{
  "frame_id": 50231,    // session-unique, monotonic per sender — TRANSPORT dedup only (07-S16/S35)
  "stream_id": "tokens",// logical stream this frame belongs to
  "seq":   1024,        // monotonic PER stream_id — ordering + resume cursor (07-S35)
  "ack":   {"tokens":1019,"build":40}, // per-stream cumulative cursors (NOT one global ack — 07-S35)
  "type":  "rpc" | "rpc_result" | "stream" | "live" | "ctrl",
  "nonce": "…",         // AEAD nonce: XChaCha20 192-bit OR counter per (key,dir) — never random GCM (07-S6)
  "ct":    "…"          // ciphertext of the payload below
}
```

> **Two independent counters, do not conflate (07-S35).** `frame_id` is a
> session-global monotonic id used **only** to dedup a frame that arrives on two
> transports during make-before-break overlap (so it renders once). Ordering,
> gap-detection, and resume are **per logical stream** via `stream_id`+`seq`; acks
> are a **per-stream cursor map**, because the interactive token stream and a bulk
> file transfer (section 6, 08 section 3) are intentionally unordered relative to
> each other and must resume/prune independently. A single global cumulative ack
> would stall pruning of fast interactive frames behind a slow bulk stream.

Decrypted payloads by `type`:

| type | direction | payload |
|---|---|---|
| `rpc` | phone→PC | MCP JSON-RPC request (e.g. `tools/call generate`, run build, patch file). May carry a **requested** capability scope (read/edit/exec) that can *trigger an escalation prompt* but never grants capability — the PC classifies the op server-side and enforces against the session's granted scope, fail-closed (02 section 8, 07-S27). Side-effecting calls carry an **idempotency key minted by the phone at compose time and persisted in the outbox**, so a resend after a phone *or* PC restart dedups (07-S12/S39). |
| `rpc_result` | PC→phone | MCP JSON-RPC response (terminal result) |
| `stream` | PC→phone | `{stream_id, seq, chunk}` — token text, log line, or file-diff hunk |
| `live` | PC→phone | server-initiated push: `{kind: progress|status|file_event|build_event|notify, …}` |
| `ctrl` | both | `hello` (carries `rendezvous_id` + protocol versions), `resume`, `ping/pong`, `cancel`, `rekey`, `bye` |

**Transport:** `wss://`/DTLS only; **fail closed** if PAKE/key-confirmation or
device-key auth fails — never downgrade to plaintext (07-S8). **Rekey** session
keys on a byte/frame/time budget via `ctrl:rekey` (07-S6).

**Burn-on-fail is pairing-phase only (07-S31).** A failed key-confirmation burns
the code + `rendezvous_id` (03 section 1). But once a session is established, an
AEAD-open failure on an individual frame must be **dropped (counted), not used to
tear the session down** — otherwise a single corrupt/stale/reordered-across-rekey
frame becomes a trivial denial-of-service. Only a *sustained* rate of open
failures triggers a rekey/reconnect; the session never silently dies on one bad
frame.

## 3. Session lifecycle

```
                  (relay-facing cleartext: rendezvous_id + slot token ONLY)
phone ══ PAKE / device-key mutual auth ══▶ PC      establishes fresh session keys (03)
   ── everything below is AEAD-encrypted; the relay sees only ciphertext ──
phone ── ctrl:hello{device_key, resume_token?} ──▶ PC   (device_key is INSIDE AEAD — 07-S32)
PC    ── ctrl:hello_ack{session_id, caps} ───────▶ phone
phone ── rpc{prompt / command} ─────────────────▶ PC
PC    ── stream{token…} stream{token…} … ───────▶ phone      (live, pipelined)
PC    ── rpc_result{final} ─────────────────────▶ phone
   …phone backgrounds / network flips / app is KILLED…
phone ══ device-key mutual auth (NEW ephemeral keys) ══▶ PC   (fresh channel, FS preserved)
phone ── ctrl:resume{session_id, per-stream cursors} ─▶ PC    (after reconnect)
PC    ── replays buffered/persisted frames from each cursor ─▶ phone
```

Two layers move on reconnect: the **cryptographic channel is always fresh**
(new ephemeral ECDH → new session keys, so forward secrecy holds), while the
**application session is resumed by `session_id`** with per-stream cursors. The
`resume_token`/`session_id` is an opaque server-side handle that is honored
**only after** device-key mutual auth on the fresh channel — it is never a
standalone bearer (07-S32). The relay only ever sees `rendezvous_id` + the opaque
slot token in cleartext; the stable `device_key` is sent inside AEAD so the relay
cannot correlate devices across sessions (07-S5/S32).

The **session server keeps running the job** while the phone is gone; for a
detached session it **spills output to a persisted session log** (not just the
in-RAM resend buffer) so the job runs to completion within its resource/TTL
budget instead of stalling at buffer-full (section 5, 07-S36).

## 4. Live updates (the "see updates from anywhere" feature)

`live` frames are unsolicited pushes so the phone reflects PC state without
polling:
- `progress` — % / step counts for a long job.
- `status` — model loaded, worker busy/idle, thermal/battery of the PC, queue depth.
- `file_event` — file created/modified by a tool (path + small diff or pointer).
- `build_event` — compile/test started/passed/failed with a tail of output.
- `notify` — "job finished", "needs your input", actionable from the lock screen.

These also feed mobile **push notifications** when the app is backgrounded (so the
user gets "build failed" / "job done" without the app open). On iOS, where a held
socket dies in background, the session server triggers a push via the OS push
service carrying only a **non-sensitive** wake/notify signal; the phone opens,
reconnects, and pulls the encrypted detail. (No content in the push payload.)

> **Push is a second outbound host on the PC (07-Q5).** When background push is
> enabled, the **phone** hands its push token to the PC over the E2EE channel and
> the **PC posts the wake** to APNs (iOS) / UnifiedPush·ntfy (Android). That is an
> outbound connection to a push gateway — so the privacy invariant is **not**
> "exactly one outbound host (the relay)" once push is on; it is **relay + the
> chosen push gateway** (self-hostable for UnifiedPush/ntfy; APNs unavoidable on
> iOS, content-free). The allowlist and privacy copy must say so; the push gateway
> sees only `(token, wake)` metadata, never content. Background push is therefore
> an explicit opt-in. The relay-only "exactly 1 host" guarantee holds when push is
> off (PC keeps the job; the phone catches up on next foreground).

## 5. Reliability: sequence, ack, resume (the PoC finding)

The container test proved a naive "resend from N" loses frames. Required design:
- Every sender stamps a **monotonic `seq`**; every receiver reports a cumulative
  **`ack`**.
- The session server holds a **bounded resend buffer** of un-acked `stream`/`live`
  frames (e.g. last N MB or M seconds). On `ctrl:resume{from_seq}` it replays from
  the cursor; already-acked frames are dropped from the buffer.
- Streams carry their own `stream_id` + per-stream `seq` so multiple concurrent
  streams (tokens + build log) resume independently.
- `ping/pong` heartbeats detect dead transports fast; on miss, the client tries
  the next tier (P2P↔relay) without ending the session.
- Idempotent `rpc` ids/keys so a resent request is not double-executed — critical
  for `exec`/file-write ops across reconnects (07-S12).
- **Replay from the buffer/log, never by regeneration.** Model output is *not*
  reproducible (the model already advanced), so resume must replay the **exact
  persisted frames**. The resend buffer is the source of replay; if a requested
  `seq` has been evicted, the server serves it from the **persisted session log**
  (below) — it must never "re-run" generation to fill a gap. (The PoC was
  corrected to replay from the buffer and fall back to the log; see RESULTS.)
- **Bounded buffer — but distinguish slow-consumer from no-consumer (07-S13/S36):**
  - **Attached but slow** (phone online, link weak): cap the buffer and apply
    backpressure — throttle/pause generation rather than grow unbounded.
  - **Detached** (phone offline/backgrounded/killed): do **not** pause — the whole
    product promise is "the job keeps running while I'm away." The server **spills
    evicted frames to a persisted session log** on disk and keeps generating to
    completion, bounded by a **detach TTL / resource budget** (wall-clock + disk),
    not by buffer size. On return the phone resumes from its cursors, pulling from
    RAM buffer or the persisted log transparently.

## 6. Backpressure & smoothness

- The phone advertises a window; the PC paces `stream` frames to it (don't blast a
  slow link). Token text is tiny, so coalesce a few tokens per frame on high-RTT
  links to cut overhead while keeping perceived smoothness.
- Large artifacts (full files, images for vision) go as **chunked** transfers with
  their own stream id, never blocking interactive token streams.
- **Head-of-line blocking (07-S14/S37):** application-level priority + small-chunk
  interleave on one socket helps, but **cannot remove TCP-level HOL** — one lost
  packet still stalls everything behind it. So the relay path uses **two labeled
  byte-channels** under the one `rendezvous_id` (interactive `c1` / bulk `c2`,
  02 section 2), not a single multiplexed stream; T2 (WebRTC) uses **separate data
  channels**; HTTP/3/QUIC uses independent streams. 08 section 3 is the canonical
  description — keep the two docs consistent: app-priority is the *within-channel*
  scheduler, distinct physical/QUIC channels are what actually defeat HOL.
- **Adaptive heartbeats (07-S15):** tune `ping/pong` to the link (battery-aware on
  cellular); on miss, fail over P2P↔relay **without ending the session**.

## 7. Versioning

`ctrl:hello` carries a Bridge protocol version and the MCP protocol version; both
sides negotiate the min common, exactly like the existing MCP handshake. Adding
message types is backward-compatible (unknown `live.kind` is ignored).
