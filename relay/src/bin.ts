#!/usr/bin/env node
/** Standalone relay entrypoint. `PORT` / `HOST` env override the defaults. */
import { Relay } from './index.ts';

const relay = new Relay({
  port: Number.parseInt(process.env.PORT ?? '8790', 10),
  host: process.env.HOST ?? '0.0.0.0',
  onMetric: (name, value) => console.log(`[relay] ${name} += ${value}`),
});

console.log(`[relay] listening on ${process.env.HOST ?? '0.0.0.0'}:${relay.port} (zero-knowledge forwarder)`);

for (const sig of ['SIGINT', 'SIGTERM'] as const) {
  process.on(sig, () => {
    console.log('[relay] shutting down');
    void relay.close().then(() => process.exit(0));
  });
}
