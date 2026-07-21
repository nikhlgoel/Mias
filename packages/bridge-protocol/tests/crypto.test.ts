import { test } from 'node:test';
import assert from 'node:assert/strict';
import { SecureChannel, type HandshakeContext } from '../src/crypto.ts';
import { nodeCryptoProvider as C } from '../src/node-crypto.ts';

function pair(ctxHost: HandshakeContext, ctxClient: HandshakeContext) {
  const host = new SecureChannel(C, 'host', ctxHost);
  const client = new SecureChannel(C, 'client', ctxClient);
  const hConfirm = host.onPeerHello(client.hello());
  const cConfirm = client.onPeerHello(host.hello());
  const hostOk = host.verifyPeerConfirm(cConfirm);
  const clientOk = client.verifyPeerConfirm(hConfirm);
  return { host, client, hostOk, clientOk };
}

const ctx = (over: Partial<HandshakeContext> = {}): HandshakeContext => ({
  rendezvousId: 'rid-abc-123',
  relayOrigin: 'wss://relay.example',
  code: 'K7Q9F2H',
  ...over,
});

test('matching code + transcript → both sides confirm and can talk', () => {
  const { host, client, hostOk, clientOk } = pair(ctx(), ctx());
  assert.equal(hostOk, true);
  assert.equal(clientOk, true);
  assert.equal(host.established, true);

  const msg = new TextEncoder().encode('hello over the bridge');
  const sealed = host.seal(msg);
  const opened = client.open(sealed);
  assert.deepEqual(Array.from(opened), Array.from(msg));

  // and the other direction
  const reply = new TextEncoder().encode('reply');
  assert.deepEqual(Array.from(host.open(client.seal(reply))), Array.from(reply));
});

test('wrong pairing code BURNS: key confirmation fails', () => {
  const { hostOk, clientOk } = pair(ctx({ code: 'RIGHT12' }), ctx({ code: 'WRONG34' }));
  assert.equal(hostOk, false);
  assert.equal(clientOk, false);
});

test('relay cross-connect on a different rendezvous_id fails confirmation (channel binding)', () => {
  const { hostOk } = pair(ctx({ rendezvousId: 'rid-A' }), ctx({ rendezvousId: 'rid-B' }));
  assert.equal(hostOk, false);
});

test('counter nonces differ per frame (no reuse over a stream)', () => {
  const { host, client } = pair(ctx(), ctx());
  const a = host.seal(new TextEncoder().encode('one'));
  const b = host.seal(new TextEncoder().encode('two'));
  const nonceA = Buffer.from(a.slice(0, 12)).toString('hex');
  const nonceB = Buffer.from(b.slice(0, 12)).toString('hex');
  assert.notEqual(nonceA, nonceB);
  // both still open in order
  assert.equal(new TextDecoder().decode(client.open(a)), 'one');
  assert.equal(new TextDecoder().decode(client.open(b)), 'two');
});

test('tampered ciphertext fails to open (AEAD integrity)', () => {
  const { host, client } = pair(ctx(), ctx());
  const sealed = host.seal(new TextEncoder().encode('secret'));
  sealed[sealed.length - 1] = (sealed[sealed.length - 1] ?? 0) ^ 0xff; // flip a tag byte
  assert.throws(() => client.open(sealed));
});

test('sealing before establishment throws', () => {
  const host = new SecureChannel(C, 'host', ctx());
  assert.throws(() => host.seal(new Uint8Array([1])), /not established/);
});

test('forward secrecy: a fresh session derives different keys from the same code', () => {
  const s1 = pair(ctx(), ctx());
  const s2 = pair(ctx(), ctx());
  const c1 = Buffer.from(s1.host.seal(new TextEncoder().encode('x'))).toString('hex');
  const c2 = Buffer.from(s2.host.seal(new TextEncoder().encode('x'))).toString('hex');
  // Same plaintext + same code, but ephemeral ECDH ⇒ different ciphertext/keys.
  assert.notEqual(c1, c2);
});
