import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  SecureChannel,
  CpacePake,
  nobleCryptoProvider as NOBLE,
  type HandshakeContext,
  utf8,
} from '../src/index.ts';
import { nodeCryptoProvider as NODE } from '../src/node-crypto.ts';
import type { CryptoProvider } from '../src/crypto.ts';

const ctx = (over: Partial<HandshakeContext> = {}): HandshakeContext => ({
  rendezvousId: 'rid-abc-123',
  relayOrigin: 'wss://relay.example',
  code: 'K7Q9F2H',
  ...over,
});
const sid = (c: HandshakeContext) => utf8(`${c.rendezvousId}|${c.relayOrigin}`);

// ── CpacePake unit properties ───────────────────────────────────────────────

test('CPace: matching code + sid → both sides derive identical material', () => {
  const c = ctx();
  const a = new CpacePake(NOBLE, c.code, sid(c));
  const b = new CpacePake(NOBLE, c.code, sid(c));
  const t = utf8('transcript-bytes');
  const ma = a.codeMaterial(b.publicShare(), t);
  const mb = b.codeMaterial(a.publicShare(), t);
  assert.deepEqual(Array.from(ma), Array.from(mb));
});

test('CPace: a wrong code yields unrelated material (offline-guess resistance)', () => {
  const c = ctx();
  const a = new CpacePake(NOBLE, 'RIGHT12', sid(c));
  const b = new CpacePake(NOBLE, 'WRONG34', sid(c));
  const t = utf8('t');
  assert.notDeepEqual(Array.from(a.codeMaterial(b.publicShare(), t)),
                      Array.from(b.codeMaterial(a.publicShare(), t)));
});

test('CPace: material is bound to the transcript', () => {
  const c = ctx();
  const a = new CpacePake(NOBLE, c.code, sid(c));
  const b = new CpacePake(NOBLE, c.code, sid(c));
  const share = b.publicShare();
  assert.notDeepEqual(
    Array.from(a.codeMaterial(share, utf8('transcript-1'))),
    Array.from(a.codeMaterial(share, utf8('transcript-2'))),
  );
});

test('CPace: a garbage peer share is rejected', () => {
  const c = ctx();
  const a = new CpacePake(NOBLE, c.code, sid(c));
  assert.throws(() => a.codeMaterial(new Uint8Array(32).fill(0xff), utf8('t')));
});

// ── SecureChannel end-to-end with CPace ─────────────────────────────────────

function pairWithCpace(C: CryptoProvider, cHost: HandshakeContext, cClient: HandshakeContext) {
  const host = new SecureChannel(C, 'host', cHost, new CpacePake(C, cHost.code, sid(cHost)));
  const client = new SecureChannel(C, 'client', cClient, new CpacePake(C, cClient.code, sid(cClient)));
  const hConfirm = host.onPeerHello(client.hello());
  const cConfirm = client.onPeerHello(host.hello());
  return { host, client, hostOk: host.verifyPeerConfirm(cConfirm), clientOk: client.verifyPeerConfirm(hConfirm) };
}

test('CPace SecureChannel: matching code confirms and encrypts both ways', () => {
  const p = pairWithCpace(NOBLE, ctx(), ctx());
  assert.equal(p.hostOk && p.clientOk, true);
  const msg = utf8('over the CPace channel');
  assert.deepEqual(Array.from(p.client.open(p.host.seal(msg))), Array.from(msg));
});

test('CPace SecureChannel: wrong code burns (no confirmation)', () => {
  const p = pairWithCpace(NOBLE, ctx({ code: 'RIGHT12' }), ctx({ code: 'WRONG34' }));
  assert.equal(p.hostOk, false);
  assert.equal(p.clientOk, false);
});

// ── noble provider parity + node↔noble interop (proves phone↔PC) ─────────────

test('nobleCryptoProvider satisfies the SecureChannel (phone-side crypto)', () => {
  const p = pairWithCpace(NOBLE, ctx(), ctx());
  assert.equal(p.hostOk && p.clientOk, true);
});

test('INTEROP: a noble host and a node client pair and talk (RN phone ↔ Node PC)', () => {
  const c = ctx();
  const host = new SecureChannel(NOBLE, 'host', c, new CpacePake(NOBLE, c.code, sid(c)));
  const client = new SecureChannel(NODE, 'client', c, new CpacePake(NODE, c.code, sid(c)));
  const hConfirm = host.onPeerHello(client.hello());
  const cConfirm = client.onPeerHello(host.hello());
  assert.equal(host.verifyPeerConfirm(cConfirm), true);
  assert.equal(client.verifyPeerConfirm(hConfirm), true);

  // And a real frame crosses the provider boundary intact.
  const msg = utf8('hello from the phone');
  assert.deepEqual(Array.from(client.open(host.seal(msg))), Array.from(msg));
  const reply = utf8('reply from the PC');
  assert.deepEqual(Array.from(host.open(client.seal(reply))), Array.from(reply));
});

test('INTEROP: node AEAD seals, noble opens (identical AES-256-GCM layout)', () => {
  const c = ctx();
  const a = new SecureChannel(NODE, 'host', c, new CpacePake(NODE, c.code, sid(c)));
  const b = new SecureChannel(NOBLE, 'client', c, new CpacePake(NOBLE, c.code, sid(c)));
  const bConfirm = b.onPeerHello(a.hello());
  const aConfirm = a.onPeerHello(b.hello());
  assert.equal(a.verifyPeerConfirm(bConfirm), true);
  assert.equal(b.verifyPeerConfirm(aConfirm), true);
  const m = utf8('cross-impl AEAD');
  assert.deepEqual(Array.from(b.open(a.seal(m))), Array.from(m));
});
