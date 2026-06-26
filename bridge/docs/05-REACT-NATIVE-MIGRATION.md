# 05 — React Native / iOS Migration Impact

The team will migrate the current Kotlin/Android app to **React Native** (for iOS
support and shared code) **before** building the Bridge client. This doc records
how the two efforts affect each other so neither paints the other into a corner.

## 1. Direction of influence

- **Bridge design → RN:** choose Bridge transports that exist cleanly in RN so the
  remote client is write-once for Android + iOS.
- **RN migration → Bridge:** the Bridge client is **new code**, so build it
  RN-native from day one; don't port the Kotlin `McpClient`. Only the PC side
  (extension/relay/worker) stays non-RN.

## 2. What stays, what moves

| Layer | Today (Kotlin) | After migration | Bridge implication |
|---|---|---|---|
| Mobile UI/app | Compose | React Native (TS) | Bridge **client** = TS module in the RN app |
| Bridge transport T1 | — | RN WebSocket (built-in) | **No native module needed**; works iOS+Android |
| Bridge transport T2 (P2P) | — | `react-native-webrtc` (M124, iOS+Android) | Native module; add in the P2P phase only |
| Crypto (SPAKE2+, AEAD) | — | JS lib or thin native module | Keep in a TS `crypto` boundary so it's swappable |
| Secure key storage | Android Keystore | RN secure storage → Keystore / iOS Secure Enclave | Same key hierarchy (03) on both OSes |
| On-device inference | llama.cpp JNI, AI Edge | Native modules (unchanged, Android); iOS later | Independent of Bridge |
| PC side | Python/FastAPI + TS extension | unchanged (not RN) | Bridge **server** is editor/desktop code, not RN |

## 3. Design rules that keep the Bridge RN-portable

1. **MVP transport = plain WebSocket** (T1). It needs zero native code, so the
   remote feature can ship on iOS the moment the RN app exists.
2. **Abstract the transport** behind a small TS interface
   (`BridgeTransport { send, onMessage, close }`) with three impls (WSS, WebRTC,
   Tailscale). The protocol layer (04) never knows which is active.
3. **JSON payloads, binary frames optional.** JSON is trivial in RN; if we later
   want binary AEAD frames, RN supports `ArrayBuffer` over WebSocket.
4. **Mind mobile background limits.** iOS kills held sockets in background; rely on
   the resume protocol (04 section 5) + push-to-wake (04 section 4), not a permanently open
   socket. This is a *protocol* guarantee, so it's OS-agnostic.
5. **No browser-storage assumptions.** Use RN secure storage for keys; never
   localStorage-style persistence for secrets.

## 4. Sequencing recommendation

Do the RN migration of the **Android app core (chat/offload UI)** first and prove
the existing LAN MCP offload still works through an RN `BridgeTransport(WSS-to-LAN)`
shim. That single step de-risks both projects: it validates the RN transport
abstraction **and** gives the Bridge its first client surface, before any relay
exists. Then layer relay → pairing → P2P per the roadmap (06).

## 5. iOS-specific watch-items

- Background execution: use push notifications to wake; design every long job to
  survive the phone being asleep (the PC holds it anyway).
- WebRTC permissions/entitlements differ on iOS17+/Android14 — only relevant in
  the P2P phase.
- Secure Enclave key types differ from Android Keystore — keep the key API
  abstract (03 section 2) so platform differences stay in one module.
