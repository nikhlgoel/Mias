/**
 * Mias Bridge protocol — shared TypeScript core.
 *
 * Spec source of truth: bridge/docs/04 (frames, session lifecycle) and
 * bridge/docs/08 (per-stream cursors, frame_id dedup, resume math). This package
 * is consumed by the mobile app (React Native) and the PC extension — both ends
 * speak exactly this code. Zero runtime dependencies.
 */

export * from './crypto.ts';
export * from './peer.ts';
export { nobleCryptoProvider } from './noble-crypto.ts';
export { CpacePake } from './cpace.ts';

// ── Versions ────────────────────────────────────────────────────────────────

/** Bridge envelope version, negotiated in ctrl:hello (bridge/docs/04 section 7). */
export const BRIDGE_PROTOCOL_VERSION = 1;

/** MCP JSON-RPC protocol version the LAN worker speaks (desktop/server.py). */
export const MCP_PROTOCOL_VERSION = '2025-03-26';

// ── Frame model (bridge/docs/04 section 2) ──────────────────────────────────
//
// Two independent counters — do not conflate (07-S35):
//  - `frameId`: session-global, monotonic per sender. TRANSPORT dedup only
//    (a frame seen on two transports during make-before-break renders once).
//  - `streamId` + `seq`: ordering, gap-detection, and resume are PER logical
//    stream; acks are a per-stream cursor map.

export type FrameType = 'rpc' | 'rpc_result' | 'stream' | 'live' | 'ctrl';

export interface FrameHeader {
  frameId: number;
  streamId: string;
  seq: number;
  /** Per-stream cumulative cursors: streamId -> next seq this side needs. */
  ack: Record<string, number>;
  type: FrameType;
}

/** An encrypted frame on the wire: header + AEAD nonce + ciphertext. */
export interface WireFrame extends FrameHeader {
  nonce: string;
  ct: string;
}

/** Well-known logical stream ids (bridge/docs/08 section 3). */
export const Streams = {
  control: 'ctrl',
  tokens: 'tokens',
  logs: 'logs',
  build: 'build',
  files: 'files',
  telemetry: 'telemetry',
} as const;

// ── Sender side: per-stream sequencing + bounded resend buffer ──────────────

export interface OutgoingFrame extends FrameHeader {
  payload: unknown;
}

/**
 * Stamps outgoing frames (global frameId + per-stream seq) and retains
 * un-acked frames per stream for replay-on-resume. Replay serves the EXACT
 * stored frames — never regenerated content (07-S34).
 */
export class FrameSender {
  private nextFrameId = 0;
  private nextSeq = new Map<string, number>();
  private unacked = new Map<string, OutgoingFrame[]>();

  send(streamId: string, type: FrameType, payload: unknown, ack: Record<string, number> = {}): OutgoingFrame {
    const seq = this.nextSeq.get(streamId) ?? 0;
    this.nextSeq.set(streamId, seq + 1);
    const frame: OutgoingFrame = {
      frameId: this.nextFrameId++,
      streamId,
      seq,
      ack,
      type,
      payload,
    };
    const buf = this.unacked.get(streamId) ?? [];
    buf.push(frame);
    this.unacked.set(streamId, buf);
    return frame;
  }

  /** Cumulative ack for one stream: drop everything below `nextNeeded`. */
  onAck(streamId: string, nextNeeded: number): void {
    const buf = this.unacked.get(streamId);
    if (!buf) return;
    this.unacked.set(streamId, buf.filter(f => f.seq >= nextNeeded));
  }

  /** Frames to replay for a resume{cursors} request — exact stored frames, in order. */
  replayFrom(cursors: Record<string, number>): OutgoingFrame[] {
    const out: OutgoingFrame[] = [];
    for (const [streamId, from] of Object.entries(cursors)) {
      for (const f of this.unacked.get(streamId) ?? []) {
        if (f.seq >= from) out.push(f);
      }
    }
    return out.sort((a, b) => a.frameId - b.frameId);
  }

  /** Buffered (un-acked) frame count for a stream — backpressure signal (07-S13). */
  buffered(streamId: string): number {
    return this.unacked.get(streamId)?.length ?? 0;
  }
}

// ── Receiver side: per-stream cursors + frameId dedup ───────────────────────

export type Accept<T extends FrameHeader> =
  | { kind: 'deliver'; frames: T[] }
  | { kind: 'duplicate' }
  | { kind: 'buffered' };

/**
 * Receiver logic per bridge/docs/08 section 4: dedup on `frameId` (transport
 * overlap), order + resume per `streamId`/`seq` with a `nextNeeded` cursor and
 * a small reorder buffer. `accept()` returns the frames now deliverable, in
 * order — exactly-once per frame.
 */
export class FrameReceiver<T extends FrameHeader> {
  private seenFrameIds = new Set<number>();
  private nextNeeded = new Map<string, number>();
  private held = new Map<string, Map<number, T>>();

  accept(frame: T): Accept<T> {
    if (this.seenFrameIds.has(frame.frameId)) return { kind: 'duplicate' };
    this.seenFrameIds.add(frame.frameId);

    // Track the cursor from first SIGHT of a stream (not first delivery): resume
    // cursors must include a stream whose early frames were all lost, else the
    // sender never replays its seq 0.
    const cursor = this.nextNeeded.get(frame.streamId) ?? 0;
    this.nextNeeded.set(frame.streamId, cursor);
    if (frame.seq < cursor) return { kind: 'duplicate' }; // already delivered via another path

    const held = this.held.get(frame.streamId) ?? new Map<number, T>();
    this.held.set(frame.streamId, held);
    held.set(frame.seq, frame);

    if (frame.seq > cursor) return { kind: 'buffered' }; // gap — wait or resume

    const frames: T[] = [];
    let next = cursor;
    while (held.has(next)) {
      frames.push(held.get(next)!);
      held.delete(next);
      next++;
    }
    this.nextNeeded.set(frame.streamId, next);
    return { kind: 'deliver', frames };
  }

  /** Cursor map to send as `ack` / in ctrl:resume (bridge/docs/04 section 3). */
  cursors(): Record<string, number> {
    return Object.fromEntries(this.nextNeeded);
  }
}

// ── Transport abstraction (bridge/docs/05 section 3, 08 section 10) ─────────

export type TransportState =
  | 'connecting'
  | 'connected'
  | 'degraded'
  | 'reconnecting'
  | 'closed';

/**
 * The seam every physical path implements: relay WSS (T1), WebRTC (T2),
 * Tailscale (T3). The protocol layer never knows which is active. First real
 * framed implementation lands with the relay in stage S5; the LAN MCP path
 * below is the pre-Bridge offload transport.
 */
export interface BridgeTransport {
  connect(): Promise<void>;
  send(data: string | ArrayBuffer): void;
  onMessage(cb: (data: string | ArrayBuffer) => void): void;
  onStateChange(cb: (state: TransportState) => void): void;
  close(): void;
}

// ── MCP JSON-RPC client (LAN offload to desktop/server.py) ──────────────────
//
// Mirrors the Kotlin McpClient semantics: initialize → notifications/initialized
// → tools/call. Auth via X-Mias-Token header when a token is configured.

export interface JsonRpcRequest {
  jsonrpc: '2.0';
  id?: number;
  method: string;
  params?: Record<string, unknown>;
}

export interface JsonRpcResponse {
  jsonrpc: '2.0';
  id?: number;
  result?: unknown;
  error?: { code: number; message: string };
}

/** Minimal HTTP POST seam — inject fetch (RN and Node both provide it). */
export type HttpPost = (
  url: string,
  body: string,
  headers: Record<string, string>,
) => Promise<string>;

/** Default HttpPost built on global fetch. */
export const fetchPost: HttpPost = async (url, body, headers) => {
  const res = await fetch(url, { method: 'POST', headers, body });
  if (!res.ok) throw new Error(`HTTP ${res.status} from ${url}`);
  return res.text();
};

export interface McpServerInfo {
  name: string;
  version: string;
}

export interface McpToolResult {
  name: string;
  output: string;
  isError: boolean;
}

export interface McpClientOptions {
  /** e.g. http://192.168.1.20:8401/rpc */
  url: string;
  /** Shared secret; sent as X-Mias-Token when non-empty. */
  token?: string;
  post?: HttpPost;
}

export class McpClient {
  private url: string;
  private token: string;
  private post: HttpPost;
  private nextId = 0;
  private initialized = false;
  serverInfo: McpServerInfo | null = null;
  negotiatedProtocolVersion: string | null = null;

  constructor(opts: McpClientOptions) {
    this.url = opts.url;
    this.token = opts.token ?? '';
    this.post = opts.post ?? fetchPost;
  }

  private headers(): Record<string, string> {
    const h: Record<string, string> = { 'Content-Type': 'application/json' };
    if (this.token) h['X-Mias-Token'] = this.token;
    return h;
  }

  private async rpc(method: string, params?: Record<string, unknown>, notify = false): Promise<JsonRpcResponse> {
    const req: JsonRpcRequest = { jsonrpc: '2.0', method };
    if (!notify) req.id = ++this.nextId;
    if (params) req.params = params;
    const raw = await this.post(this.url, JSON.stringify(req), this.headers());
    return JSON.parse(raw) as JsonRpcResponse;
  }

  /** Full MCP initialize handshake (initialize + notifications/initialized). */
  async initialize(clientName = 'Mias Mobile', clientVersion = '0.1.0'): Promise<McpServerInfo> {
    const res = await this.rpc('initialize', {
      protocolVersion: MCP_PROTOCOL_VERSION,
      capabilities: {},
      clientInfo: { name: clientName, version: clientVersion },
    });
    if (res.error) throw new Error(`MCP initialization failed: ${res.error.message}`);
    const result = res.result as {
      protocolVersion?: string;
      serverInfo?: McpServerInfo;
    } | undefined;
    if (!result?.serverInfo) throw new Error('MCP initialization returned no serverInfo');
    this.serverInfo = result.serverInfo;
    this.negotiatedProtocolVersion = result.protocolVersion ?? null;
    await this.rpc('notifications/initialized', undefined, true);
    this.initialized = true;
    return this.serverInfo;
  }

  private async ensureInitialized(): Promise<void> {
    if (!this.initialized) await this.initialize();
  }

  async listTools(): Promise<Array<{ name: string; description: string }>> {
    await this.ensureInitialized();
    const res = await this.rpc('tools/list');
    if (res.error) throw new Error(`MCP error: ${res.error.message}`);
    const tools = (res.result as { tools?: Array<{ name?: string; description?: string }> })?.tools ?? [];
    return tools.map(t => ({ name: t.name ?? 'unknown', description: t.description ?? '' }));
  }

  /**
   * Call a tool; unwraps the MCP content envelope
   * `{ content: [{type:"text", text}], isError }` with raw-JSON fallback.
   */
  async callTool(name: string, args: Record<string, string>): Promise<McpToolResult> {
    await this.ensureInitialized();
    const res = await this.rpc('tools/call', { name, arguments: args });
    if (res.error) return { name, output: res.error.message, isError: true };
    const r = res.result as
      | { content?: Array<{ type?: string; text?: string }>; isError?: boolean }
      | undefined;
    if (!r) return { name, output: '', isError: true };
    const content = r.content;
    if (!content || content.length === 0) {
      return { name, output: JSON.stringify(r), isError: r.isError ?? false };
    }
    const text = content.map(c => c.text ?? '').filter(Boolean).join('\n');
    return { name, output: text, isError: r.isError ?? false };
  }

  /** Generate text on the desktop worker (single-shot; streaming arrives with the Bridge session server, S5). */
  async generate(prompt: string, maxTokens = 2048): Promise<string> {
    const result = await this.callTool('generate', {
      prompt,
      max_tokens: String(maxTokens),
    });
    if (result.isError) throw new Error(result.output);
    return result.output;
  }
}
