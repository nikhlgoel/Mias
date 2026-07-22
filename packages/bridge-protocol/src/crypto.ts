/**
 * SecureChannel — the E2EE layer of the Bridge (bridge/docs/03).
 *
 * Provider-agnostic so the same handshake runs on the PC (Node crypto) and,
 * later, the phone (an RN native crypto provider). What it provides today:
 *   - Forward secrecy via an ephemeral X25519 ECDH per session (07-S7).
 *   - Channel binding: keys are derived over a transcript of rendezvous_id +
 *     both ephemeral public keys + relay origin + protocol-version offers,
 *     so a relay cross-connect / downgrade breaks key confirmation (07-S2/S32).
 *   - Explicit key confirmation; a mismatch BURNS the pairing (07-S3).
 *   - AEAD with counter nonces per (key, direction) — never random GCM over a
 *     long stream (07-S6).
 *   - A real **balanced PAKE** (CpacePake, cpace.ts) over the audited @noble
 *     ristretto255, so a short pairing code never yields an offline-crackable
 *     value (07-S2) — one online guess only. Mixed with the ephemeral ECDH
 *     above. `CodeKdfPake` remains as a non-PAKE fallback for tests.
 */

// ── Pluggable crypto primitives (Node now; RN native later) ─────────────────

export interface KeyPair {
  publicKey: Uint8Array;
  privateKey: Uint8Array;
}

export interface CryptoProvider {
  randomBytes(n: number): Uint8Array;
  /** Ephemeral X25519 keypair. */
  x25519Generate(): KeyPair;
  /** X25519 raw shared secret. */
  x25519Shared(privateKey: Uint8Array, peerPublicKey: Uint8Array): Uint8Array;
  /** HKDF-SHA256 → `length` bytes. */
  hkdf(ikm: Uint8Array, salt: Uint8Array, info: Uint8Array, length: number): Uint8Array;
  /** HMAC-SHA256. */
  hmac(key: Uint8Array, data: Uint8Array): Uint8Array;
  /** AES-256-GCM seal: returns ciphertext||tag. `nonce` is 12 bytes. */
  aesGcmSeal(key: Uint8Array, nonce: Uint8Array, plaintext: Uint8Array, aad?: Uint8Array): Uint8Array;
  /** AES-256-GCM open; throws on auth failure. */
  aesGcmOpen(key: Uint8Array, nonce: Uint8Array, ciphertext: Uint8Array, aad?: Uint8Array): Uint8Array;
}

// ── Pairing-code PAKE seam ──────────────────────────────────────────────────
//
// A balanced PAKE exchanges one public share each way (piggybacked on HELLO, so
// no extra round-trip) and then derives secret material bound to the transcript.
// The real implementation is `CpacePake` (cpace.ts); `CodeKdfPake` is a plain
// HKDF fallback that ignores the peer share (kept for tests / non-PAKE paths).

export interface Pake {
  /** This side's public share, sent in HELLO. Empty for a non-PAKE fallback. */
  publicShare(): Uint8Array;
  /** Secret material after seeing the peer's share, bound to the transcript. */
  codeMaterial(peerShare: Uint8Array, transcript: Uint8Array): Uint8Array;
}

/**
 * Non-PAKE fallback: HKDF(code, salt=transcript). NOT zero-knowledge against an
 * online guess — use `CpacePake` in production. Kept for tests and any flow that
 * deliberately opts out of the PAKE.
 */
export class CodeKdfPake implements Pake {
  private readonly crypto: CryptoProvider;
  private readonly code: string;
  constructor(crypto: CryptoProvider, code: string) {
    this.crypto = crypto;
    this.code = code;
  }
  publicShare(): Uint8Array {
    return new Uint8Array(0);
  }
  codeMaterial(_peerShare: Uint8Array, transcript: Uint8Array): Uint8Array {
    return this.crypto.hkdf(utf8(this.code), transcript, utf8('mias-pake-v1'), 32);
  }
}

// ── Roles + wire messages for the handshake ─────────────────────────────────

export type ChannelRole = 'host' | 'client';

/** First handshake message: ephemeral pubkey + PAKE public share (both public). */
export interface HelloMsg {
  eph: string; // base64 ephemeral X25519 public key
  pake: string; // base64 PAKE public share (CPace Y); "" for the KDF fallback
  versions: number[]; // bridge protocol versions offered
}

/** Key-confirmation message (HMAC over the transcript, tagged by sender role). */
export interface ConfirmMsg {
  mac: string; // base64
}

export interface HandshakeContext {
  rendezvousId: string;
  relayOrigin: string;
  code: string;
}

// ── SecureChannel ───────────────────────────────────────────────────────────

const DIR_HOST_TO_CLIENT = 1;
const DIR_CLIENT_TO_HOST = 2;

export class SecureChannel {
  private readonly crypto: CryptoProvider;
  private readonly role: ChannelRole;
  private readonly ctx: HandshakeContext;
  private readonly pake: Pake;
  private readonly versions: number[];
  private eph: KeyPair;
  private sendKey: Uint8Array | null = null;
  private recvKey: Uint8Array | null = null;
  private confirmKey: Uint8Array | null = null;
  private sendCtr = 0;
  private recvCtr = 0;
  private sendDir: number;
  private recvDir: number;
  established = false;

  constructor(
    crypto: CryptoProvider,
    role: ChannelRole,
    ctx: HandshakeContext,
    pake?: Pake,
    versions: number[] = [1],
  ) {
    this.crypto = crypto;
    this.role = role;
    this.ctx = ctx;
    // Default is the plain-KDF fallback; BridgePeer injects the real CpacePake.
    this.pake = pake ?? new CodeKdfPake(crypto, ctx.code);
    this.versions = versions;
    this.eph = crypto.x25519Generate();
    // Directions are fixed by role so both sides agree which key encrypts which way.
    this.sendDir = role === 'host' ? DIR_HOST_TO_CLIENT : DIR_CLIENT_TO_HOST;
    this.recvDir = role === 'host' ? DIR_CLIENT_TO_HOST : DIR_HOST_TO_CLIENT;
  }

  /** The hello to send to the peer (ephemeral pubkey + PAKE public share). */
  hello(): HelloMsg {
    return { eph: b64(this.eph.publicKey), pake: b64(this.pake.publicShare()), versions: this.versions };
  }

  /**
   * Consume the peer's hello: derive session keys (ECDH ⊕ code) over the bound
   * transcript, and return this side's key-confirmation message.
   */
  onPeerHello(peer: HelloMsg): ConfirmMsg {
    const peerEph = unb64(peer.eph);
    const shared = this.crypto.x25519Shared(this.eph.privateKey, peerEph);

    // Canonical transcript: rendezvous_id · relayOrigin · host-eph · client-eph ·
    // min-common-version. Both sides order eph keys by role, not arrival, so the
    // transcript is identical on both ends.
    const hostEph = this.role === 'host' ? this.eph.publicKey : peerEph;
    const clientEph = this.role === 'host' ? peerEph : this.eph.publicKey;
    const version = minCommon(this.versions, peer.versions);
    const transcript = concat(
      utf8(this.ctx.rendezvousId), utf8('|'),
      utf8(this.ctx.relayOrigin), utf8('|'),
      hostEph, utf8('|'), clientEph, utf8('|'),
      utf8(String(version)),
    );

    // Mix the ephemeral ECDH (forward secrecy) with the PAKE material (which the
    // peer's public share + our secret produce, bound to the transcript).
    const codeMat = this.pake.codeMaterial(unb64(peer.pake), transcript);
    const ikm = concat(shared, codeMat);
    const salt = utf8(this.ctx.rendezvousId);

    // Distinct keys per direction + a confirmation key, all bound to the transcript.
    this.sendKey = this.crypto.hkdf(ikm, salt, tinfo(transcript, `k${this.sendDir}`), 32);
    this.recvKey = this.crypto.hkdf(ikm, salt, tinfo(transcript, `k${this.recvDir}`), 32);
    this.confirmKey = this.crypto.hkdf(ikm, salt, tinfo(transcript, 'confirm'), 32);

    return { mac: b64(this.crypto.hmac(this.confirmKey, utf8(`confirm:${this.role}`))) };
  }

  /**
   * Verify the peer's key confirmation. Returns true on success; false BURNS the
   * pairing (07-S3): a mismatch means wrong code / relay tampering / MITM.
   */
  verifyPeerConfirm(peer: ConfirmMsg): boolean {
    if (!this.confirmKey) return false;
    const peerRole: ChannelRole = this.role === 'host' ? 'client' : 'host';
    const expected = this.crypto.hmac(this.confirmKey, utf8(`confirm:${peerRole}`));
    if (!timingSafeEqual(expected, unb64(peer.mac))) return false;
    this.established = true;
    return true;
  }

  /** Seal a frame payload. Counter-nonce per (key, direction) — never reused. */
  seal(plaintext: Uint8Array, aad?: Uint8Array): Uint8Array {
    if (!this.established || !this.sendKey) throw new Error('SecureChannel not established');
    const nonce = counterNonce(this.sendDir, this.sendCtr++);
    const ct = this.crypto.aesGcmSeal(this.sendKey, nonce, plaintext, aad);
    return concat(nonce, ct);
  }

  /** Open a sealed frame. Throws on auth failure (caller drops; never fatal mid-session — 07-S31). */
  open(sealed: Uint8Array, aad?: Uint8Array): Uint8Array {
    if (!this.established || !this.recvKey) throw new Error('SecureChannel not established');
    const nonce = sealed.slice(0, 12);
    const ct = sealed.slice(12);
    return this.crypto.aesGcmOpen(this.recvKey, nonce, ct, aad);
  }
}

// ── helpers ─────────────────────────────────────────────────────────────────

function counterNonce(direction: number, counter: number): Uint8Array {
  const n = new Uint8Array(12);
  new DataView(n.buffer).setUint32(0, direction);
  // 64-bit counter in the low 8 bytes (big-endian).
  const hi = Math.floor(counter / 2 ** 32);
  new DataView(n.buffer).setUint32(4, hi);
  new DataView(n.buffer).setUint32(8, counter >>> 0);
  return n;
}

function minCommon(a: number[], b: number[]): number {
  const set = new Set(b);
  const common = a.filter(v => set.has(v));
  return common.length > 0 ? Math.max(...common) : Math.min(...a);
}

function tinfo(transcript: Uint8Array, label: string): Uint8Array {
  return concat(utf8(`mias-bridge:${label}:`), transcript);
}

export function utf8(s: string): Uint8Array {
  return new TextEncoder().encode(s);
}

export function concat(...parts: Uint8Array[]): Uint8Array {
  const total = parts.reduce((n, p) => n + p.length, 0);
  const out = new Uint8Array(total);
  let o = 0;
  for (const p of parts) {
    out.set(p, o);
    o += p.length;
  }
  return out;
}

export function b64(bytes: Uint8Array): string {
  let s = '';
  for (const byte of bytes) s += String.fromCharCode(byte);
  // btoa exists in RN (Hermes) and Node ≥ 16.
  return btoa(s);
}

export function unb64(s: string): Uint8Array {
  const bin = atob(s);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

function timingSafeEqual(a: Uint8Array, b: Uint8Array): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a[i]! ^ b[i]!;
  return diff === 0;
}
