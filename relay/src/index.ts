/**
 * Mias Bridge rendezvous relay (bridge/docs/02 section 2, 07-S1/S5/S21/S30).
 *
 * A zero-knowledge byte forwarder: it matches two peers by a high-entropy
 * `rendezvous_id` + labeled `channel`, forwards opaque frames between them, and
 * never terminates the E2EE — it cannot read prompts, code, or output. This is
 * the Python PoC's relay, graduated to production TypeScript with the fourth-pass
 * hardening:
 *   - **Slot tokens (07-S30):** a peer gets an opaque token on first attach and
 *     must present it to *reclaim* its slot — so knowing the rendezvous_id alone
 *     can't evict a live peer.
 *   - **Room TTL + caps (07-S21):** idle/unpaired rooms expire; per-IP limits on
 *     connections and room creation bound abuse.
 *   - **Labeled channels (07-S37):** interactive vs bulk ride separate byte-pipes
 *     under one rendezvous_id, so a bulk transfer can't head-of-line-block tokens.
 */
import { WebSocketServer, WebSocket, type RawData } from 'ws';
import { randomBytes } from 'node:crypto';

export type PeerRole = 'host' | 'client';

export interface RelayHello {
  rid: string; // rendezvous_id
  role: PeerRole;
  channel?: string; // default 'c1'
  slotToken?: string; // required to reclaim an occupied slot
}

interface Slot {
  ws: WebSocket;
  token: string;
}

interface Room {
  slots: Map<string, Slot>; // key: `${role}/${channel}`
  createdAt: number;
  lastActivity: number;
}

export interface RelayOptions {
  port?: number;
  host?: string;
  /** Idle room TTL (ms). Rooms with no traffic are reaped. */
  roomTtlMs?: number;
  /** Max concurrent connections per remote IP. */
  maxConnPerIp?: number;
  /** Max rooms a single IP may create in the ttl window. */
  maxRoomsPerIp?: number;
  /** Observability counter hook (no content, ever). */
  onMetric?: (name: string, value: number) => void;
}

export class Relay {
  private wss: WebSocketServer;
  private rooms = new Map<string, Room>();
  private connPerIp = new Map<string, number>();
  private roomsPerIp = new Map<string, number>();
  private reaper: ReturnType<typeof setInterval>;
  private readonly opts: Required<Omit<RelayOptions, 'onMetric'>> & Pick<RelayOptions, 'onMetric'>;

  /** Frames the relay was able to JSON-parse — a zero-knowledge assertion; stays 0. */
  parseableFrames = 0;
  reclaims = 0;

  constructor(options: RelayOptions = {}) {
    this.opts = {
      port: options.port ?? 8790,
      host: options.host ?? '127.0.0.1',
      roomTtlMs: options.roomTtlMs ?? 5 * 60_000,
      maxConnPerIp: options.maxConnPerIp ?? 64,
      maxRoomsPerIp: options.maxRoomsPerIp ?? 256,
      onMetric: options.onMetric,
    };
    this.wss = new WebSocketServer({ host: this.opts.host, port: this.opts.port });
    this.wss.on('connection', (ws, req) => this.onConnection(ws, (req.socket.remoteAddress ?? '?')));
    this.reaper = setInterval(() => this.reap(), Math.min(this.opts.roomTtlMs, 30_000));
    this.reaper.unref?.();
  }

  get port(): number {
    const addr = this.wss.address();
    return typeof addr === 'object' && addr ? addr.port : this.opts.port;
  }

  private onConnection(ws: WebSocket, ip: string): void {
    const conns = (this.connPerIp.get(ip) ?? 0) + 1;
    if (conns > this.opts.maxConnPerIp) {
      ws.close(1013, 'rate limit');
      return;
    }
    this.connPerIp.set(ip, conns);

    let bound: { rid: string; slotKey: string; token: string } | null = null;

    ws.once('message', (raw: RawData) => {
      let hello: RelayHello;
      try {
        hello = JSON.parse(raw.toString()) as RelayHello;
      } catch {
        ws.close(1008, 'bad hello');
        return;
      }
      const channel = hello.channel ?? 'c1';
      const slotKey = `${hello.role}/${channel}`;
      const room = this.getOrCreateRoom(hello.rid, ip);
      if (!room) {
        ws.close(1013, 'room rate limit');
        return;
      }

      const existing = room.slots.get(slotKey);
      if (existing) {
        // Reclaim requires the slot token issued on first attach (07-S30).
        if (hello.slotToken !== existing.token) {
          ws.close(1008, 'slot occupied');
          return;
        }
        this.reclaims++;
        this.metric('reclaim', 1);
        try {
          existing.ws.close(1000, 'reclaimed');
        } catch {
          /* ignore */
        }
      }

      const token = existing?.token ?? randomBytes(16).toString('base64url');
      room.slots.set(slotKey, { ws, token });
      room.lastActivity = Date.now();
      bound = { rid: hello.rid, slotKey, token };
      ws.send(JSON.stringify({ type: 'relay-ack', slotToken: token }));

      const peerKey = `${hello.role === 'host' ? 'client' : 'host'}/${channel}`;
      ws.on('message', (data: RawData) => this.forward(hello.rid, peerKey, data));
    });

    ws.on('close', () => {
      this.connPerIp.set(ip, Math.max(0, (this.connPerIp.get(ip) ?? 1) - 1));
      if (!bound) return;
      const room = this.rooms.get(bound.rid);
      const slot = room?.slots.get(bound.slotKey);
      // Only clear the slot if it's still ours (a reclaim may have replaced it).
      if (room && slot && slot.ws === ws) {
        room.slots.delete(bound.slotKey);
        if (room.slots.size === 0) this.rooms.delete(bound.rid);
      }
    });
  }

  private forward(rid: string, peerKey: string, data: RawData): void {
    const room = this.rooms.get(rid);
    if (!room) return;
    room.lastActivity = Date.now();
    // Zero-knowledge assertion: a real frame is AEAD ciphertext and must NOT
    // parse as JSON. We count any that do (must stay 0).
    try {
      JSON.parse(data.toString());
      this.parseableFrames++;
    } catch {
      /* expected: opaque ciphertext */
    }
    const peer = room.slots.get(peerKey);
    if (peer && peer.ws.readyState === WebSocket.OPEN) {
      peer.ws.send(data);
    }
  }

  private getOrCreateRoom(rid: string, ip: string): Room | null {
    let room = this.rooms.get(rid);
    if (room) return room;
    const created = (this.roomsPerIp.get(ip) ?? 0) + 1;
    if (created > this.opts.maxRoomsPerIp) return null;
    this.roomsPerIp.set(ip, created);
    room = { slots: new Map(), createdAt: Date.now(), lastActivity: Date.now() };
    this.rooms.set(rid, room);
    this.metric('room_created', 1);
    return room;
  }

  private reap(): void {
    const now = Date.now();
    for (const [rid, room] of this.rooms) {
      if (now - room.lastActivity > this.opts.roomTtlMs) {
        for (const slot of room.slots.values()) {
          try {
            slot.ws.close(1000, 'expired');
          } catch {
            /* ignore */
          }
        }
        this.rooms.delete(rid);
        this.metric('room_expired', 1);
      }
    }
    // Reset per-IP room-creation counters each window.
    this.roomsPerIp.clear();
  }

  private metric(name: string, value: number): void {
    this.opts.onMetric?.(name, value);
  }

  get roomCount(): number {
    return this.rooms.size;
  }

  async close(): Promise<void> {
    clearInterval(this.reaper);
    await new Promise<void>(resolve => this.wss.close(() => resolve()));
  }
}
