/**
 * Relay connection — a RawTransport (for BridgePeer) over a `ws` WebSocket to the
 * rendezvous relay. Handles the relay registration handshake (JSON, consumed
 * internally) and then surfaces only the opaque peer frames.
 */
import { WebSocket } from 'ws';
import type { RawTransport } from '@mias/bridge-protocol';

export interface RelayHello {
  rid: string;
  role: 'host' | 'client';
  channel?: string;
  slotToken?: string;
}

export interface RelayConnection extends RawTransport {
  slotToken: string;
}

/** Connect to the relay, register, and resolve a RawTransport for peer frames. */
export function connectToRelay(
  relayUrl: string,
  hello: RelayHello,
  WebSocketImpl: typeof WebSocket = WebSocket,
): Promise<RelayConnection> {
  return new Promise((resolve, reject) => {
    const ws = new WebSocketImpl(relayUrl);
    ws.binaryType = 'arraybuffer';
    let registered = false;
    let slotToken = '';
    let messageCb: ((data: Uint8Array) => void) | null = null;
    let closeCb: (() => void) | null = null;

    ws.on('open', () => ws.send(JSON.stringify(hello)));

    ws.on('message', (data: Buffer | ArrayBuffer, isBinary: boolean) => {
      if (!registered) {
        // First message is the relay-ack (JSON, non-binary).
        try {
          const ack = JSON.parse(data.toString()) as { type: string; slotToken?: string };
          if (ack.type === 'relay-ack') {
            registered = true;
            slotToken = ack.slotToken ?? '';
            resolve({
              slotToken,
              send: (bytes: Uint8Array) => ws.send(bytes),
              onMessage: cb => { messageCb = cb; },
              onClose: cb => { closeCb = cb; },
              close: () => ws.close(),
            });
          }
        } catch {
          reject(new Error('bad relay ack'));
          ws.close();
        }
        return;
      }
      // Peer frames are binary/opaque.
      const bytes = data instanceof ArrayBuffer ? new Uint8Array(data) : new Uint8Array(data);
      messageCb?.(bytes);
    });

    ws.on('close', () => {
      if (!registered) reject(new Error('relay closed before ack'));
      closeCb?.();
    });
    ws.on('error', err => {
      if (!registered) reject(err);
    });
  });
}
