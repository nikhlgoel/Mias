import { test } from 'node:test';
import assert from 'node:assert/strict';
import { WebSocket } from 'ws';
import { Relay } from '../src/index.ts';

const sleep = (ms: number) => new Promise(r => setTimeout(r, ms));

async function startRelay(opts = {}): Promise<Relay> {
  const relay = new Relay({ port: 0, host: '127.0.0.1', ...opts });
  for (let i = 0; i < 50 && relay.port === 0; i++) await sleep(10);
  return relay;
}

function connect(port: number, hello: object): Promise<{ ws: WebSocket; ack: { slotToken?: string } }> {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(`ws://127.0.0.1:${port}`);
    ws.binaryType = 'arraybuffer';
    ws.on('open', () => ws.send(JSON.stringify(hello)));
    ws.once('message', (d: Buffer) => resolve({ ws, ack: JSON.parse(d.toString()) }));
    ws.on('error', reject);
    ws.on('close', () => reject(new Error('closed')));
  });
}

test('forwards opaque bytes between matched host and client', async () => {
  const relay = await startRelay();
  const host = await connect(relay.port, { rid: 'r1', role: 'host', channel: 'c1' });
  const client = await connect(relay.port, { rid: 'r1', role: 'client', channel: 'c1' });

  const got = new Promise<Uint8Array>(resolve => {
    client.ws.on('message', (d: ArrayBuffer, isBinary: boolean) => {
      if (isBinary) resolve(new Uint8Array(d));
    });
  });
  host.ws.send(new Uint8Array([0x03, 1, 2, 3])); // tagged binary — not JSON
  const received = await got;
  assert.deepEqual(Array.from(received), [0x03, 1, 2, 3]);
  assert.equal(relay.parseableFrames, 0, 'ciphertext never parses as JSON');

  host.ws.close();
  client.ws.close();
  await relay.close();
});

test('binary frames never increment the zero-knowledge counter; JSON would', async () => {
  const relay = await startRelay();
  const host = await connect(relay.port, { rid: 'r2', role: 'host', channel: 'c1' });
  const client = await connect(relay.port, { rid: 'r2', role: 'client', channel: 'c1' });
  const got = new Promise<void>(resolve => client.ws.on('message', () => resolve()));
  // A hypothetical plaintext leak (JSON) WOULD be counted — proving the assertion is real.
  host.ws.send(Buffer.from('{"leak":true}'));
  await got;
  assert.equal(relay.parseableFrames, 1);
  host.ws.close();
  client.ws.close();
  await relay.close();
});

test('reclaim requires the slot token', async () => {
  const relay = await startRelay();
  const first = await connect(relay.port, { rid: 'r3', role: 'host', channel: 'c1' });
  assert.ok((first.ack.slotToken ?? '').length > 0);

  // Without the token → rejected/closed.
  await assert.rejects(() => connect(relay.port, { rid: 'r3', role: 'host', channel: 'c1' }), /closed/);

  // With the token → allowed (reclaims), evicting the first.
  const reclaimed = await connect(relay.port, {
    rid: 'r3', role: 'host', channel: 'c1', slotToken: first.ack.slotToken,
  });
  assert.equal(relay.reclaims, 1);

  reclaimed.ws.close();
  await relay.close();
});

test('separate channels do not collide (interactive vs bulk)', async () => {
  const relay = await startRelay();
  const c1Host = await connect(relay.port, { rid: 'r4', role: 'host', channel: 'c1' });
  const c2Host = await connect(relay.port, { rid: 'r4', role: 'host', channel: 'c2' });
  assert.ok(c1Host.ack.slotToken !== c2Host.ack.slotToken);
  c1Host.ws.close();
  c2Host.ws.close();
  await relay.close();
});

test('idle rooms are reaped after the TTL', async () => {
  const relay = await startRelay({ roomTtlMs: 60 });
  const host = await connect(relay.port, { rid: 'r5', role: 'host', channel: 'c1' });
  assert.equal(relay.roomCount, 1);
  host.ws.close();
  await sleep(200);
  assert.equal(relay.roomCount, 0);
  await relay.close();
});
