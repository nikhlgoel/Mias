import { test } from 'node:test';
import assert from 'node:assert/strict';
import { FrameSender, FrameReceiver, type OutgoingFrame } from '../src/index.ts';

test('sender stamps global frameId and per-stream seq independently', () => {
  const s = new FrameSender();
  const a0 = s.send('tokens', 'stream', 't0');
  const b0 = s.send('build', 'stream', 'b0');
  const a1 = s.send('tokens', 'stream', 't1');
  assert.deepEqual([a0.frameId, b0.frameId, a1.frameId], [0, 1, 2]);
  assert.deepEqual([a0.seq, b0.seq, a1.seq], [0, 0, 1]); // seq is per stream
});

test('receiver delivers in order, buffers gaps, releases on fill', () => {
  const s = new FrameSender();
  const f0 = s.send('tokens', 'stream', 0);
  const f1 = s.send('tokens', 'stream', 1);
  const f2 = s.send('tokens', 'stream', 2);
  const r = new FrameReceiver<OutgoingFrame>();

  assert.equal(r.accept(f0).kind, 'deliver');
  assert.equal(r.accept(f2).kind, 'buffered'); // gap: seq 1 missing
  const res = r.accept(f1);
  assert.equal(res.kind, 'deliver');
  assert.deepEqual((res as { frames: OutgoingFrame[] }).frames.map(f => f.seq), [1, 2]);
  assert.deepEqual(r.cursors(), { tokens: 3 });
});

test('frameId dedup: same frame via two transports renders once (07-S16/S35)', () => {
  const s = new FrameSender();
  const f = s.send('tokens', 'stream', 'x');
  const r = new FrameReceiver<OutgoingFrame>();
  assert.equal(r.accept(f).kind, 'deliver');
  assert.equal(r.accept(f).kind, 'duplicate');
  assert.equal(r.accept({ ...f }).kind, 'duplicate'); // structural copy too
});

test('streams are independent: a slow bulk stream never stalls token delivery', () => {
  const s = new FrameSender();
  const t0 = s.send('tokens', 'stream', 0);
  s.send('build', 'stream', 0); // build#0 never arrives at the receiver
  const b1 = s.send('build', 'stream', 1);
  const t1 = s.send('tokens', 'stream', 1);
  const r = new FrameReceiver<OutgoingFrame>();
  assert.equal(r.accept(t0).kind, 'deliver');
  assert.equal(r.accept(b1).kind, 'buffered'); // build waits on its own cursor
  assert.equal(r.accept(t1).kind, 'deliver'); // tokens keep flowing
  assert.deepEqual(r.cursors(), { tokens: 2, build: 0 });
});

test('resume replays the exact stored frames from per-stream cursors (07-S34)', () => {
  const s = new FrameSender();
  const frames = [0, 1, 2, 3].map(i => s.send('tokens', 'stream', `tok${i}`));
  s.onAck('tokens', 2); // receiver acked seq 0..1
  const replay = s.replayFrom({ tokens: 2 });
  assert.deepEqual(replay.map(f => f.seq), [2, 3]);
  assert.deepEqual(replay.map(f => f.payload), ['tok2', 'tok3']);
  assert.equal(replay[0], frames[2]); // the SAME object — replay, not regeneration
});

test('ack prunes the resend buffer per stream', () => {
  const s = new FrameSender();
  for (let i = 0; i < 5; i++) s.send('tokens', 'stream', i);
  assert.equal(s.buffered('tokens'), 5);
  s.onAck('tokens', 4);
  assert.equal(s.buffered('tokens'), 1);
  assert.deepEqual(s.replayFrom({ tokens: 0 }).map(f => f.seq), [4]);
});

test('end-to-end: drop mid-stream, resume from cursors, zero loss/dup', () => {
  const s = new FrameSender();
  const r = new FrameReceiver<OutgoingFrame>();
  const delivered: unknown[] = [];
  const deliver = (res: ReturnType<FrameReceiver<OutgoingFrame>['accept']>) => {
    if (res.kind === 'deliver') delivered.push(...res.frames.map(f => f.payload));
  };

  const all = Array.from({ length: 10 }, (_, i) => s.send('tokens', 'stream', i));
  // First 4 arrive, then the link drops (5..9 lost in flight).
  for (const f of all.slice(0, 4)) deliver(r.accept(f));
  // Reconnect: client sends resume with its cursors; sender replays.
  for (const f of s.replayFrom(r.cursors())) deliver(r.accept(f));
  assert.deepEqual(delivered, [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]);
});
