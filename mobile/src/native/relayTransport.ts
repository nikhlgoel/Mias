/**
 * RelayBridgeTransport — the T1 relay transport for the phone (bridge/docs/02).
 *
 * A RawTransport over React Native's built-in WebSocket: it registers with the
 * rendezvous relay (JSON hello, consumed internally) and then carries opaque
 * binary peer frames for a BridgePeer. RN-safe (no node deps).
 *
 * REMAINING NATIVE PIECE (bridge/docs/03 section 2, 05 section 2): a BridgePeer on
 * the phone needs a `CryptoProvider` (X25519 / AES-256-GCM / HKDF), which RN does
 * not provide in JS. That's a small native crypto module (Android Keystore-backed
 * on-device; e.g. a Conscrypt/BoringSSL bridge) — the one thing between this
 * transport and a live phone↔PC session. The session server + relay already run
 * this exact protocol (see /session-server e2e tests).
 */
import type { RawTransport } from '@mias/bridge-protocol';

export interface RelayHello {
  rid: string;
  role: 'host' | 'client';
  channel?: string;
  slotToken?: string;
}

export interface RelayTransportResult {
  transport: RawTransport;
  slotToken: string;
}

/** Dial the relay and resolve a RawTransport once registered. */
export function connectRelayTransport(relayUrl: string, hello: RelayHello): Promise<RelayTransportResult> {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(relayUrl);
    (ws as unknown as { binaryType: string }).binaryType = 'arraybuffer';
    let registered = false;
    let messageCb: ((data: Uint8Array) => void) | null = null;
    let closeCb: (() => void) | null = null;

    ws.onopen = () => ws.send(JSON.stringify(hello));

    ws.onmessage = (ev: WebSocketMessageEvent) => {
      if (!registered) {
        try {
          const ack = JSON.parse(String(ev.data)) as { type: string; slotToken?: string };
          if (ack.type === 'relay-ack') {
            registered = true;
            resolve({
              slotToken: ack.slotToken ?? '',
              transport: {
                send: (bytes: Uint8Array) => ws.send(bytes.buffer as ArrayBuffer),
                onMessage: cb => { messageCb = cb; },
                onClose: cb => { closeCb = cb; },
                close: () => ws.close(),
              },
            });
          }
        } catch {
          reject(new Error('bad relay ack'));
          ws.close();
        }
        return;
      }
      const data = ev.data;
      if (data instanceof ArrayBuffer) messageCb?.(new Uint8Array(data));
    };

    ws.onclose = () => {
      if (!registered) reject(new Error('relay closed before ack'));
      closeCb?.();
    };
    ws.onerror = () => {
      if (!registered) reject(new Error('relay connection error'));
    };
  });
}
