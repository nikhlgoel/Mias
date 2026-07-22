/**
 * BridgePeer — one end of an E2EE Bridge session, transport-agnostic.
 *
 * Ties together the SecureChannel handshake (crypto.ts), the per-stream framing
 * (FrameSender/FrameReceiver), and a small binary envelope, over any RawTransport
 * (relay WSS, WebRTC, Tailscale). The session server (host), the phone (client),
 * and tests all use this same class — only the RawTransport differs.
 *
 * Envelope (peer↔peer, always binary so the relay stays zero-knowledge — a byte
 * tag prefix makes every frame un-parseable as JSON):
 *   [0x01] HELLO    — JSON HelloMsg (ephemeral pubkey + versions; not secret)
 *   [0x02] CONFIRM  — JSON ConfirmMsg (key-confirmation MAC)
 *   [0x03] DATA     — SecureChannel-sealed bytes of a JSON application frame
 */
import {
  SecureChannel,
  type ChannelRole,
  type CryptoProvider,
  type HandshakeContext,
  type Pake,
  utf8,
} from './crypto.ts';
import { CpacePake } from './cpace.ts';
import {
  FrameReceiver,
  FrameSender,
  type FrameType,
  type OutgoingFrame,
} from './index.ts';

export interface RawTransport {
  send(data: Uint8Array): void;
  onMessage(cb: (data: Uint8Array) => void): void;
  onClose(cb: () => void): void;
  close(): void;
}

const TAG_HELLO = 0x01;
const TAG_CONFIRM = 0x02;
const TAG_DATA = 0x03;

export interface BridgePeerOptions {
  pake?: Pake;
  versions?: number[];
}

export class BridgePeer {
  private readonly transport: RawTransport;
  private readonly channel: SecureChannel;
  private readonly sender = new FrameSender();
  private readonly receiver = new FrameReceiver<OutgoingFrame>();
  private frameCb: ((frame: OutgoingFrame) => void) | null = null;
  private errorCb: ((err: Error) => void) | null = null;
  private establishedResolve: (() => void) | null = null;
  private establishedReject: ((e: Error) => void) | null = null;
  private sentConfirm = false;
  private openFailures = 0;

  established = false;

  constructor(
    transport: RawTransport,
    crypto: CryptoProvider,
    role: ChannelRole,
    ctx: HandshakeContext,
    opts: BridgePeerOptions = {},
  ) {
    this.transport = transport;
    // Default to the real CPace PAKE, bound to the stable shared session id
    // (rendezvous_id ‖ relay origin — both ends derive it identically).
    const sid = utf8(`${ctx.rendezvousId}|${ctx.relayOrigin}`);
    const pake = opts.pake ?? new CpacePake(crypto, ctx.code, sid);
    this.channel = new SecureChannel(crypto, role, ctx, pake, opts.versions);
    this.transport.onMessage(data => this.onMessage(data));
    this.transport.onClose(() => {
      if (!this.established) this.establishedReject?.(new Error('closed before handshake'));
    });
  }

  /** Send our hello and resolve once key confirmation completes both ways. */
  connect(): Promise<void> {
    return new Promise((resolve, reject) => {
      this.establishedResolve = resolve;
      this.establishedReject = reject;
      this.transport.send(tag(TAG_HELLO, utf8(JSON.stringify(this.channel.hello()))));
    });
  }

  onFrame(cb: (frame: OutgoingFrame) => void): void {
    this.frameCb = cb;
  }
  onError(cb: (err: Error) => void): void {
    this.errorCb = cb;
  }

  /** Send an application frame (rpc / rpc_result / stream / live / ctrl). */
  send(streamId: string, type: FrameType, payload: unknown): void {
    if (!this.established) throw new Error('BridgePeer not established');
    const frame = this.sender.send(streamId, type, payload, this.receiver.cursors());
    this.transport.send(tag(TAG_DATA, this.channel.seal(utf8(JSON.stringify(frame)))));
  }

  /** Per-stream cursors this side needs — for ctrl:resume after a reconnect. */
  cursors(): Record<string, number> {
    return this.receiver.cursors();
  }

  /** Replay stored frames from a peer's resume cursors (host side). */
  replay(cursors: Record<string, number>): void {
    for (const frame of this.sender.replayFrom(cursors)) {
      this.transport.send(tag(TAG_DATA, this.channel.seal(utf8(JSON.stringify(frame)))));
    }
  }

  private onMessage(data: Uint8Array): void {
    const t = data[0];
    const body = data.subarray(1);
    try {
      if (t === TAG_HELLO) {
        const confirm = this.channel.onPeerHello(JSON.parse(td(body)));
        this.transport.send(tag(TAG_CONFIRM, utf8(JSON.stringify(confirm))));
        this.sentConfirm = true;
      } else if (t === TAG_CONFIRM) {
        // We can only verify after we've derived keys (seen the peer hello).
        if (!this.sentConfirm) return; // hello not processed yet; ordering guard
        const ok = this.channel.verifyPeerConfirm(JSON.parse(td(body)));
        if (!ok) {
          this.fail(new Error('key confirmation failed — pairing burned'));
          return;
        }
        this.established = true;
        this.establishedResolve?.();
      } else if (t === TAG_DATA) {
        this.onData(body);
      }
    } catch (e) {
      this.fail(e instanceof Error ? e : new Error(String(e)));
    }
  }

  private onData(sealed: Uint8Array): void {
    let frame: OutgoingFrame;
    try {
      frame = JSON.parse(td(this.channel.open(sealed))) as OutgoingFrame;
    } catch {
      // Mid-session AEAD/parse failure: DROP + count, never tear down (07-S31).
      this.openFailures++;
      if (this.openFailures > 32) this.fail(new Error('sustained decrypt failures'));
      return;
    }
    const res = this.receiver.accept(frame);
    if (res.kind === 'deliver') {
      for (const f of res.frames) {
        // Ack pruning: the peer's cursors ride on every frame we send; also prune
        // our resend buffer by the cursors the peer reported on this frame.
        for (const [sid, next] of Object.entries(frame.ack)) this.sender.onAck(sid, next);
        this.frameCb?.(f);
      }
    }
  }

  private fail(err: Error): void {
    this.establishedReject?.(err);
    this.errorCb?.(err);
  }

  close(): void {
    this.transport.close();
  }
}

function tag(t: number, body: Uint8Array): Uint8Array {
  const out = new Uint8Array(body.length + 1);
  out[0] = t;
  out.set(body, 1);
  return out;
}

function td(bytes: Uint8Array): string {
  return new TextDecoder().decode(bytes);
}
