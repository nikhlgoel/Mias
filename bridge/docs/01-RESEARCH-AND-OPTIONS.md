# 01 — Research & Options

This is the "why" behind the architecture. It compares every realistic way to
connect a phone to a home PC across the internet, evaluates the original idea,
and reports the container-test numbers that settled the decision.

## 1. The core insight that decides everything

The hard part is **NAT/firewall**: a home PC has no public address and won't
accept inbound connections. There are only three ways around this, and the
industry has converged on one for "just works" UX:

| Strategy | How it beats NAT | Cost |
|---|---|---|
| **Inbound** (open a port / forward / DDNS) | User configures the router | Fragile, insecure, impossible on CGNAT/campus/cellular. **Rejected.** |
| **Overlay VPN** (WireGuard/Tailscale) | Both devices join a private mesh | Powerful, but user must install + log into a VPN on both devices. Too heavy as the *default*. **Kept as optional.** |
| **Outbound + rendezvous relay** | The PC *dials out*; a relay both sides reach matches them | Works on every network with zero config. **Chosen default.** |

**Outbound + relay is exactly how VS Code Remote Tunnels and Cloudflare Tunnel
work.** VS Code: *"the host initiates an outbound connection to the tunnel
service, and the client attaches through that service after authentication …
without opening inbound firewall ports, a VPN, or dynamic DNS."* Cloudflare:
*"the daemon initiates outbound connections to the edge … the origin never
receives inbound connections."* This is the proven pattern for our exact problem.

## 2. Options compared

### A. Relay-always (WebSocket-over-TLS through a rendezvous server) — **MVP**
Both PC and phone hold an outbound WSS connection to the relay; the relay copies
bytes between them. Simple, works on 100% of networks, easy in RN (plain
WebSocket, no native module). Downside: every byte takes one extra hop, so it
adds a fixed latency and uses relay bandwidth. Our tests show this is **smooth
enough** because streaming pipelines (see section 4).

### B. P2P (WebRTC DataChannel, ICE/STUN, TURN fallback) — **upgrade**
After matching through the relay (used as the signaling channel), the two devices
try a **direct** connection via ICE/STUN. If direct fails (symmetric NAT, carrier
CGNAT — common on cellular), it falls back to a **TURN** relay anyway. Removes the
relay hop and lowers latency when it works. Cost: `react-native-webrtc` native
module, more moving parts, you still must run STUN/TURN. **Layer it on after the
relay path is solid; never ship without the relay fallback.**

### C. Overlay VPN (Tailscale/WireGuard, or self-hosted headscale) — **optional**
Gives real device-to-device IP connectivity over any network. Mias already has
`TailscaleMeshClient`. But it asks the user to install Tailscale and log in on
both devices — wrong for the "type a 6-char code" default. Keep it as a
**power-user / max-privacy** transport that, when present, the Bridge can prefer.

### D. Managed tunnel SaaS (ngrok/Cloudflare Tunnel as-is) — **rejected as core**
Fastest to demo, but it puts a third party in the data path and clashes with the
no-cloud privacy promise unless we add our own E2EE on top. Useful only as a
throwaway dev shortcut, not the product.

### Decision
**Default = A (relay) with E2EE, B (P2P) as an automatic latency upgrade, C
(Tailscale) as an optional advanced transport.** All three carry the *same*
encrypted Bridge protocol, so the app picks the best available path transparently.

## 3. Evaluating the original idea

> *"On PC, through the extension they start a session which starts a server.
> Connect from mobile using a 5–6 digit alphanumeric code, or a generated link,
> and for often-used devices click to reconnect to the latest session."*

**Verdict: the idea is right, with two important corrections.**

| Part of the idea | Assessment |
|---|---|
| Extension starts a session = starts a server | ✅ Correct. That server is the **session server** (02-ARCHITECTURE section 2). Key correction: it must **dial OUT to a relay**, not wait for the phone to dial in — otherwise it won't work off-LAN. |
| 5–6 char alphanumeric code | ✅ Good and keep it — but treat the code as the seed for a **SPAKE2+ key exchange**, not a password sent to a server. That gives E2EE and makes a short code safe against guessing (one online guess only). See 03. |
| Generated link / QR | ✅ Keep as a convenience that encodes the same code + session id in a deep link. |
| Saved/often-used devices, one-tap to latest session | ✅ Excellent — this is the daily-driver path. Correction: back it with a **stored device key-pair** (public-key pinning), so reconnect is both one-tap *and* cryptographically authenticated, not just a remembered code. |

Net: your mental model is sound; we're hardening *how* the code is used (PAKE +
device keys) and *who dials whom* (PC dials out).

## 4. Container / process test results (empirical)

A 3-party simulation (`../poc/relay_poc.py`) runs an independent **relay**,
**PC host**, and **mobile** client, with E2EE (AES-GCM; key via HKDF as a
stand-in for SPAKE2+) and an injected one-way WAN delay to emulate distance.
Full method + raw output: [../poc/RESULTS.md](../poc/RESULTS.md).

| Distance (1-way latency) | TTFT (first token) | Inter-token gap (median) | Stream integrity | Relay could read |
|---|---|---|---|---|
| LAN (~5 ms) | 11 ms | 1.6 ms | 200/200 in order | **0 frames** |
| National (~40 ms) | 82 ms | 1.6 ms | 200/200 in order | **0 frames** |
| Intercontinental (~150 ms) | 303 ms | 1.4 ms | 300/300 in order | **0 frames** |

**What this proves:**
1. **Distance hits TTFT (~2× one-way latency), not smoothness.** Once tokens
   start, they arrive at generation cadence because WebSocket frames pipeline —
   latency is a one-time offset, not a per-token tax. So "feels smooth from
   college" is achievable on the relay path alone; P2P is a *nice-to-have* that
   trims the startup delay.
2. **The relay is genuinely zero-knowledge.** It parsed **0** of the forwarded
   frames at every distance — it only ever sees ciphertext + a session id.
3. **Reconnect/resume works but needs an ack'd buffer.** The drop-mid-stream run
   reconnected and continued, but a naive "resend from N" loop dropped one token.
   → Design requirement captured: per-frame sequence + ack + bounded resend
   buffer on the session server (see 04-PROTOCOL section 5).

## 5. Sources

- VS Code Remote Tunnels — https://code.visualstudio.com/docs/remote/tunnels
- Diving into Microsoft's dev tunnels (InfoWorld) — https://www.infoworld.com/article/2336324/diving-into-microsofts-dev-tunnels.html
- Cloudflare Tunnel architecture — https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/
- Cloudflare Tunnel vs ngrok vs Tailscale — https://dev.to/mechcloud_academy/cloudflare-tunnel-vs-ngrok-vs-tailscale-choosing-the-right-secure-tunneling-solution-4inm
- WebRTC protocols (STUN/TURN/ICE) — https://developer.mozilla.org/en-US/docs/Web/API/WebRTC_API/Protocols
- WebRTC NAT traversal deep dive — https://akashsahani2001.medium.com/building-real-time-p2p-communication-a-deep-dive-into-webrtc-ice-stun-and-turn-e645492230c5
- SPAKE2+ (RFC 9383) — https://datatracker.ietf.org/doc/rfc9383/
- RustCrypto SPAKE2 — https://github.com/RustCrypto/PAKEs/tree/master/spake2
- MCP transports (Streamable HTTP) — https://modelcontextprotocol.io/specification/2025-11-25/basic/transports
- Why MCP moved SSE → Streamable HTTP — https://blog.fka.dev/blog/2025-06-06-why-mcp-deprecated-sse-and-go-with-streamable-http/
- react-native-webrtc — https://github.com/react-native-webrtc/react-native-webrtc
- awesome-tunneling (self-host relays: frp, rathole, Pangolin) — https://github.com/anderspitman/awesome-tunneling
