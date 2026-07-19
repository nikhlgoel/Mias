import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  parseReActStream,
  sanitizeResponse,
  stripJsonResidue,
  stripMetaReasoning,
  isRunawayRepetition,
  trimRepetition,
  unescapeJsonString,
} from '../src/index.ts';

// ── parseReActStream ────────────────────────────────────────────────────────

test('plain prose streams straight through as visible', () => {
  const s = parseReActStream('Hello there, how can I help');
  assert.equal(s.visible, 'Hello there, how can I help');
  assert.equal(s.thinking, '');
});

test('forming JSON shows nothing until should_say lands', () => {
  const s = parseReActStream('{"thought":"user greets me","action":"respond_user"');
  assert.equal(s.visible, '');
  assert.equal(s.thinking, 'user greets me');
});

test('partial should_say value streams live, escapes decoded', () => {
  const buf = '{"thought":"greet","should_say":"Hi!\\nWelcome ba';
  const s = parseReActStream(buf);
  assert.equal(s.visible, 'Hi!\nWelcome ba');
  assert.equal(s.thinking, 'greet');
});

test('a plain reply quoting the word thought is not misread as JSON', () => {
  const s = parseReActStream('I had a thought about that book you mentioned.');
  assert.equal(s.visible, 'I had a thought about that book you mentioned.');
});

test('unescape handles \\uXXXX and tolerates a trailing partial escape', () => {
  assert.equal(unescapeJsonString('caf\\u00e9'), 'café');
  assert.equal(unescapeJsonString('line\\n'), 'line\n');
  assert.equal(unescapeJsonString('partial\\u00'), 'partialu00'); // no crash
});

// ── sanitizeResponse ────────────────────────────────────────────────────────

test('valid ReAct JSON → should_say as chatText, thought as reasoning', () => {
  const r = sanitizeResponse(
    '{"thought":"they want a joke","should_say":"Why did the dev cross the road?","is_final":true}',
  );
  assert.equal(r.chatText, 'Why did the dev cross the road?');
  assert.equal(r.reasoningText, 'they want a joke');
});

test('JSON inside markdown fences is parsed', () => {
  const r = sanitizeResponse('```json\n{"should_say":"Fenced reply"}\n```');
  assert.equal(r.chatText, 'Fenced reply');
});

test('JSON with only a thought falls back to the thought as text', () => {
  const r = sanitizeResponse('{"thought":"hmm interesting","is_final":false}');
  assert.equal(r.chatText, 'hmm interesting');
});

test('malformed JSON with recoverable should_say is extracted', () => {
  const r = sanitizeResponse('garbage {"should_say":"Recovered!","is_final":');
  assert.equal(r.chatText, 'Recovered!');
});

test('plain prose passes through unharmed', () => {
  const r = sanitizeResponse('Just a normal answer.');
  assert.equal(r.chatText, 'Just a normal answer.');
  assert.equal(r.reasoningText, null);
});

test('speaker labels and code fences are stripped from prose', () => {
  const r = sanitizeResponse('Assistant: Here is the answer.');
  assert.equal(r.chatText, 'Here is the answer.');
});

test('stripMetaReasoning drops leading scaffolding but keeps substance', () => {
  const out = stripMetaReasoning('I will use web_search to find that. The capital is Paris.');
  assert.equal(out, 'The capital is Paris.');
  // never empties the message
  assert.equal(stripMetaReasoning('I will use web_search to find that.'),
    'I will use web_search to find that.');
});

test('stripJsonResidue scrubs structural keys and dangling braces', () => {
  const out = stripJsonResidue('{"is_final": false, "action": "respond_user" Hello world');
  assert.equal(out, 'Hello world');
});

// ── repetition breaker ──────────────────────────────────────────────────────

test('detects a single-char run', () => {
  assert.equal(isRunawayRepetition('a'.repeat(30) + 'x'.repeat(30)), true);
});

test('detects a repeated short cluster', () => {
  assert.equal(isRunawayRepetition('The answer is ' + 'haha'.repeat(20)), true);
});

test('normal prose is not flagged', () => {
  assert.equal(
    isRunawayRepetition(
      'This is a perfectly ordinary sentence with plenty of variety in its characters and words.',
    ),
    false,
  );
});

test('trimRepetition drops the trailing run, keeps content', () => {
  const out = trimRepetition('Here is the answer' + '!'.repeat(20));
  assert.equal(out, 'Here is the answer');
  // all-garbage input yields the apology
  assert.match(trimRepetition('!!!!!!!!!!!!!!!!!!!!'), /rephrase/);
});
