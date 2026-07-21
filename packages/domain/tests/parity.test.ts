import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  PERSONAS,
  personaById,
  composeRetrievalContext,
  deriveConversationTitle,
  ChatSession,
} from '../src/index.ts';

test('personaById resolves known ids and falls back to default', () => {
  assert.equal(personaById('coder').name, 'Engineer');
  assert.equal(personaById('nope').id, 'default');
  assert.equal(personaById(null).id, 'default');
  assert.equal(PERSONAS.length, 5);
});

test('composeRetrievalContext joins non-blank blocks in canonical order', () => {
  const out = composeRetrievalContext({
    memory: '## Memory update\nsaved',
    skill: '',
    rag: '## Documents\npassage',
    hindsight: '  ',
  });
  assert.equal(out, '## Memory update\nsaved\n\n## Documents\npassage');
  assert.equal(composeRetrievalContext({}), '');
});

test('deriveConversationTitle uses first user text capped at 40 chars', () => {
  const s = new ChatSession();
  s.startTurn('A'.repeat(60));
  s.feed({ kind: 'final', text: 'ok' });
  assert.equal(deriveConversationTitle(s.getMessages()), 'A'.repeat(40));
  assert.equal(deriveConversationTitle([]), 'New Conversation');
});

test('loadMessages seeds and rollbackLastTurn returns the last user text', () => {
  const s = new ChatSession();
  s.loadMessages([
    { id: '1', role: 'user', text: 'first q', reasoning: null, isStreaming: false, createdAt: 1 },
    { id: '2', role: 'assistant', text: 'first a', reasoning: null, isStreaming: false, createdAt: 2 },
    { id: '3', role: 'user', text: 'second q', reasoning: null, isStreaming: false, createdAt: 3 },
    { id: '4', role: 'assistant', text: 'second a', reasoning: null, isStreaming: false, createdAt: 4 },
  ]);
  const text = s.rollbackLastTurn();
  assert.equal(text, 'second q');
  assert.deepEqual(s.getMessages().map(m => m.id), ['1', '2']);
  // rolling back again returns the previous user turn
  assert.equal(s.rollbackLastTurn(), 'first q');
  assert.deepEqual(s.getMessages(), []);
  assert.equal(s.rollbackLastTurn(), null); // nothing left
});

test('rollbackLastTurn is refused mid-turn', () => {
  const s = new ChatSession();
  s.startTurn('q');
  assert.equal(s.rollbackLastTurn(), null);
});
