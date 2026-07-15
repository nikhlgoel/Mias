import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  McpClient,
  MCP_PROTOCOL_VERSION,
  type HttpPost,
  type JsonRpcRequest,
} from '../src/index.ts';

/** Scripted fake of desktop/server.py's /rpc endpoint. */
function fakeServer() {
  const calls: Array<{ req: JsonRpcRequest; headers: Record<string, string> }> = [];
  const post: HttpPost = async (_url, body, headers) => {
    const req = JSON.parse(body) as JsonRpcRequest;
    calls.push({ req, headers });
    const respond = (result: unknown) =>
      JSON.stringify({ jsonrpc: '2.0', id: req.id, result });
    switch (req.method) {
      case 'initialize':
        return respond({
          protocolVersion: MCP_PROTOCOL_VERSION,
          serverInfo: { name: 'Mias Desktop Server', version: 'release-001' },
          capabilities: { tools: { listChanged: false } },
        });
      case 'notifications/initialized':
        return respond({});
      case 'tools/list':
        return respond({ tools: [{ name: 'generate', description: 'Generate text' }] });
      case 'tools/call': {
        const args = (req.params?.arguments ?? {}) as Record<string, string>;
        return respond({
          content: [{ type: 'text', text: `echo:${args.prompt}` }],
          isError: false,
        });
      }
      default:
        return JSON.stringify({
          jsonrpc: '2.0',
          id: req.id,
          error: { code: -32601, message: `Method '${req.method}' not supported.` },
        });
    }
  };
  return { calls, post };
}

test('initialize performs the full handshake and captures server info', async () => {
  const srv = fakeServer();
  const c = new McpClient({ url: 'http://pc:8401/rpc', token: 'secret', post: srv.post });
  const info = await c.initialize();
  assert.equal(info.name, 'Mias Desktop Server');
  assert.equal(c.negotiatedProtocolVersion, MCP_PROTOCOL_VERSION);
  // Handshake order + shapes
  assert.equal(srv.calls[0]!.req.method, 'initialize');
  assert.equal(
    (srv.calls[0]!.req.params as { protocolVersion: string }).protocolVersion,
    MCP_PROTOCOL_VERSION,
  );
  assert.equal(srv.calls[1]!.req.method, 'notifications/initialized');
  assert.equal(srv.calls[1]!.req.id, undefined); // notification carries no id
});

test('token is sent as X-Mias-Token; omitted when empty', async () => {
  const withToken = fakeServer();
  await new McpClient({ url: 'u', token: 't0k', post: withToken.post }).initialize();
  assert.equal(withToken.calls[0]!.headers['X-Mias-Token'], 't0k');

  const noToken = fakeServer();
  await new McpClient({ url: 'u', post: noToken.post }).initialize();
  assert.equal('X-Mias-Token' in noToken.calls[0]!.headers, false);
});

test('generate auto-initializes, unwraps the content envelope', async () => {
  const srv = fakeServer();
  const c = new McpClient({ url: 'u', post: srv.post });
  const out = await c.generate('hello', 64);
  assert.equal(out, 'echo:hello');
  assert.equal(srv.calls[0]!.req.method, 'initialize'); // lazy handshake happened first
  const call = srv.calls.find(x => x.req.method === 'tools/call')!;
  assert.deepEqual(call.req.params?.arguments, { prompt: 'hello', max_tokens: '64' });
});

test('tool errors surface as isError / thrown generate', async () => {
  const post: HttpPost = async (_u, body) => {
    const req = JSON.parse(body) as JsonRpcRequest;
    if (req.method === 'initialize') {
      return JSON.stringify({
        jsonrpc: '2.0', id: req.id,
        result: { protocolVersion: MCP_PROTOCOL_VERSION, serverInfo: { name: 's', version: 'v' }, capabilities: {} },
      });
    }
    if (req.method === 'notifications/initialized') return JSON.stringify({ jsonrpc: '2.0', result: {} });
    return JSON.stringify({
      jsonrpc: '2.0', id: req.id,
      result: { content: [{ type: 'text', text: 'boom' }], isError: true },
    });
  };
  const c = new McpClient({ url: 'u', post });
  const res = await c.callTool('generate', { prompt: 'x' });
  assert.equal(res.isError, true);
  await assert.rejects(() => c.generate('x'), /boom/);
});

test('listTools parses the tool catalogue', async () => {
  const srv = fakeServer();
  const c = new McpClient({ url: 'u', post: srv.post });
  const tools = await c.listTools();
  assert.deepEqual(tools, [{ name: 'generate', description: 'Generate text' }]);
});
