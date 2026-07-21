/**
 * SessionServer — the PC side of the Bridge (bridge/docs/02).
 *
 * Dials out to the relay, terminates E2EE (BridgePeer, host role), and serves
 * the phone's prompts by proxying to the local MCP inference worker
 * (desktop/server.py) over localhost, streaming tokens back over sequenced,
 * resumable frames. The job runs on the PC regardless of the phone's link.
 *
 * NOTE (bridge/docs/07-S28): the worker must be bound to 127.0.0.1 with a random
 * per-launch token — the session server is the only remote path in.
 */
import { WebSocket } from 'ws';
import {
  BridgePeer,
  McpClient,
  Streams,
  type CryptoProvider,
  type OutgoingFrame,
  type RawTransport,
} from '@mias/bridge-protocol';
import { connectToRelay, type RelayHello } from './relay-connection.ts';

export interface SessionServerOptions {
  relayUrl: string;
  rendezvousId: string;
  /** Pairing code (first contact) — seeds the SecureChannel. */
  code: string;
  /** Local MCP worker endpoint, e.g. http://127.0.0.1:8401/rpc */
  mcpUrl: string;
  mcpToken?: string;
  crypto: CryptoProvider;
  /** Coalesce generated text into ~this many chars per stream frame. */
  chunkChars?: number;
  /** Test seam: inject a transport (skip the real relay dial). */
  transportFactory?: (hello: RelayHello) => Promise<RawTransport>;
  /** Test seam: inject an MCP client (skip the real worker). */
  mcpClient?: McpClient;
  WebSocketImpl?: typeof WebSocket;
}

export class SessionServer {
  private readonly opts: SessionServerOptions;
  private peer: BridgePeer | null = null;
  private mcp: McpClient;

  constructor(opts: SessionServerOptions) {
    this.opts = opts;
    this.mcp = opts.mcpClient ?? new McpClient({ url: opts.mcpUrl, token: opts.mcpToken });
  }

  /** Dial the relay, complete pairing, and start serving prompts. */
  async start(): Promise<void> {
    const hello: RelayHello = { rid: this.opts.rendezvousId, role: 'host', channel: 'c1' };
    const transport =
      this.opts.transportFactory != null
        ? await this.opts.transportFactory(hello)
        : await connectToRelay(this.opts.relayUrl, hello, this.opts.WebSocketImpl);

    const peer = new BridgePeer(transport, this.opts.crypto, 'host', {
      rendezvousId: this.opts.rendezvousId,
      relayOrigin: this.opts.relayUrl,
      code: this.opts.code,
    });
    this.peer = peer;
    peer.onFrame(frame => void this.onFrame(frame));
    peer.onError(() => {});
    await peer.connect();
  }

  private async onFrame(frame: OutgoingFrame): Promise<void> {
    if (frame.type === 'rpc') {
      const payload = frame.payload as { method?: string; params?: { prompt?: string }; id?: number };
      if (payload.method === 'generate' && typeof payload.params?.prompt === 'string') {
        await this.handleGenerate(payload.params.prompt, payload.id ?? 0);
      }
    } else if (frame.type === 'ctrl') {
      const payload = frame.payload as { kind?: string; cursors?: Record<string, number> };
      if (payload.kind === 'resume' && payload.cursors) {
        this.peer?.replay(payload.cursors);
      }
    }
  }

  /**
   * Proxy a prompt to the MCP worker and stream the answer back. The worker is
   * single-shot today (desktop/server.py); we coalesce the result into stream
   * frames so the phone renders progressively. Real token streaming arrives when
   * the worker gains Streamable HTTP (bridge/docs/09 section 1).
   */
  private async handleGenerate(prompt: string, id: number): Promise<void> {
    const peer = this.peer;
    if (!peer) return;
    let text: string;
    try {
      text = await this.mcp.generate(prompt);
    } catch (err) {
      peer.send(Streams.control, 'rpc_result', {
        id,
        error: { code: -32000, message: err instanceof Error ? err.message : 'generate failed' },
      });
      return;
    }
    const chunkChars = this.opts.chunkChars ?? 24;
    for (let i = 0; i < text.length; i += chunkChars) {
      peer.send(Streams.tokens, 'stream', { chunk: text.slice(i, i + chunkChars) });
    }
    peer.send(Streams.control, 'rpc_result', { id, result: { done: true } });
  }

  stop(): void {
    this.peer?.close();
  }
}

export { connectToRelay } from './relay-connection.ts';
export type { RelayHello } from './relay-connection.ts';
