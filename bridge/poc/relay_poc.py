#!/usr/bin/env python3
"""Mias Remote Bridge connectivity PoC (v3 — fourth-pass: bugs + wiring).

RELAY  : zero-knowledge forwarder. Routes by HIGH-ENTROPY rendezvous_id (NOT the human code).
         Reconnecting peer reclaims its slot (last-writer-wins). NOTE: real relay requires an
         opaque slot token to reclaim (07-S30); modeled here as a passthrough field only.
PC HOST: dials OUT. Terminates E2EE. Generates each token EXACTLY ONCE (monotonic gen_ptr) and
         keeps a bounded RAM resend buffer + a full persisted session log. On resume it REPLAYS
         the exact stored frames (07-S34) — it never regenerates model output. Generation keeps
         running while the client is detached and spills to the log (07-S36), instead of pausing.
         Side-effecting rpc dedup by idempotency key, persisted for the session (07-S12/S39).
MOBILE : dials OUT with {rendezvous_id, code}; on drop, reconnects with exponential backoff+jitter
         (07-S18) and sends resume{from=next_needed}. Dedups by per-stream seq (07-S35).

Crypto here are PoC stand-ins (real design in docs/03):
  key = HKDF(code, salt=rendezvous_id)  ==  balanced-PAKE (CPace/SPAKE2) + channel binding.
  AEAD = AES-GCM with COUNTER nonces per (key,direction) — no random-nonce reuse over a long stream.
Burn-on-fail is HANDSHAKE-ONLY here (07-S31): a mid-session decrypt failure is dropped+counted,
  it does NOT tear the session down (that would be a trivial one-frame DoS).
"""
import asyncio, json, os, time, secrets, statistics, struct, random
from collections import OrderedDict
import websockets
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.hkdf import HKDF
from cryptography.hazmat.primitives import hashes

WAN_MS   = float(os.environ.get("WAN_MS", "40"))
N_TOKENS = int(os.environ.get("N_TOKENS", "200"))
RELAY_PORT = int(os.environ.get("RELAY_PORT", "8799"))
BUF_CAP  = int(os.environ.get("BUF_CAP", "32"))   # small RAM buffer to force log-fallback on long detach
def now(): return time.perf_counter()
async def wan(): await asyncio.sleep(WAN_MS / 1000.0)

def derive_key(code, rid):
    return HKDF(algorithm=hashes.SHA256(), length=32, salt=rid.encode(), info=b"mias-bridge").derive(code.encode())

class Sealer:  # counter-nonce AEAD
    def __init__(self, key, direction): self.a = AESGCM(key); self.ctr = 0; self.dir = direction
    def seal(self, obj):
        n = struct.pack(">I", self.dir) + struct.pack(">Q", self.ctr); self.ctr += 1
        return n + self.a.encrypt(n, json.dumps(obj).encode(), None)
    def open(self, blob): return json.loads(self.a.decrypt(blob[:12], blob[12:], None).decode())

class Relay:
    def __init__(self): self.rooms = {}; self.relay_could_read = 0; self.reclaims = 0
    async def handler(self, ws):
        hello = json.loads(await ws.recv()); role = hello["role"]; rid = hello["rid"]
        room = self.rooms.setdefault(rid, {"host": None, "client": None})
        if room[role] is not None:
            self.reclaims += 1
            try: await room[role].close()
            except Exception: pass
        room[role] = ws; await ws.send(json.dumps({"type": "relay-ack"}))
        peer = "client" if role == "host" else "host"
        try:
            async for raw in ws:
                try: json.loads(raw); self.relay_could_read += 1   # E2EE proof: stays 0 (frames are AEAD bytes)
                except Exception: pass
                t = self.rooms.get(rid, {}).get(peer)
                if t:
                    async def deliver(tt, r):
                        await wan()
                        try: await tt.send(r)
                        except Exception: pass
                    asyncio.create_task(deliver(t, raw))
        except websockets.ConnectionClosed: pass
        finally:
            if self.rooms.get(rid, {}).get(role) is ws: self.rooms[rid][role] = None
relay = Relay()

async def pc_host(code, rid, ready, stop, hstate):
    key = derive_key(code, rid); tx = Sealer(key, 1); rx = Sealer(key, 2)
    log = {}              # full persisted session log: seq -> frame (never evicted) — models on-disk spill (S36)
    buf = OrderedDict()   # bounded RAM resend buffer: seq -> frame (FIFO evict at BUF_CAP) (S13)
    gen_ptr = [0]         # next token to GENERATE — monotonic, never rewinds → proves no regeneration (S34)
    send_cursor = [0]     # next seq to TRANSMIT — rewinds to {from} on resume
    streaming = [False]
    served_from_log = [0]; decrypt_fails = [0]; idem_runs = [0]
    idem_results = {}     # idempotency key -> result; persists for the session (S39)

    def remember(seq, frame):
        log[seq] = frame; buf[seq] = frame
        while len(buf) > BUF_CAP: buf.popitem(last=False)   # evict oldest from RAM

    def fetch(seq):
        if seq in buf: return buf[seq], "buf"
        if seq in log: served_from_log[0] += 1; return log[seq], "log"   # spilled → served from persisted log
        return None, None   # MUST NOT happen — would imply we tried to regenerate

    async with websockets.connect(f"ws://127.0.0.1:{RELAY_PORT}") as ws:
        await ws.send(json.dumps({"role": "host", "rid": rid})); await ws.recv(); ready.set()

        async def reader():
            handshook = False
            while not stop.is_set():
                try: raw = await asyncio.wait_for(ws.recv(), timeout=0.5)
                except asyncio.TimeoutError: continue
                except websockets.ConnectionClosed: return
                try: msg = rx.open(raw)
                except Exception:
                    if not handshook: return            # S31: handshake-phase failure burns the session
                    decrypt_fails[0] += 1; continue     # S31: mid-session failure drops the frame, never fatal
                handshook = True
                t = msg.get("type")
                if t in ("start", "resume"):
                    send_cursor[0] = msg["from"]; streaming[0] = True
                elif t == "rpc" and msg.get("method") == "run_build":
                    idem = msg["idem"]
                    if idem in idem_results:
                        res = idem_results[idem]        # dedup — do NOT re-run the side effect
                    else:
                        idem_runs[0] += 1
                        res = {"build": "ok", "run_no": idem_runs[0]}
                        idem_results[idem] = res
                    try: await ws.send(tx.seal({"type": "rpc_result", "id": msg.get("id"), "idem": idem, "result": res}))
                    except Exception: pass

        async def generator():
            # Generates each token once; keeps running even with no client attached (S36 — job runs while away).
            while not stop.is_set():
                if streaming[0] and gen_ptr[0] < N_TOKENS:
                    i = gen_ptr[0]
                    remember(i, {"type": "token", "stream_id": "tokens", "seq": i, "text": f"tok{i} "})
                    gen_ptr[0] += 1
                    await asyncio.sleep(0.001)
                else:
                    await asyncio.sleep(0.002)

        async def transmitter():
            sent_done = False
            while not stop.is_set():
                if streaming[0] and send_cursor[0] < gen_ptr[0]:
                    frame, _src = fetch(send_cursor[0])
                    if frame is None:
                        raise RuntimeError(f"seq {send_cursor[0]} not in buffer or log — would require regeneration (S34)")
                    try: await ws.send(tx.seal(frame))
                    except Exception: await asyncio.sleep(0.005); continue
                    send_cursor[0] += 1; sent_done = False
                    await asyncio.sleep(0.001)
                elif streaming[0] and gen_ptr[0] >= N_TOKENS and send_cursor[0] >= N_TOKENS and not sent_done:
                    try: await ws.send(tx.seal({"type": "done", "stream_id": "tokens", "total": N_TOKENS}))
                    except Exception: pass
                    sent_done = True; await asyncio.sleep(0.002)
                else:
                    await asyncio.sleep(0.002)

        try:
            await asyncio.gather(reader(), generator(), transmitter())
        finally:
            hstate.update(served_from_log=served_from_log[0], decrypt_fails=decrypt_fails[0],
                          idem_runs=idem_runs[0], gen_ptr=gen_ptr[0])

async def mobile(code, rid, res, drops=None, detach=None):
    """Streaming client. drops = seqs at which to flap (immediate reconnect).
       detach = (seq, secs): disconnect at `seq` and stay away `secs` so the host outruns its RAM
       buffer and must replay evicted frames from the persisted log (S36/S34)."""
    drops = set(drops or []); fired = set()
    detach_at, detach_secs = (detach if detach else (None, 0.0)); detached = [False]
    key = derive_key(code, rid); tx = Sealer(key, 2); rx = Sealer(key, 1)
    recv_times = []; got = set(); next_needed = 0; flaps = 0
    attempts = [0]; backoff_total = [0.0]

    async def connect():
        w = await websockets.connect(f"ws://127.0.0.1:{RELAY_PORT}")
        await w.send(json.dumps({"role": "client", "rid": rid})); await w.recv(); return w

    async def reconnect():
        # S18: exponential backoff with jitter (also the per-client half of reconnect-storm control).
        delay = min(0.2, 0.02 * (2 ** attempts[0])) * (0.5 + random.random())
        attempts[0] += 1; backoff_total[0] += delay
        await asyncio.sleep(delay)
        return await connect()

    t0 = now(); ws = await connect(); res["pair_ms"] = (now() - t0) * 1000
    t_req = now(); await ws.send(tx.seal({"type": "start", "from": 0})); reconnected = False
    while True:
        try: raw = await ws.recv()
        except websockets.ConnectionClosed:
            ws = await reconnect(); await ws.send(tx.seal({"type": "resume", "from": next_needed}))
            reconnected = True; continue
        try: msg = rx.open(raw)
        except Exception: continue
        if msg.get("type") == "token":
            s = msg["seq"]
            if s not in got:
                got.add(s)
                if not recv_times: res["ttft_ms"] = (now() - t_req) * 1000
                recv_times.append(now())
            while next_needed in got: next_needed += 1
            if s in drops and s not in fired:
                fired.add(s); flaps += 1; await ws.close()
            elif detach_at is not None and s >= detach_at and not detached[0]:
                detached[0] = True; flaps += 1
                await ws.close()
                await asyncio.sleep(detach_secs)         # host keeps generating + spilling to log while away
                ws = await reconnect(); await ws.send(tx.seal({"type": "resume", "from": next_needed}))
                reconnected = True
        elif msg.get("type") == "done":
            if next_needed >= N_TOKENS: break
            else: await ws.send(tx.seal({"type": "resume", "from": next_needed}))   # fill remaining gaps
    gaps = [(recv_times[i] - recv_times[i - 1]) * 1000 for i in range(1, len(recv_times))]
    target = statistics.median(gaps) / 1000 if gaps else 0.0; rel = []; t = recv_times[0] if recv_times else 0
    for at in recv_times:
        t = max(at, t + target); rel.append(t)
    rel_gaps = [(rel[i] - rel[i - 1]) * 1000 for i in range(1, len(rel))]
    res.update(unique=len(got), in_order=(sorted(got) == list(range(N_TOKENS))), reconnected=reconnected,
               flaps=flaps, reconnect_attempts=attempts[0], backoff_total_ms=round(backoff_total[0] * 1000, 1),
               median_gap_ms=round(statistics.median(gaps), 2) if gaps else None,
               p95_gap_ms=round(sorted(gaps)[int(len(gaps) * 0.95)], 2) if gaps else None,
               jitter_p95_ms=round(sorted(rel_gaps)[int(len(rel_gaps) * 0.95)], 2) if rel_gaps else None)
    await ws.close()

async def mobile_rpc(code, rid, res):
    """Idempotent side-effecting rpc across a reconnect (07-S12/S39): send run_build, drop BEFORE the
       result arrives, reconnect, resend the SAME idempotency key. Host must execute exactly once."""
    key = derive_key(code, rid); tx = Sealer(key, 2); rx = Sealer(key, 1)
    idem = secrets.token_hex(8)

    async def connect():
        w = await websockets.connect(f"ws://127.0.0.1:{RELAY_PORT}")
        await w.send(json.dumps({"role": "client", "rid": rid})); await w.recv(); return w

    ws = await connect()
    await ws.send(tx.seal({"type": "rpc", "method": "run_build", "id": 1, "idem": idem}))
    await ws.close()                                   # simulate losing the result before it lands
    await asyncio.sleep(0.05)
    ws = await connect()
    await ws.send(tx.seal({"type": "rpc", "method": "run_build", "id": 2, "idem": idem}))   # resend SAME key
    result = None
    while result is None:
        try: raw = await ws.recv()
        except websockets.ConnectionClosed:
            ws = await connect(); await ws.send(tx.seal({"type": "rpc", "method": "run_build", "id": 3, "idem": idem})); continue
        try: msg = rx.open(raw)
        except Exception: continue
        if msg.get("type") == "rpc_result": result = msg["result"]
    res["build_run_no"] = result["run_no"]
    await ws.close()

async def scenario(label, drops=None, detach=None):
    rid = secrets.token_urlsafe(16); code = secrets.token_hex(4).upper()
    ready = asyncio.Event(); stop = asyncio.Event(); res = {}; hstate = {}
    h = asyncio.create_task(pc_host(code, rid, ready, stop, hstate)); await asyncio.wait_for(ready.wait(), 5)
    t0 = now(); await asyncio.wait_for(mobile(code, rid, res, drops=drops, detach=detach), 20)
    res["total_ms"] = round((now() - t0) * 1000, 1)
    stop.set(); h.cancel()
    await asyncio.gather(h, return_exceptions=True)   # safely awaits cancelled task (CancelledError is BaseException)
    print(f"\n=== {label} (WAN one-way={WAN_MS}ms, tokens={N_TOKENS}, buf_cap={BUF_CAP}) ===")
    print(f"  pairing handshake          : {res['pair_ms']:.1f} ms")
    print(f"  TTFT (req -> first token)  : {res['ttft_ms']:.1f} ms")
    print(f"  tokens unique received     : {res['unique']} / {N_TOKENS}")
    print(f"  all in order, no loss/dup  : {res['in_order']}")
    print(f"  reconnected mid-stream     : {res['reconnected']}  (flaps={res.get('flaps',0)})")
    print(f"  reconnect attempts/backoff : {res['reconnect_attempts']} / {res['backoff_total_ms']} ms (exp+jitter, S18)")
    print(f"  median / raw-p95 gap       : {res['median_gap_ms']} / {res['p95_gap_ms']} ms")
    print(f"  jitter-buffered p95 gap    : {res['jitter_p95_ms']} ms  (smoothed render cadence)")
    print(f"  host gen_ptr reached       : {hstate.get('gen_ptr')} / {N_TOKENS}  (gen runs even while detached, S36)")
    print(f"  frames replayed FROM LOG   : {hstate.get('served_from_log')}  (>0 == survived RAM-buffer eviction, S34/S36)")
    print(f"  mid-session decrypt drops  : {hstate.get('decrypt_fails')}  (dropped, not fatal - S31)")
    print(f"  relay frames it could read : {relay.relay_could_read}  (0 == zero-knowledge)")
    print(f"  relay slot reclaims        : {relay.reclaims}")

async def scenario_rpc(label):
    rid = secrets.token_urlsafe(16); code = secrets.token_hex(4).upper()
    ready = asyncio.Event(); stop = asyncio.Event(); res = {}; hstate = {}
    h = asyncio.create_task(pc_host(code, rid, ready, stop, hstate)); await asyncio.wait_for(ready.wait(), 5)
    await asyncio.wait_for(mobile_rpc(code, rid, res), 20)
    stop.set(); h.cancel(); await asyncio.gather(h, return_exceptions=True)
    print(f"\n=== {label} (WAN one-way={WAN_MS}ms) ===")
    print(f"  run_build sent 2x (drop+resume) across reconnect")
    print(f"  host side-effect executions: {hstate.get('idem_runs')}  (MUST be 1 - idempotent, S12/S39)")
    print(f"  client saw build run_no    : {res['build_run_no']}  (== 1, the deduped result)")

async def main():
    srv = await websockets.serve(relay.handler, "127.0.0.1", RELAY_PORT)
    await scenario("Scenario A: clean stream over WAN")
    relay.relay_could_read = 0
    await scenario("Scenario B: mobile drops mid-stream, buffered resume", drops=[N_TOKENS // 2])
    relay.relay_could_read = 0
    flaps = sorted(random.sample(range(5, N_TOKENS - 5), min(6, N_TOKENS - 10)))
    await scenario(f"Scenario C: {len(flaps)} random flaps in one stream", drops=flaps)
    relay.relay_could_read = 0
    await scenario("Scenario D: long detach -> host outruns RAM buffer -> replay from persisted log",
                   detach=(20, 0.6))
    relay.relay_could_read = 0
    await scenario_rpc("Scenario E: idempotent side-effecting rpc across reconnect")
    srv.close(); await srv.wait_closed()
asyncio.run(asyncio.wait_for(main(), timeout=40))
