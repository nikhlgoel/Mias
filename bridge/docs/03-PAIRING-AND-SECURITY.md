# 03 — Pairing & Security

Goal: connect a phone to a PC with a **short code, a link/QR, or one tap on a
saved device**, while keeping the channel **end-to-end encrypted**, **forward
secret**, and the relay **unable to read, route-hijack, or impersonate**
anything. Incorporates the 2026-06-26 review ([07](07-THREAT-REVIEW-AND-FIXES.md)).

## 0. Two identifiers — never conflate them (07-S1)

| Identifier | Who sees it | Purpose |
|---|---|---|
| **`rendezvous_id`** (high-entropy, ≥128-bit) | the **relay** (for routing) | matches the two sockets; meaningless without keys |
| **pairing code** (short, human) | the **two devices only** | seeds the PAKE; **never sent to the relay** |

The relay routes on `rendezvous_id` and can still read nothing. The code lives
only inside the PAKE on the endpoints.

## 1. Three ways to connect

### 1a. Pairing code (first-time, manual)
1. IDE extension → **Start session**: the session server dials the relay over
   `wss://`, gets a `rendezvous_id`, and shows a **code** + countdown.
   Code format: **Crockford Base32, ambiguity-free, ≥ ~30 bits (7 chars)**,
   **TTL ≤ 5 min**, **single active use** (07-S3).
2. Phone: **Connect → enter code**.
3. Both run a **balanced PAKE (CPace, or balanced SPAKE2)** with the code as the
   shared secret (07-S2). Balanced — not SPAKE2+ — because both sides hold the
   code symmetrically. The transcript is **channel-bound** to `rendezvous_id` +
   both ephemeral public keys + relay origin **+ the Bridge/MCP protocol-version
   offers** (so a relay can't force a version downgrade — the offers are covered by
   key confirmation, 07-S32), and ends with an explicit **key-confirmation** step.
4. **Forward secrecy:** mix the PAKE output with an **ephemeral X25519 ECDH** to
   derive per-session keys; discard ephemerals after (07-S7).
5. **Burn on failure:** one failed key-confirmation **burns the code** and the
   `rendezvous_id`; relay + host **rate-limit/lockout** further attempts (07-S3).
6. On success the phone offers **"Remember this PC"** (→ 1d).

> An online attacker therefore gets **exactly one guess** per code, against an
> ephemeral, channel-bound exchange — not an offline-crackable hash.

### 1b. QR / link (first-time, one-tap) — **PC shows, phone scans**
**Direction matters (07-S22):** the **PC extension displays the QR**; the **phone
scans it with its camera**. Phones aim easily; many desktops/laptops have no
webcam or only a front-facing one, so asking the PC to scan is wrong. The phone's
"Connect" screen is therefore a **camera viewfinder + "enter code" fallback**, not
a QR to be photographed.

- The PC also offers a **copyable https link** (verified Universal/App Link, not a
  custom `mias://` scheme — 07-S9) for the rare case the phone can't scan (e.g.
  sent to the same phone over the user's own channel). QR is primary.
- **Mutual confirmation (07-S23):** after a scan, the **PC shows "phone *X* wants
  to connect — Approve?"** so a phished QR can't silently pair the user's phone to
  a stranger's PC. The PAKE still runs on the code embedded in the QR.

### 1c. Secure QR specification
**Payload (small, ~60–120 bytes):**
```
mias1:<rendezvous_id b64url>.<code b64url>.<relay_host>.<exp_unix>.<sig?>
```
- `rendezvous_id`: ≥128-bit routing id (relay-facing).
- `code`: the PAKE secret (≥30-bit), **one-shot**, TTL ≤ 5 min (`exp_unix`).
- `relay_host`: advisory only — the app **validates it against its configured/
  allowlisted relay set** and ignores/queries the user on mismatch (07-S24:
  prevents a malicious QR pointing the phone at an attacker relay). E2EE + PAKE
  still protect content regardless.
- Encoded as a **verified https Universal/App Link** so the same string works as a
  scannable QR *and* a tappable link.

**QR rendering (PC side):** error-correction **level Q (25%)** for resilience to
screen glare/partial occlusion; automatic version for the payload size; a full
**quiet zone (≥4 modules)**; **high contrast** (respect the extension theme but
keep ≥4.5:1); a **minimum on-screen size** (~220px) so phones lock focus quickly;
a centered Mias glyph is optional but must not exceed the ECC budget. If the code
**rotates** (TTL refresh), the QR **re-renders live** with the countdown.

**Scan UX (phone side):** a viewfinder with an alignment frame, torch toggle,
haptic + checkmark on decode, and an always-present "enter code manually" link.
Camera-permission denial **degrades gracefully to manual entry** (07-S25) — pairing
never *requires* the camera.

### 1d. Saved device (daily driver, one tap)
First successful pair exchanges and **pins long-term device public keys** (stored
encrypted on each side). Reconnect later **without exposing any directory**
(07-S5):
1. Phone recomputes a **rotating `rendezvous_id` derived from the per-pair shared
   secret** — only the paired phone can compute the PC's current value; the relay
   holds **no pubkey→session map**.
2. **Clock-skew tolerance (07-S33).** The rotation input is a **coarse time bucket**
   (e.g. 1-hour windows) since there is *no connection yet* to issue/sync a server
   TTL. The PC **registers under several adjacent buckets at once**, and the phone
   **tries a small window** (bucket N, N±1) so a modestly-skewed clock still finds
   the PC. This is the one TTL that cannot rely on the "server-issued TTL" rule
   (08 section 9) because it is computed offline on both ends.
3. Phone and PC mutually authenticate via **pinned device keys**
   (challenge-response over the encrypted channel), rate-limited. The stable
   `device_key` is never sent to the relay in cleartext (07-S32).
4. Fresh per-session keys via X25519 ECDH (forward secrecy). One tap, fully
   authenticated, no code. On the phone, the pinned keys are **unlocked by a
   biometric `CryptoObject`, not merely after a biometric prompt** (07-M15) — see
   11 section B.

## 2. Key hierarchy

| Key | Lifetime | Purpose |
|---|---|---|
| Pairing code | one session, ≤5 min, one-shot | seeds the balanced PAKE (first contact only) |
| PAKE output | per pairing | authenticates the channel from the code |
| Device identity keypair (each side) | long-term, in secure storage | authenticates "saved device" reconnects |
| Ephemeral X25519 (each side, per session) | per connection | ECDH → forward-secret session keys |
| Session keys (send/recv) | per connection, rekeyed on budget | AEAD encrypt all frames |

**AEAD (07-S6):** **XChaCha20-Poly1305 (192-bit nonce)** or AES-256-GCM with
**counter nonces per (key, direction)** — never random GCM nonces over a long
stream. **Rekey** on a byte/frame/time budget.

Storage: phone → platform keystore (Android Keystore / iOS Secure Enclave via the
RN secure-storage layer); PC → OS keychain / DPAPI via the extension. Mirrors
Mias's existing on-device-encryption stance.

## 3. What each party can and cannot do

| Party | Can | Cannot |
|---|---|---|
| **Relay** | see `rendezvous_id`, ciphertext sizes/timing; match two sockets | read content; forge/inject; route a third party in (one host+one client); hold an identity→session directory |
| **Network attacker** | see that someone uses the relay | read content (E2EE over TLS); replay (nonce+seq); MITM (channel-bound PAKE + pinned keys) |
| **Online guesser** | try a code once | a second guess (one-shot + burn + rate-limit) |
| **Stolen phone** | nothing pre-unlock | use saved devices — pinned keys are **bound to a biometric `CryptoObject`** (Keystore `setUserAuthenticationRequired`), not merely behind a UI prompt (07-M15) |

**Residual (07-S10):** the relay still learns metadata (who↔who, timing, sizes).
Mitigations: **self-host the relay**, or use **Tailscale (T3, no relay at all)**;
optional padding/cover traffic later.

## 4. Threat model & mitigations

| Threat | Mitigation |
|---|---|
| Relay compromised/malicious | zero-knowledge by construction; routes on `rendezvous_id`; self-hostable |
| Route hijack at relay (07-S1) | `rendezvous_id` ≠ code; one host + one client; E2EE fails closed on mismatch |
| Code shoulder-surfed / guessed (07-S3) | ≥30-bit Base32, TTL, one-shot, burn-on-fail, rate-limit |
| MITM / UKS during first pair (07-S2) | balanced PAKE + channel binding + key confirmation |
| Nonce reuse (07-S6) | XChaCha20 / counter nonces + rekey |
| Past-traffic decryption after key leak (07-S7) | per-session ephemeral ECDH (forward secrecy) |
| Saved-device enumeration (07-S5) | rotating derived `rendezvous_id`; no relay directory; authed reconnect |
| Plaintext downgrade (07-S8) | `wss://` only; **fail closed** if verification fails |
| Remote tool RCE (07-S4) | server-side scope enforcement (never client-asserted, S27) + path jail + per-op biometric approval that works with nobody at the PC (S26) + per-project allowlist (see 02 section 8) |
| Second LAN entry point behind the Bridge (07-S28) | session server binds the inference worker to `127.0.0.1` + random per-launch token |
| Relay slot eviction by `rendezvous_id` knowledge (07-S30) | reclaim requires a relay-issued opaque slot token; rate-limited |
| Mid-session DoS via one bad frame (07-S31) | AEAD-open failure drops the frame (burn-on-fail is pairing-phase only) |
| Replay of side-effecting ops (07-S12) | idempotency keys (phone-minted, outbox-persisted) deduped by the session server across restarts (S39) |
| Deep-link hijack (07-S9) | verified Universal/App Links; QR preferred; one-shot code |
| Prompt injection via tool output | `GuardrailProcessor` (an **NL content heuristic**, not a command/path sanitizer — S29) + observation truncation |
| Background-push metadata to a push gateway (07-Q5) | content-free wake only; self-hostable UnifiedPush/ntfy; gateway added to the allowlist when push is on |
| Phone lost | biometric-bound keys; **"forget device"** from the PC extension revokes the pinned key |

## 5. Privacy-policy reconciliation

Mias advertises "no cloud calls" + an outbound **host allowlist**. The Bridge adds
**one** new outbound host by default — the **relay** — and:
- the relay is a **blind forwarder** (ciphertext only; no prompts/code/output/model data ever readable);
- it is **self-hostable** (or skip it entirely with Tailscale T3);
- transports are **`wss://` and fail closed** — no plaintext path exists.

**Two opt-in features add a host each, and must be reflected in the allowlist +
copy when enabled:** (a) **background push** adds the **push gateway**
(APNs / self-hostable UnifiedPush·ntfy), content-free wake only (07-Q5);
(b) **cloud-API passthrough** adds the chosen provider host, off by default
(07-Q1, 09 section 1). With both off, the "exactly one host (the relay)" guarantee
holds. Update the in-app privacy copy to state each allowlisted host and that none
can read traffic, keeping "no third party reads your data" literally true.

## 6. Library notes

- **Balanced PAKE:** CPace (CFRG-selected balanced PAKE) or balanced SPAKE2;
  implementations in Rust/Go/JS. Use **SPAKE2+ only** if a future flow needs an
  asymmetric stored verifier. Matter uses SPAKE2+ for its (asymmetric)
  commissioning — good prior art for the mechanics, not the symmetry.
- **AEAD:** XChaCha20-Poly1305 (libsodium) preferred for big random nonces; else
  AES-256-GCM with counter nonces (matches Mias's AES-256-GCM at-rest choice).
- **ECDH:** X25519.
