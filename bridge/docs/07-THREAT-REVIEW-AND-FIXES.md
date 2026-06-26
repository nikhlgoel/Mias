# 07 — Threat Review & Fixes (2026-06-26)

A second-pass self-review of the design before it becomes the build spec. Each
item: **Flaw → Risk → Fix → Where applied**. Items marked **[BLOCKER]** must be
in place before any phase ships; **[HARDEN]** before public/relay exposure. The
PoC was updated to match the corrected design and re-passes (see
[../poc/RESULTS.md](../poc/RESULTS.md)).

## Security

### S1 [BLOCKER] Relay routing conflated with the pairing secret
- **Flaw:** v1 keyed relay rooms on the human pairing code, so anyone who saw/guessed the code could be routed into the session.
- **Risk:** session hijack / impersonation at the relay.
- **Fix:** route by a **high-entropy `rendezvous_id`** the relay generates; the human **code is used only inside the PAKE** and never sent to or used by the relay. Relay matches exactly one host + one client per `rendezvous_id`.
- **Applied:** 02 section 2 (relay), 03 section 1/section 2, 04 section 3 (`ctrl:hello` carries `rendezvous_id`), PoC `scenario()`.

### S2 [BLOCKER] Wrong PAKE family + no channel binding
- **Flaw:** spec said SPAKE2+ (an *augmented* PAKE for asymmetric server-verifier storage); our pairing is *symmetric* (both sides hold the code). No transcript binding invited relay cross-connect / unknown-key-share.
- **Fix:** use a **balanced PAKE (CPace, or balanced SPAKE2)**; bind the transcript to `rendezvous_id` + both ephemeral public keys + relay origin; require an explicit **key-confirmation** step. (SPAKE2+ stays only if/where we later add asymmetric stored verifiers.)
- **Applied:** 03 section 1–section 2, section 6.

### S3 [BLOCKER] Short-code online guessing
- **Flaw:** "one guess" only holds if attempts are actually limited.
- **Fix:** code = **Crockford Base32, ambiguity-free, ≥ ~30 bits (7 chars)**; **TTL ≤ 5 min**; **single active use**; host **burns the code on the first failed key-confirmation**; relay + host **rate-limit + lockout** per `rendezvous_id`/IP.
- **Applied:** 03 section 1, section 4; 06 P1 exit tests; PoC host attempt-burn.

### S4 [BLOCKER] Remote workspace tools = remote code execution
- **Flaw:** exposing run-build / terminal / file-patch to the phone is RCE on the home PC if pairing is ever bypassed or the phone is compromised.
- **Fix:** **default-deny capability scopes** per session (`read` / `edit` / `exec`); **explicit PC-side approval** for exec/destructive ops (or a per-project command allowlist); **workspace path jail** (no escaping the opened project); reuse Mias `GuardrailProcessor` on tool inputs; idempotent side-effecting calls (S12).
- **Applied:** new 02 section 8 (Remote authorization & sandboxing), 04 section 1 (`rpc` capability tags), 06 P3 + P6.

### S5 [BLOCKER] Saved-device enumeration / metadata directory at relay
- **Flaw:** "look up the latest live session for this PC key" implies a relay-held pubkey→session directory queryable by anyone.
- **Fix:** **no plaintext directory.** The PC advertises under a **rotating `rendezvous_id` derived from a per-pair shared secret** only the paired phone can recompute; reconnect lookup is **authenticated** (mutual challenge-response on pinned device keys) and rate-limited. Relay never stores long-term identity→session maps.
- **Applied:** 03 section 1d, section 3; 02 section 2.

### S6 [BLOCKER] AEAD nonce reuse over long streams
- **Flaw:** random 96-bit GCM nonces per frame risk birthday collisions on multi-hour token streams (catastrophic for GCM).
- **Fix:** **counter-based nonces per (key, direction)** or **XChaCha20-Poly1305 (192-bit nonce)**; **rekey** on a frame/byte/time budget; new session keys per connection.
- **Applied:** 03 section 2 (key hierarchy), 04 section 2/section 5; PoC `Sealer` (counter nonces).

### S7 [BLOCKER] Forward secrecy
- **Flaw:** deriving session keys from the code/device-key alone means a later code/key compromise decrypts past captures.
- **Fix:** mix the PAKE/device-key output with an **ephemeral X25519 ECDH per session**; discard ephemerals after.
- **Applied:** 03 section 1, section 2.

### S8 [BLOCKER] Fail-closed + TLS transport
- **Flaw:** PoC used `ws://`; unspecified behaviour if E2EE/handshake can't verify.
- **Fix:** transport is **`wss://` (TLS)** for defense-in-depth + metadata hiding even under E2EE; **refuse to proceed** (never downgrade to plaintext) if PAKE/key-confirmation or device-key auth fails; relay URL validated (scheme/host), optional cert pinning. Allowlist gains **exactly one** new host (the relay).
- **Applied:** 02 section 5/section 7, 03 section 5, 04 section 2.

### S9 [HARDEN] Deep-link / QR interception
- **Flaw:** custom-scheme `mias://pair?...code` can be hijacked by another app or logged.
- **Fix:** prefer **verified Universal Links / App Links (https)**; **QR over copyable link**; code remains PAKE-protected + one-shot so interception yields one online guess only.
- **Applied:** 03 section 1b.

### S10 [HARDEN] Relay metadata leakage
- **Flaw:** even zero-knowledge of content, the relay sees who-talks-to-whom, timing, sizes.
- **Fix:** document the residual; offer **self-host relay** and **Tailscale (T3, no relay)** for metadata-sensitive users; optional padding/idle cover later.
- **Applied:** 03 section 3/section 5, 02 section 2.

## Performance / reliability

### S11 [BLOCKER] Resume correctness (the v1 PoC bug)
- **Flaw:** naive "resend from N" dropped a frame on reconnect.
- **Fix:** monotonic `seq` + cumulative `ack` + **bounded resend buffer**; per-stream cursors; resume replays from the client's `next_needed`. PoC now reconnects with **zero loss/dup** at every distance.
- **Applied:** 04 section 5; PoC `pc_host` buffer + `mobile` resume.

### S12 [BLOCKER] Idempotent side effects
- **Flaw:** a resent `rpc` could double-run a build or re-apply a file write.
- **Fix:** **idempotency keys** on side-effecting `rpc`; the session server dedupes by key across reconnects.
- **Applied:** 04 section 1/section 5; ties to S4.

### S13 [HARDEN] Resend buffer overflow / runaway job
- **Flaw:** if the phone is gone for hours the buffer grows unbounded and the job keeps burning GPU.
- **Fix:** **bounded buffer with backpressure to generation** (pause/throttle when no consumer / buffer full); **session detach TTL**; on long absence persist a compact log the phone fetches on return instead of full replay.
- **Applied:** 04 section 5/section 6; 06 P2.

### S14 [HARDEN] Head-of-line blocking
- **Flaw:** large file/image transfers on one ordered channel stall interactive tokens.
- **Fix:** **priority + small-chunk interleave** on single-ordered transports (WSS); use **separate WebRTC data channels** on T2.
- **Applied:** 04 section 6.

### S15 [HARDEN] Heartbeat tuning on cellular
- **Flaw:** fixed aggressive heartbeats drain battery; lax ones delay drop detection.
- **Fix:** **adaptive heartbeat** + fast transport-tier failover (P2P↔relay) without ending the session.
- **Applied:** 04 section 5.

## Quick checklist to start building (derived from above)
1. Relay: route by `rendezvous_id`; one host+one client; reclaim-on-reconnect; rate-limit; `wss://`; stores no plaintext, no identity directory. *(S1,S3,S5,S8,S10)*
2. Pairing: balanced PAKE + channel binding + key confirmation; code = Base32 ≥30 bits, TTL, one-shot, burn-on-fail. *(S2,S3)*
3. Crypto: per-session X25519 ECDH mixed with PAKE/device key; counter-nonce AEAD (or XChaCha20); rekey budget. *(S6,S7)*
4. Saved devices: pinned keys, rotating derived `rendezvous_id`, authenticated reconnect. *(S5)*
5. Protocol: seq+ack+bounded buffer; per-stream cursors; idempotency keys; priority interleave; adaptive heartbeats; fail-closed. *(S11–S15,S8)*
6. Workspace tools: default-deny scopes; PC-side approval for exec; path jail; GuardrailProcessor. *(S4)*

---

# Second deep pass (2026-06-26) — resilience / PC worker / UX

New issues surfaced when reviewing per-connection/function behaviour, the PC
extension, and UX. Validated by PoC Scenario C (6 random flaps → zero loss).

### S16 [BLOCKER] Sequence reset on transport switch
- **Flaw:** per-transport sequencing would duplicate/reorder when failing over P2P↔relay.
- **Fix:** **global per-session `seq`** (not per-transport) + dedup set; make-before-break overlap renders once. → 08 section 2/section 4.

### S17 [BLOCKER] TCP head-of-line blocking on one connection
- **Flaw:** a bulk transfer + one lost packet stalls interactive tokens.
- **Fix:** **two physical connections** (interactive vs bulk) on relay; separate WebRTC data channels; priority scheduler. → 08 section 3.

### S18 [HARDEN] Reconnect storms after an outage
- **Flaw:** synchronized clients hammer the relay on recovery.
- **Fix:** exponential **backoff with jitter** + server rate-limit. → 08 section 5.

### S19 [HARDEN] Mobile-network roam (Wi-Fi↔cellular) drops the session
- **Flaw:** IP change kills a plain TCP/WSS session.
- **Fix:** prefer **QUIC connection migration / ICE restart**; always recover via resume cursor. → 08 section 2/section 9.

### S20 [HARDEN] Clock-skew breaks code/lease TTLs
- **Fix:** server-issued TTLs + tolerance; never trust client clock. → 08 section 9.

### S21 [HARDEN] Captive portals / metered / firewalls
- **Fix:** captive-portal detect + sign-in UX; metered-mode (no auto model pulls, compress); WSS/TURN on **443**. → 08 section 9.

### Q1 [BLOCKER] Opt-in cloud API breaks the no-cloud promise
- **Flaw:** allowing a PC-side cloud API for AI access can silently exfiltrate.
- **Fix:** **off by default**, explicit labeled toggle, key encrypted **on PC only (never synced)**, per-turn "used <cloud>" indicator, allowlist + privacy copy updated. → 09 section 1.

### Q2 [HARDEN] Memory/self-learning sync conflicts & exposure
- **Fix:** source-of-truth = PC for project memory / phone for personal; per-field LWW + visible conflict resolver; memory **encrypted at rest**, phone-viewable/redactable. → 09 section 5.

### Q3 [HARDEN] Skills = privileged code on the PC
- **Fix:** every skill declares a capability scope + host allowlist; `exec`/destructive skills need PC-side approval; path-jailed; guardrailed inputs; sandboxed custom skills. → 09 section 3 / 02 section 8.

### U1 [HARDEN] UX must not lose user intent or alarm the user
- **Fix:** offline outbox for outgoing instructions; reassuring (not red-modal) reconnect; optimistic send with recoverable failures; eye-comfort theming for long sessions. → 08 section 5, 10 section 4/section 5.

---

# Third pass (2026-06-26) — pairing direction, QR security, mobile gaps

Triggered by the QR-direction catch and a scan of the current Android app.

### S22 [BLOCKER] QR shown on the wrong device
- **Flaw:** mockup implied the phone shows a QR. Phones aim easily; many PCs lack a (rear) webcam.
- **Fix:** **PC displays the QR; phone scans** with its camera; phone screen is a viewfinder + manual-code fallback. → 03 section 1b, 10 section 6, mockup redone.

### S23 [BLOCKER] Phished QR could silently pair a phone to a stranger's PC
- **Fix:** **mutual confirmation** — after a scan the PC prompts "Approve <phone>?"; pairing completes only on PC-side approval. → 03 section 1b.

### S24 [BLOCKER] Malicious QR points the phone at an attacker relay
- **Fix:** the QR's `relay_host` is **advisory**; the app validates it against its configured/allowlisted relay and queries the user on mismatch. E2EE+PAKE still protect content. → 03 section 1c.

### S25 [HARDEN] Camera-only pairing excludes some users / denials
- **Fix:** manual code entry is **always** available; camera-permission denial degrades gracefully. → 03 section 1b/section 1c, 10 section 6.

### Q4 [HARDEN] Background push vs the no-Google-services stance
- **Flaw:** standard Android background push = FCM (Google); iOS = APNs.
- **Fix:** Android holds the link via a **foreground service** and uses **UnifiedPush/ntfy (self-hostable)** for wake, FCM opt-in only; iOS uses APNs as **content-free** wake (detail pulled after reconnect). PC keeps the job running regardless. → 11 section C, 04 section 4.

### Mobile gaps (current Android app) — see 11 for the full table + plan
M1 no QR scanner · M3 no pairing crypto (plaintext token over LAN) · M4 no transport
abstraction · M5 offload is single-shot not streamed · M6 no live connection/reconnect
UI (`isDesktopReachable` is "configured", not healthy) · M7 no offline outbox ·
M8 no saved devices · M10 `fallbackToDestructiveMigration(dropAllTables=true)`
data-loss risk. All scheduled into the RN migration phases (11 section D).

---

# Fourth pass (2026-06-26) — bugs + wiring (layer 2)

An independent adversarial pass focused on the **seams**: end-to-end and failure
flows, interface contracts, and the protocol↔resilience↔relay↔code reality
mapping. Earlier passes left contradictions where two correct-sounding rules
collide at a boundary (global-seq vs per-stream resume; "job runs while away" vs
"pause when buffer full; "PC-side approval" vs "user is away"). Each: **Flaw →
Fix → Applied**. New PoC checks back the reliability items (see RESULTS).

## Security & authorization

### S26 [BLOCKER] Remote-exec approval assumed someone is at the PC
- **Flaw:** S4 mandated "explicit PC-side approval for exec." But the entire premise is the user is *away* from the home PC — nobody is there to click Approve, so either exec is impossible (defeats "fix it from my phone") or approval is theatre.
- **Fix:** for remote sessions, an exec/destructive op is authorized only when **all** hold: session is in `exec` scope (granted earlier by a biometric-gated action), the op is **per-op biometric-confirmed on the phone**, and it matches a **per-project command allowlist** (else a one-time biometric override). The IDE prompt is *additional* only when a human is present at the PC. Every exec hits a tamper-evident audit log.
- **Applied:** 02 section 8, 09 section 3, 10 section 5, 03 section 4 (RCE row), 08 section 10 (AuditLog).

### S27 [BLOCKER] Capability scope was client-asserted
- **Flaw:** 04 said the `rpc` frame "carries a capability scope" — inviting an implementation that trusts the phone's own claim of authority.
- **Fix:** the granted scope is a **server-side session property**; the PC **classifies each inbound op** to a required capability and **rejects fail-closed** if it exceeds the grant. A scope tag in the frame is only a *request* that may trigger escalation — never a grant.
- **Applied:** 02 section 8, 04 section 2 (`rpc` row), 09 section 3.

### S28 [BLOCKER] Local inference worker is a second, unauthenticated entry point
- **Flaw:** `desktop/server.py` binds `0.0.0.0:8401` and enforces `X-Mias-Token` only if `MIAS_TOKEN` is set (default empty). Behind the Bridge it becomes an open `generate` (and future-tools) endpoint on the PC's LAN, bypassing all Bridge auth/scope/approval.
- **Fix:** the session server launches/uses the worker bound to **`127.0.0.1` with a random per-launch token**; the only remote path is the E2EE Bridge. (Also switch `verify_token` to a constant-time compare.)
- **Applied:** 02 section 2 (inference worker), 02 section 8.

### S29 [HARDEN] `GuardrailProcessor` mis-cited as a tool-input/command sanitizer
- **Flaw:** 02/03/09 + S4 listed "reuse `GuardrailProcessor` on tool inputs" as part of the RCE defense. The real class is an **NL keyword heuristic** for jailbreak/harmful *conversation* text (`evaluateInput`/`evaluateOutput`); it gives **zero** protection against `rm -rf`, path traversal, or a malicious build command — a false-confidence leak.
- **Fix:** scope it to free-text prompts and tool *output* fed back to the model; make the jail + server-side scopes + per-op approval + allowlist the actual exec/file controls.
- **Applied:** 02 section 8, 09 section 3, 03 section 4 (prompt-injection row).

### S30 [HARDEN] Relay slot reclaim was unauthenticated
- **Flaw:** the relay reclaims a slot for any reconnecting peer presenting the `rendezvous_id` (last-writer-wins). Anyone who knows a live `rendezvous_id` can evict the real peer (forced-disconnect DoS).
- **Fix:** the relay issues an **opaque slot token** on first attach; reclaim requires it; rate-limited. (E2EE still prevents reading; this stops eviction.)
- **Applied:** 02 section 2 (relay), 03 section 4.

### S31 [HARDEN] Burn-on-fail conflated pairing with established session
- **Flaw:** the PoC ends the host on the *first* undecryptable frame ("attempt-burn"). Correct for a wrong pairing code; catastrophic mid-session — one injected/corrupt/reordered-across-rekey frame becomes a trivial DoS that kills any live session.
- **Fix:** burn-on-fail is **pairing-phase only**; an established-session AEAD-open failure is **dropped + counted**, and only a *sustained* rate triggers rekey/reconnect — never a silent teardown.
- **Applied:** 04 section 2, PoC comment/behavior (host distinguishes handshake vs session).

### S32 [HARDEN] Cleartext `device_key` / unbound version offers
- **Flaw:** the lifecycle diagram showed `ctrl:hello{device_key}` as the first frame and version negotiation outside the bound transcript — a stable device pubkey to the relay enables cross-session correlation (defeats S5), and an unbound version offer invites a downgrade by a malicious relay.
- **Fix:** only `rendezvous_id` (+ slot token) is relay-facing cleartext; `device_key`/`resume_token` ride **inside AEAD**; protocol-version offers are **bound into the PAKE transcript** so key-confirmation covers them.
- **Applied:** 04 section 3, 03 section 1a (transcript), 03 section 1d.

### S33 [HARDEN] Rotating `rendezvous_id` has an unaddressed clock-sync dependency
- **Flaw:** saved-device reconnect derives the `rendezvous_id` *offline* on both ends; if time-based, clock skew (S20) makes phone and PC compute different ids — and S20's "server-issued TTL" cannot help because there is no connection yet.
- **Fix:** derive from a **coarse time bucket** (e.g. 1h); the PC **registers under several adjacent buckets**; the phone **tries a small window**. Document the skew tolerance explicitly as the one TTL that is computed offline.
- **Applied:** 03 section 1d.

## Protocol & reliability

### S34 [BLOCKER] PoC "resume" regenerated tokens instead of replaying — masked the real bug
- **Flaw:** the host's resend `buf` was built but never read; resume rewound a cursor and **regenerated** deterministic `tok{i}` text. Real model output is **not reproducible**, so this hid the exact failure mode resume must handle.
- **Fix:** resume **replays the exact persisted frames** from the buffer; on eviction it serves from the **persisted session log**; it must never regenerate. PoC rewritten to replay from `buf`/log and to fail loudly if a needed seq is unavailable.
- **Applied:** 04 section 5, 08 section 4, PoC `pc_host` (replay-from-buffer), RESULTS.

### S35 [BLOCKER] Global seq + single cumulative ack contradicts per-stream resume & multi-channel
- **Flaw:** S16 ("global per-session seq") + the frame's single cumulative `ack` collide with S17/section 3 (independent interactive vs bulk streams) and per-stream resume: one global cumulative ack stalls pruning of fast interactive frames behind a slow bulk stream, and forces replay of already-rendered frames.
- **Fix:** split the concerns — **`frame_id`** (session-global, monotonic) for **transport-overlap dedup only**; **`stream_id`+`seq`** for ordering/resume; **per-stream ack cursors**. The dedup set keys on `frame_id`; cursors are per `stream_id`.
- **Applied:** 04 section 2 (frame shape), 08 section 4.

### S36 [BLOCKER] "Job keeps running while away" contradicted "pause generation when buffer full"
- **Flaw:** S13 pauses generation when the buffer fills / no consumer. For a phone that's gone for the whole job (the core use case), the buffer fills almost immediately, so the job **stalls** — directly contradicting "start a long job, lock the phone, … job never died."
- **Fix:** distinguish **attached-but-slow** (apply backpressure: throttle/pause) from **detached** (spill evicted frames to a **persisted session log** and keep generating to completion, bounded by detach TTL / disk budget — never pause).
- **Applied:** 04 section 3/section 5, 08 section 7, 06 P2 exit test.

### S37 [HARDEN] "Two physical connections" fought the one-slot relay model
- **Flaw:** 08 section 3 wants two connections (interactive/bulk) to beat TCP HOL, but S1/section 2 enforces one peer per `rendezvous_id`, so a 2nd socket would *reclaim* (close) the 1st. 04 section 6 separately described single-socket multiplexing — the two docs disagreed.
- **Fix:** the relay routes **labeled channels** under one `rendezvous_id` (`role`+`channel`); a single multiplexed socket can only do app-level fairness, not defeat TCP HOL. 08 section 3 is canonical; 04 section 6 now agrees.
- **Applied:** 02 section 2 (relay), 04 section 6, 08 section 3.

### S38 [HARDEN] Make-before-break is impossible relay↔relay
- **Flaw:** "keep the standby warm, overlap renders once" cannot hold on the relay path — a 2nd relay socket evicts the 1st (reclaim). Overlap only works between *different* tiers.
- **Fix:** make-before-break is **cross-tier only** (e.g. relay warm while P2P primary); a same-tier relay reconnect is **break-before-make**, recovered losslessly by the resume cursor.
- **Applied:** 08 section 2.

### S39 [BLOCKER] Idempotency key lifecycle underspecified across restarts
- **Flaw:** "idempotency keys on side-effecting rpc" never said *when/where* the key is minted or that the PC's dedup memory persists. If the key is minted at send time (post-restart) or the PC forgets executed keys on restart, a resend double-runs a build/write.
- **Fix:** the **phone mints the key at compose time and stores it in the durable outbox** (so a post-kill resend carries the same key); the PC's **idempotency cache persists for the session** (in the job registry) so a PC restart still dedups.
- **Applied:** 04 section 2 (`rpc` row), 02 section 8, 08 section 10, ties M14.

## Product / privacy

### Q5 [HARDEN] Background push expands the PC's outbound allowlist beyond "exactly one relay"
- **Flaw:** Q4/11-C add APNs / UnifiedPush·ntfy for wake, but 06 Targets and 03 section 5 still assert "exactly 1 new outbound host (the relay)." The PC posting a wake to a push gateway is a *second* outbound host — a privacy-invariant contradiction.
- **Fix:** state the real allowlist: **relay + (when push is on) the push gateway** (self-hostable for UnifiedPush/ntfy; APNs content-free). "Exactly one host" holds only with push off. Phone hands its push token to the PC over E2EE; the gateway sees only `(token, wake)` metadata.
- **Applied:** 04 section 4, 03 section 5; 06 Targets note.

### Q6 [HARDEN] Memory conflict resolution used wall-clock LWW
- **Flaw:** Q2's "per-field last-writer-wins" relies on timestamps; device clocks skew (S20), so LWW can silently drop the wrong edit.
- **Fix:** per-field **logical clock (Lamport / version vector)**, not wall-clock; visible resolver for true clashes. Confirms source-of-truth: project = PC, personal = phone (open decision 4).
- **Applied:** 09 section 5.

## Mobile (current app reality)

### M14 [BLOCKER] Offline outbox is not durable (`OperationQueue` is in-memory)
- **Flaw:** `OperationQueue` documents "operations do not survive a process restart." 08 section 5 / 11-M7 lean on it for the offline outbox and claim "two-way durability." A backgrounded app is routinely OS-killed (exactly the SUSPENDED state), so a queued instruction is **lost** before reconnect.
- **Fix:** a **disk-persisted encrypted outbox** `{idempotency_key, ciphertext, created_at, status}` is the source of truth; `OperationQueue` is only the in-memory dispatcher on top. Replay un-acked entries by key on reconnect (ties S39).
- **Applied:** 11 section A/section B (M7, M14), 08 section 5/section 10.

### M15 [HARDEN] Biometric is a UI gate, not a key binding
- **Flaw:** `BiometricGate` returns a boolean and `ZkVault`'s MasterKey is not `setUserAuthenticationRequired`, so device keys unlock with the *app process*, not the user's biometric. "Stolen phone gets nothing — gated by BiometricGate before keys unlock" overstates this.
- **Fix:** store saved-device keys as Keystore keys with `setUserAuthenticationRequired(true)` and unlock via a `BiometricPrompt.CryptoObject`, making the guarantee cryptographic.
- **Applied:** 03 section 1d/section 3, 11 section B (M15).

### M16 [HARDEN] Connection health conflates device-offline with link-dead
- **Flaw:** `isDesktopReachable` is "configured." A correct state machine needs two distinct signals with different recovery: *device has no network* (`ConnectivityMonitor`, exists) → wait; *link/peer dead but device online* (heartbeat, new) → race transports now.
- **Fix:** feed both into the state machine; don't collapse them into one boolean.
- **Applied:** 08 section 5, 11 section B (M16).

## UX

### U2 [HARDEN] No phone screens for approval / scope-escalation / denial
- **Flaw:** 10 covered degraded/offline/reconnecting but not the approval round-trips the protocol can be in — scope-escalation requested, per-op approval pending, denied/timed-out, "needs your input." A protocol state with no screen is an unreachable, broken flow.
- **Fix:** add those states to the UX (calm, recoverable; granting is biometric-confirmed; `notify` deep-links to the awaiting prompt).
- **Applied:** 10 section 5.

## Fourth-pass build-checklist deltas (fold into the section above)
7. **Server-side authority:** classify+enforce scope on the PC; remote-exec = scope + per-op biometric + allowlist; localhost-only worker; audit log. *(S26,S27,S28)*
8. **Protocol counters:** `frame_id` (transport dedup) vs per-stream `seq`/ack; replay from buffer/log, never regenerate. *(S34,S35)*
9. **Detached jobs:** spill to persisted log, don't pause; idempotency key minted+persisted by phone, dedup cache persisted on PC. *(S36,S39)*
10. **Durable outbox** on disk; biometric-bound keys; dual health signals. *(M14,M15,M16)*
11. **Allowlist truth:** relay (+ push gateway when push on, + cloud host when opt-in). *(Q5)*
