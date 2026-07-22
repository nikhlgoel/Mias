/**
 * CpacePake — a balanced PAKE (CPace construction) over ristretto255, the real
 * replacement for the HKDF stand-in (bridge/docs/03 section 6, 07-S2).
 *
 * Why this is safe to write here (vs. the earlier "hand-rolling CPace is
 * unsafe"): the elliptic-curve group, hash-to-group, and scalar arithmetic come
 * from the **audited @noble/curves** ristretto255 — we do not hand-roll any
 * field/curve math. What's implemented is the CPace protocol shape:
 *
 *   1. Both sides derive the SAME password-dependent generator
 *      G = hash_to_ristretto255( DSI ‖ len(pw) ‖ pw ‖ len(sid) ‖ sid )
 *      from the pairing code `pw` and a shared, pre-transcript session id `sid`
 *      (rendezvous_id ‖ relay origin — both known to both ends before any keys).
 *   2. Each picks a random scalar y, sends Y = y·G (piggybacked on HELLO, so no
 *      extra round-trip).
 *   3. Shared point K = y·Y_peer = (y·y_peer)·G — identical on both ends.
 *   4. Material = HKDF(K.bytes, salt=transcript) — bound to the full transcript
 *      (ephemeral pubkeys, versions, relay origin) by the SecureChannel.
 *
 * An attacker who doesn't know `pw` cannot compute G, so cannot recover K from
 * the public Y values → only one online guess per code, never an offline crack.
 * Forward secrecy holds independently via the ephemeral scalars y (and the
 * separate ephemeral X25519 the SecureChannel also mixes in).
 *
 * NOTE: because both ends are Mias, this must interoperate only with itself —
 * it does not need byte-compat with other CPace implementations. A cross-check
 * against the CFRG CPace test vectors is recommended before external interop.
 */
import { ristretto255, ristretto255_hasher } from '@noble/curves/ed25519.js';
import type { CryptoProvider, Pake } from './crypto.ts';
import { concat, utf8 } from './crypto.ts';

const DSI = utf8('CPace-ristretto255-mias-v1'); // domain-separation identifier
const Point = ristretto255.Point;

/** Length-prefixed concat (CPace's lv_cat), so fields can't be confused. */
function lv(...parts: Uint8Array[]): Uint8Array {
  const out: Uint8Array[] = [];
  for (const p of parts) {
    const len = new Uint8Array(2);
    new DataView(len.buffer).setUint16(0, p.length);
    out.push(len, p);
  }
  return concat(...out);
}

export class CpacePake implements Pake {
  private readonly crypto: CryptoProvider;
  private readonly generator: InstanceType<typeof Point>;
  private readonly y: bigint;
  private readonly yPublic: Uint8Array;

  /**
   * @param sid stable, pre-transcript shared context (rendezvous_id ‖ relay
   *   origin). Both ends must derive it identically.
   */
  constructor(crypto: CryptoProvider, code: string, sid: Uint8Array) {
    this.crypto = crypto;
    // Password-derived generator (the security-critical step): map the code +
    // sid into the group. Only holders of `code` can compute this point.
    this.generator = ristretto255_hasher.hashToCurve(lv(DSI, utf8(code), sid));
    // Ephemeral secret scalar in [1, order); reject 0.
    this.y = randomScalar(crypto);
    this.yPublic = this.generator.multiply(this.y).toBytes();
  }

  publicShare(): Uint8Array {
    return this.yPublic;
  }

  codeMaterial(peerShare: Uint8Array, transcript: Uint8Array): Uint8Array {
    // K = y · Y_peer. Point.fromBytes validates the encoding (rejects garbage /
    // small-order); a bad share throws and the handshake fails (→ burn).
    const yPeer = Point.fromBytes(peerShare);
    const k = yPeer.multiply(this.y).toBytes();
    return this.crypto.hkdf(k, transcript, utf8('mias-cpace-isk-v1'), 32);
  }
}

/** Uniform scalar in [1, order) using the provider's RNG (64-byte reduction). */
function randomScalar(crypto: CryptoProvider): bigint {
  const order = Point.Fn.ORDER;
  for (let i = 0; i < 64; i++) {
    const bytes = crypto.randomBytes(64);
    let n = 0n;
    for (const b of bytes) n = (n << 8n) | BigInt(b);
    const s = n % order;
    if (s !== 0n) return s;
  }
  throw new Error('failed to draw a non-zero scalar');
}
