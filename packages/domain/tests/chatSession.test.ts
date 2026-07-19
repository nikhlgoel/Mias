import { test } from 'node:test';
import assert from 'node:assert/strict';
import { ChatSession, type ChatMessage } from '../src/index.ts';

function lastAssistant(msgs: ChatMessage[]): ChatMessage {
  const m = [...msgs].reverse().find(x => x.role === 'assistant');
  assert.ok(m, 'expected an assistant message');
  return m!;
}

test('token deltas assemble a streaming bubble; final replaces with sanitized text', () => {
  const s = new ChatSession();
  s.startTurn('hi');
  const chunks = ['{"thought":"greet', '","should_say":"Hel', 'lo there!"', ',"is_final":true}'];
  for (const c of chunks) s.feed({ kind: 'token', text: c });

  let a = lastAssistant(s.getMessages());
  assert.equal(a.isStreaming, true);
  assert.equal(a.text, 'Hello there!'); // live should_say, JSON never leaks
  assert.equal(a.reasoning, 'greet');

  s.feed({ kind: 'final', text: '{"thought":"greet","should_say":"Hello there!","is_final":true}' });
  a = lastAssistant(s.getMessages());
  assert.equal(a.isStreaming, false);
  assert.equal(a.text, 'Hello there!');
  assert.equal(s.isProcessing, false);
});

test('plain-prose stream renders progressively and finalizes via done', () => {
  const s = new ChatSession();
  s.startTurn('q');
  for (const c of ['Hel', 'lo ', 'world']) s.feed({ kind: 'token', text: c });
  assert.equal(lastAssistant(s.getMessages()).text, 'Hello world');
  s.feed({ kind: 'done' });
  const a = lastAssistant(s.getMessages());
  assert.equal(a.isStreaming, false);
  assert.equal(a.text, 'Hello world');
});

test('single-chunk source (LAN offload) works: one final, no tokens', () => {
  const s = new ChatSession();
  s.startTurn('q');
  s.feed({ kind: 'final', text: 'Answer from the desktop model.' });
  const a = lastAssistant(s.getMessages());
  assert.equal(a.text, 'Answer from the desktop model.');
  assert.equal(a.isStreaming, false);
});

test('error keeps readable streamed text as fallback', () => {
  const s = new ChatSession();
  s.startTurn('q');
  s.feed({ kind: 'token', text: 'Partial ans' });
  s.feed({ kind: 'error', text: 'engine crashed' });
  const a = lastAssistant(s.getMessages());
  assert.equal(a.text, 'Partial ans');
  assert.equal(a.isStreaming, false);
});

test('error with nothing streamed produces the apology bubble', () => {
  const s = new ChatSession();
  s.startTurn('q');
  s.feed({ kind: 'error', text: 'load failed' });
  const last = s.getMessages().at(-1)!;
  assert.equal(last.role, 'error');
  assert.match(last.text, /went wrong/);
});

test('observation resets the buffer so the next loop turn parses fresh', () => {
  const s = new ChatSession();
  s.startTurn('q');
  s.feed({ kind: 'token', text: '{"thought":"need tool","action":"search"' });
  s.feed({ kind: 'observation', text: 'result: 42' });
  s.feed({ kind: 'token', text: '{"should_say":"It is 42."}' });
  assert.equal(lastAssistant(s.getMessages()).text, 'It is 42.');
});

test('ReAct steps hidden by default, shown when enabled', () => {
  const hidden = new ChatSession();
  hidden.startTurn('q');
  hidden.feed({ kind: 'thought', text: 'thinking...' });
  assert.equal(hidden.getMessages().some(m => m.role === 'thought'), false);

  const shown = new ChatSession({ showReActSteps: true });
  shown.startTurn('q');
  shown.feed({ kind: 'thought', text: 'thinking...' });
  shown.feed({ kind: 'action', tool: 'search', text: '{q}' });
  assert.equal(shown.getMessages().some(m => m.role === 'thought'), true);
  assert.equal(shown.getMessages().some(m => m.role === 'action'), true);
});

test('runaway repetition trims, fires onRunaway, ends the turn', () => {
  const s = new ChatSession();
  let fired = false;
  s.onRunaway = () => { fired = true; };
  s.startTurn('q');
  s.feed({ kind: 'token', text: 'Looping now ' });
  s.feed({ kind: 'token', text: 'ha'.repeat(60) });
  assert.equal(fired, true);
  assert.equal(s.isProcessing, false);
  const a = lastAssistant(s.getMessages());
  assert.equal(a.isStreaming, false);
  assert.equal(a.text.includes('hahahahaha'), false);
});

test('stop freezes the partial text', () => {
  const s = new ChatSession();
  s.startTurn('q');
  s.feed({ kind: 'token', text: 'Partial thought about' });
  s.stop();
  const a = lastAssistant(s.getMessages());
  assert.equal(a.isStreaming, false);
  assert.equal(a.text, 'Partial thought about');
  // late events after stop are ignored
  s.feed({ kind: 'token', text: ' MORE' });
  assert.equal(lastAssistant(s.getMessages()).text, 'Partial thought about');
});

test('subscribe notifies on every change and supports unsubscribe', () => {
  const s = new ChatSession();
  let calls = 0;
  const un = s.subscribe(() => { calls++; });
  s.startTurn('q'); // push user msg
  const after = calls;
  un();
  s.feed({ kind: 'final', text: 'done' });
  assert.equal(calls, after); // no notifications after unsubscribe
});
