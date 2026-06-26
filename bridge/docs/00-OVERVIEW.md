# 00 — Overview

## 1. The problem we're solving

Today Mias can offload heavy inference to a desktop **only on the same network**:
the user types the desktop's Tailscale/LAN IP into Settings and the phone POSTs
MCP JSON-RPC to `http://<ip>:8401/rpc` (`core/network/mcp/McpClient.kt`,
`desktop/server.py`). The Android Tailscale auto-discovery path is non-functional,
so it is manual-IP, single-shot, request/response, LAN-bound.

What we want instead:

> Keep the PC running at home. Walk into college. From the phone — wherever it is,
> on cellular or campus Wi-Fi, behind any NAT — **start/continue work on the PC,
> push new instructions, and see live updates stream back**, and have it feel smooth.

The PC side is delivered as an **IDE extension** (VS Code first; people connect
whichever editor they use). Later we build our own IDE on the same protocol.

## 2. Goals

- **G1 — Works regardless of network or distance.** No port-forwarding, no static
  IP, no router config, no requiring the user to run a VPN. Cellular ↔ home
  broadband must work.
- **G2 — Smooth.** Streaming output (tokens, logs, file diffs) must flow at the
  rate it is produced; distance may delay the *start* of a response but not the
  *flow* of it.
- **G3 — Easy pairing.** Connect with a **6-char code**, a **link/QR**, or a
  **one-tap saved device**. No accounts required for the basic path.
- **G4 — Privacy preserved.** Mias's identity is "no third party ever reads your
  data." The remote link must be **end-to-end encrypted**; any relay we use must
  be a blind forwarder, and ideally self-hostable.
- **G5 — Live, bidirectional control.** The phone can send new prompts/commands
  mid-job; the PC can push progress, partial results, and status unprompted.
- **G6 — Survives mobile reality.** Cellular drops, app backgrounding, and network
  flips must reconnect and **resume** without losing the running job.
- **G7 — Future-proof for RN/iOS.** Transport must be implementable in React
  Native for both Android and iOS (the planned migration).

## 3. Non-goals (for the first versions)

- Not a general remote-desktop / screen-share tool. We move **structured Mias
  work** (prompts, tool calls, model output, file/build events), not pixels.
- Not multi-user collaboration. One user, their own devices.
- Not a public cloud service. The relay is infrastructure-minimal and self-hostable;
  it never holds plaintext or model data.
- Not replacing on-device inference. The Bridge is the *offload + remote-control*
  path; the phone still runs local models when appropriate.

## 4. How this fits what already exists

| Existing piece | Role in the Bridge |
|---|---|
| `desktop/server.py` (FastAPI + MCP) | Becomes the **inference worker** the IDE-extension session server talks to locally. Reused, not thrown away. |
| MCP 2025-03-26 JSON-RPC + `X-Mias-Token` | Reused as the **payload** semantics. The Bridge adds a persistent, bidirectional, E2EE **transport** around MCP messages and extends it for live push. |
| `McpClient.kt` | Evolves into a transport-agnostic **Bridge client** (relay/P2P) instead of a direct LAN HTTP client. |
| `TailscaleMeshClient.kt` | Demoted to an **optional power-user transport** (see 02-ARCHITECTURE section 6). Not the default, because it requires installing+logging into Tailscale on both devices. |
| Host allowlist / no-cloud policy | Extended to allow exactly one new host: the user's chosen **relay** (default self-host, or a Mias-run blind relay). |

## 5. Glossary

| Term | Meaning |
|---|---|
| **Bridge** | The whole mobile↔PC remote system described in this folder. |
| **Session server** | The process the IDE extension starts on the PC. Holds the job, talks locally to the inference worker, dials out to the relay. |
| **Relay (rendezvous)** | Tiny public service both sides dial out to; matches them by code and forwards opaque ciphertext. Zero-knowledge. |
| **Pairing code** | Short (6-char) human-typeable secret shown on the PC, entered/scanned on the phone; seeds the E2EE key. |
| **Saved device** | A PC the phone has paired with before; reconnect is one tap via stored device keys. |
| **P2P upgrade** | Optional direct phone↔PC WebRTC connection negotiated through the relay to drop the relay hop. |
| **Live update** | A server-initiated push (progress, partial output, file/build event) the phone renders without polling. |
