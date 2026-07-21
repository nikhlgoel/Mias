#!/usr/bin/env node
/**
 * Standalone session-server entrypoint (the CLI form used before the VS Code
 * extension hosts it). Env: RELAY_URL, RENDEZVOUS_ID, PAIRING_CODE, MCP_URL,
 * MCP_TOKEN. The extension supplies these programmatically instead.
 */
import { SessionServer } from './index.ts';
import { nodeCryptoProvider } from '@mias/bridge-protocol/node';

const server = new SessionServer({
  relayUrl: process.env.RELAY_URL ?? 'wss://relay.mias.local',
  rendezvousId: process.env.RENDEZVOUS_ID ?? '',
  code: process.env.PAIRING_CODE ?? '',
  mcpUrl: process.env.MCP_URL ?? 'http://127.0.0.1:8401/rpc',
  mcpToken: process.env.MCP_TOKEN,
  crypto: nodeCryptoProvider,
});

server
  .start()
  .then(() => console.log('[session-server] paired; serving prompts'))
  .catch((err: unknown) => {
    console.error('[session-server] failed to start:', err);
    process.exit(1);
  });

for (const sig of ['SIGINT', 'SIGTERM'] as const) {
  process.on(sig, () => {
    server.stop();
    process.exit(0);
  });
}
