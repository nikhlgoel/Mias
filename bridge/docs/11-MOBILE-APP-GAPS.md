# 11 — Mobile App: Gaps for the Whole Flow + Plan

A scan of the **current Kotlin Android app** against the end-to-end Bridge flow.
What exists, what's missing/buggy, and how to add it — **built in React Native
during the migration** (don't add throwaway Kotlin first; see `docs/RN_MIGRATION_PROMPT.md`).
Each gap notes the existing code to reuse.

## A. What already exists (reuse, don't rebuild)
- **Settings screen** with desktop **host / port / token** + **HF token** fields
  (`ui/settings/SettingsViewModel.kt`, `MiasPrefs`) — gate 7 is effectively done.
- **MCP offload client** (`core/network/mcp/McpClient.kt`) with full handshake +
  `X-Mias-Token` — the LAN baseline to evolve.
- **Orchestrator offload path** (`InferenceOrchestrator.desktopEngine`,
  `TawsAction.OFFLOAD_DESKTOP`).
- **OperationQueue** (`core/resilience/`) — in-memory dispatcher for the outbox.
  **Caveat (07-M14): it is explicitly NOT durable** ("operations do not survive a
  process restart"), so it cannot *be* the offline outbox on its own — it must sit
  on top of a disk-persisted store.
- **Foreground service + notifications** perms (`FOREGROUND_SERVICE_DATA_SYNC`,
  `POST_NOTIFICATIONS`) — basis for a held background connection + live updates.
- **CAMERA** permission already declared (for vision) — scanner can reuse it.
- **Real DB migrations** 1→5 on the conversations DB (`MiasDatabase`).

## B. Gaps & bugs (each → fix → reuse)

| # | Gap / bug (current state) | Severity | Fix (build in RN unless noted) |
|---|---|---|---|
| M1 | **No QR scanner** — no barcode/zxing/MLKit lib anywhere; pairing is manual host/port only | High | Add a camera **scanner** screen (RN: `vision-camera` + code-scanner, or MLKit barcode). CAMERA perm exists. **Phone scans; PC displays** (07-S22). Manual-code fallback always present (07-S25). |
| M2 | **No pairing flow/UI** (code entry, scan, "connecting…", approval) | High | New **Pair** flow: scan/enter code → PAKE → key confirmation → "remember device". UX in 10 section 6. |
| M3 | **No pairing crypto** — offload trusts a plaintext `X-Mias-Token` over HTTP on LAN | High | Implement the balanced-PAKE + AEAD **SecureChannel** (03) in the shared TS protocol pkg; keep token auth only for legacy LAN. |
| M4 | **No transport abstraction** — `McpClient` is direct HTTP to a manual IP; no relay/P2P/Tailscale | High | Introduce `BridgeTransport` (08 section 10) with Relay(WSS)/WebRTC/Tailscale impls; `McpClient` semantics ride on top. (= Bridge step R1.) |
| M5 | **Offload is single-shot** — `McpClient.generate()` returns one `String`, not streamed | High | Stream tokens over the Bridge channel (04 section 2); desktop worker → Streamable HTTP/SSE; render via jitter buffer (08 section 6). Fixes DESIGN debt #4. |
| M6 | **No connection-status / reconnect / resume UI** — only a static `isDesktopReachable = desktopEngine != null` (configured, not healthy) | High | Live connection state machine (08 section 5) + status chip + reassuring reconnect banner (10 section 5); replace the boolean with real heartbeat/health. |
| M7 | **No offline outbox** — outgoing instructions lost when offline | Med | Persist a **durable** encrypted outbox (Room/file), keyed by phone-minted idempotency key; use `OperationQueue` only as the in-memory dispatcher on top; flush on reconnect (08 section 5). See **M14** — in-memory alone loses intent on app kill. |
| M8 | **No saved-devices store** — prefs hold a single desktop host/port | Med | Encrypted saved-device records (pinned keys, rotating rendezvous id) in Keystore-backed storage (03 section 1d). |
| M9 | **No live-updates/push** — offload is request/response; nothing renders PC-initiated events | Med | Handle `live` frames (progress/status/file/build/notify) (04 section 4); background via foreground service; **push without FCM** — see C. |
| M10 | **DB data-loss landmine** — the **conversations DB** uses *unconditional* `fallbackToDestructiveMigration(dropAllTables=true)` (the real landmine: any unmatched upgrade wipes chats). The **model-hub** DB is milder — `fallbackToDestructiveMigrationOnDowngrade(dropAllTables=true)` (downgrade-only) — but **starts at `MIGRATION_2_3` with no 1→2 path**, so a real v1→v3 *upgrade* throws instead of migrating. | Med | Remove the unconditional destructive fallback on the conversations DB for release builds; add the missing model-hub `1→2` migration; require real migrations (PRD gate 8). Verify during RN data-layer bridging. |
| M11 | **`DownloadQueueEntity` loses ModelCard fields** (DESIGN debt #9) — non-curated HF models can't auto-resume after process kill | Low | Persist a JSON blob of the card so PC-driven + phone downloads resume. |
| M12 | **Tailscale Android path non-functional** (known) | Low | T3 optional transport later; not the default. |
| M13 | **Settings is LAN-IP-centric** | Low | Evolve Settings: pairing + saved devices become primary; manual IP becomes "advanced/legacy LAN". |
| M14 | **Outbox durability gap** — `OperationQueue` is in-memory; a backgrounded app is routinely OS-killed, so a queued instruction is lost before reconnect (contradicts 08 section 1 "two-way durability") | High | Disk-persisted encrypted outbox `{idempotency_key, ciphertext, created_at, status}`; idempotency key minted at compose time so a post-restart resend dedups (07-S39). |
| M15 | **Biometric is a UI gate, not a key binding** — `BiometricGate` returns a boolean; `ZkVault` MasterKey is not `setUserAuthenticationRequired`, so keys unlock with the app process, not with the user's biometric | Med | Store saved-device keys as Keystore keys with `setUserAuthenticationRequired(true)` and unlock via `BiometricPrompt.CryptoObject` so "stolen phone gets nothing" is cryptographic, not advisory (03 section 1d/section 3). |
| M16 | **Health conflation** — the connection state machine needs *device-offline* (`ConnectivityMonitor`, exists) **and** *link-dead* (heartbeat, new); `isDesktopReachable` is neither | Med | Wire both signals into the state machine (08 section 5): no-network → SUSPENDED/wait; heartbeat-miss-with-network → RECONNECTING/race transports. |

## C. Background connection & push without breaking "no Google" (recheck)
Holding a live link when the app is backgrounded:
- **Android:** a **foreground service** (perm already present) can hold the WSS/
  WebRTC link; for wake-from-killed use **UnifiedPush / ntfy (self-hostable)**
  rather than **FCM**, to preserve the no-Google-services stance (07-Q4). FCM stays
  an opt-in only if the user accepts it.
- **iOS (post-RN):** background sockets die; **APNs is unavoidable** for true
  background push. Design: content-free wake push → app reconnects → pulls
  encrypted detail (04 section 4). Treat APNs as transport metadata only (no plaintext).
- Either way, **the PC keeps running the job**; push only decides how fast the
  phone re-attaches — no quality impact (08 section 1).

## D. Sequencing (fits the RN migration phases)
1. **R1 vertical slice** (RN migration): chat over **`BridgeTransport(WSS→LAN)`**
   → fixes M4 scaffold + M5 streaming against today's server. Proves the
   abstraction with zero new infra.
2. **Pairing + relay** (Bridge P1): M1, M2, M3, M8, **M15** (biometric-key binding) + relay → internet-distance.
3. **Resilience + live** (Bridge P2): M6, **M16** (health signals), M7, **M14** (durable outbox), M9 + connection UX.
4. **Hardening:** M10, M11, M13; push (C); Tailscale M12.

Every item lands as RN UI + a method on a wrapped native module or the shared TS
protocol package — consistent with the migration's "wrap core, rewrite UI" rule.
