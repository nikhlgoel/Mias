/**
 * BridgeChatClient — the phone side of a live Bridge session (Bridge P1).
 *
 * Assembles the RN relay transport + the shared `BridgePeer` + the pure-JS
 * `nobleCryptoProvider` + CPace PAKE (all from @mias/bridge-protocol) into a
 * "pair with my PC and stream a turn" client. No native crypto module needed —
 * @noble runs in Hermes (randomness via the get-random-values polyfill in
 * index.js). This is the exact protocol the /session-server runs and the e2e
 * tests prove.
 */
import {
  BridgePeer,
  Streams,
  nobleCryptoProvider,
  type OutgoingFrame,
} from '@mias/bridge-protocol';
import { connectRelayTransport } from './relayTransport';

/** A pasted/scanned pairing payload from the PC extension. */
export interface PairingInfo {
  relayUrl: string;
  rendezvousId: string;
  code: string;
}

/**
 * Parse the PC extension's pairing link:
 *   https://pair.mias.app/v1#mias1:<rid>.<code_b64url>.<relayHost_b64url>.<exp>
 * or the bare `mias1:...` form. Returns null if malformed/expired.
 */
export function parsePairingLink(input: string): PairingInfo | null {
  const raw = input.trim();
  const marker = raw.indexOf('mias1:');
  if (marker < 0) return null;
  const body = raw.slice(marker + 'mias1:'.length);
  const parts = body.split('.');
  if (parts.length < 4) return null;
  const [rid, codeB64, relayB64, expStr] = parts;
  if (!rid || !codeB64 || !relayB64) return null;
  const exp = Number.parseInt(expStr ?? '0', 10);
  if (Number.isFinite(exp) && exp > 0 && exp * 1000 < Date.now()) return null; // expired
  try {
    const code = b64urlDecode(codeB64);
    const relayHost = b64urlDecode(relayB64);
    const relayUrl = relayHost.startsWith('ws') ? relayHost : `wss://${relayHost}`;
    return { relayUrl, rendezvousId: rid, code };
  } catch {
    return null;
  }
}

export interface BridgeChatEvents {
  onDelta: (text: string) => void;
  onDone: () => void;
  onError: (message: string) => void;
}

export class BridgeChatClient {
  private peer: BridgePeer | null = null;
  private events: BridgeChatEvents | null = null;

  get isConnected(): boolean {
    return this.peer?.established ?? false;
  }

  /** Dial the relay and complete CPace pairing with the PC. */
  async connect(info: PairingInfo): Promise<void> {
    const { transport } = await connectRelayTransport(info.relayUrl, {
      rid: info.rendezvousId,
      role: 'client',
      channel: 'c1',
    });
    const peer = new BridgePeer(transport, nobleCryptoProvider, 'client', {
      rendezvousId: info.rendezvousId,
      relayOrigin: info.relayUrl,
      code: info.code,
    });
    peer.onFrame(frame => this.onFrame(frame));
    peer.onError(err => this.events?.onError(err.message));
    await peer.connect();
    this.peer = peer;
  }

  /** Send a prompt to the PC; streamed tokens arrive via `events.onDelta`. */
  send(prompt: string, events: BridgeChatEvents): void {
    if (!this.peer?.established) {
      events.onError('Not paired with a PC yet.');
      return;
    }
    this.events = events;
    this.peer.send(Streams.control, 'rpc', { method: 'generate', params: { prompt }, id: Date.now() });
  }

  private onFrame(frame: OutgoingFrame): void {
    if (frame.streamId === Streams.tokens && frame.type === 'stream') {
      this.events?.onDelta((frame.payload as { chunk: string }).chunk);
    } else if (frame.type === 'rpc_result') {
      const p = frame.payload as { error?: { message: string } };
      if (p.error) this.events?.onError(p.error.message);
      else this.events?.onDone();
    }
  }

  close(): void {
    this.peer?.close();
    this.peer = null;
  }
}

function b64urlDecode(s: string): string {
  const b64 = s.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(s.length / 4) * 4, '=');
  const bin = atob(b64);
  let out = '';
  for (let i = 0; i < bin.length; i++) out += String.fromCharCode(bin.charCodeAt(i));
  return decodeURIComponent(escape(out));
}
