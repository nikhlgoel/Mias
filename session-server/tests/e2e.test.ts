import { test } from 'node:test';
import assert from 'node:assert/strict';
import { WebSocket } from 'ws';
import {
  BridgePeer,
  McpClient,
  MCP_PROTOCOL_VERSION,
  Streams,
  type HttpPost,
  type JsonRpcRequest,
  type OutgoingFrame,
} from '@mias/bridge-protocol';
import { nodeCryptoProvider as C } from '@mias/bridge-protocol/node';
import { Relay } from '../../relay/src/index.ts';
import { SessionServer } from '../src/index.ts';
import { connectToRelay } from '../src/relay-connection.ts';

const CANNED = 'The quick brown fox jumps over the lazy dog, then keeps on running.';

/** A fake desktop/server.py MCP worker returning a fixed generation. */
function fakeMcp(): McpClient {
  const post: HttpPost = async (_url, body) => {
    const req = JSON.parse(body) as JsonRpcRequest;
    const ok = (result: unknown) => JSON.stringify({ jsonrpc: '2.0', id: req.id, result });
    if (req.method === 'initialize') {
      return ok({ protocolVersion: MCP_PROTOCOL_VERSION, serverInfo: { name: 's', version: 'v' }, capabilities: {} });
    }
    if (req.method === 'notifications/initialized') return ok({});
    return ok({ content: [{ type: 'text', text: CANNED }], isError: false });
  };
  return new McpClient({ url: 'http://127.0.0.1:8401/rpc', post });
}

async function startRelay(): Promise<Relay> {
  const relay = new Relay({ port: 0, host: '127.0.0.1' });
  // Wait for the server to bind.
  for (let i = 0; i < 50 && relay.port === 0; i++) await sleep(10);
  return relay;
}
const sleep = (ms: number) => new Promise(r => setTimeout(r, ms));

test('end-to-end: phone pairs over the relay and streams an offload turn', async () => {
  const relay = await startRelay();
  const relayUrl = `ws://127.0.0.1:${relay.port}`;
  const rid = 'rid-e2e-001';
  const code = 'K7Q9F2H';

  const server = new SessionServer({
    relayUrl, rendezvousId: rid, code,
    mcpUrl: 'http://127.0.0.1:8401/rpc',
    crypto: C, mcpClient: fakeMcp(), chunkChars: 8,
  });
  const serverStarted = server.start();

  // Mock phone: client peer over the relay.
  const transport = await connectToRelay(relayUrl, { rid, role: 'client', channel: 'c1' }, WebSocket);
  const client = new BridgePeer(transport, C, 'client', { rendezvousId: rid, relayOrigin: relayUrl, code });

  const chunks: string[] = [];
  let done = false;
  client.onFrame((f: OutgoingFrame) => {
    if (f.streamId === Streams.tokens && f.type === 'stream') {
      chunks.push((f.payload as { chunk: string }).chunk);
    } else if (f.type === 'rpc_result') {
      done = true;
    }
  });

  await client.connect();
  await serverStarted;

  client.send(Streams.control, 'rpc', { method: 'generate', params: { prompt: 'hi' }, id: 1 });

  for (let i = 0; i < 200 && !done; i++) await sleep(10);

  assert.equal(done, true, 'received the terminal rpc_result');
  assert.equal(chunks.join(''), CANNED, 'streamed chunks reassemble to the model output');
  assert.equal(relay.parseableFrames, 0, 'relay could read ZERO frames (zero-knowledge)');
  assert.ok(chunks.length > 3, 'output actually streamed in multiple frames');

  server.stop();
  client.close();
  await relay.close();
});

test('wrong pairing code burns: the phone never establishes', async () => {
  const relay = await startRelay();
  const relayUrl = `ws://127.0.0.1:${relay.port}`;
  const rid = 'rid-e2e-002';

  const server = new SessionServer({
    relayUrl, rendezvousId: rid, code: 'RIGHT12',
    mcpUrl: 'x', crypto: C, mcpClient: fakeMcp(),
  });
  void server.start().catch(() => {});

  const transport = await connectToRelay(relayUrl, { rid, role: 'client', channel: 'c1' }, WebSocket);
  const client = new BridgePeer(transport, C, 'client', { rendezvousId: rid, relayOrigin: relayUrl, code: 'WRONG34' });

  let failed = false;
  client.onError(() => { failed = true; });
  await assert.rejects(() => client.connect(), /confirmation failed|closed/);
  assert.equal(client.established, false);
  assert.ok(failed || !client.established);

  server.stop();
  client.close();
  await relay.close();
});

test('relay rejects an unauthenticated slot reclaim (07-S30)', async () => {
  const relay = await startRelay();
  const relayUrl = `ws://127.0.0.1:${relay.port}`;
  const rid = 'rid-e2e-003';

  const a = await connectToRelay(relayUrl, { rid, role: 'client', channel: 'c1' }, WebSocket);
  assert.ok(a.slotToken.length > 0, 'first attach issues a slot token');

  // A second client without the token must NOT evict the first.
  await assert.rejects(
    () => connectToRelay(relayUrl, { rid, role: 'client', channel: 'c1' }, WebSocket),
    /closed|occupied/,
  );

  a.close();
  await relay.close();
});
