/**
 * Mias shared domain logic — platform-free TypeScript.
 *
 * Faithful ports of the proven Kotlin implementations (behavior-compatible):
 *  - StreamingReActParser  (core/inference/react/StreamingReActParser.kt)
 *  - ResponseSanitizer     (core/inference/react/ResponseSanitizer.kt)
 *  - runaway-repetition breaker (app ChatViewModel)
 *  - ChatSession           (the streaming-assembly logic of a chat turn)
 *
 * Consumed by the mobile app and, later, the PC extension. Zero runtime deps.
 */

// ── Streaming ReAct parser ──────────────────────────────────────────────────
//
// The grammar forces the model to emit
// `{"thought":"…","action":"…","action_input":{…},"is_final":…,"should_say":"…"}`.
// While streaming we can't strict-parse, so two lenient regexes surface the
// live `thought` (thinking box) and `should_say` (bubble body) values.

export interface StreamState {
  thinking: string;
  visible: string;
}

const THOUGHT_RE = /"thought"\s*:\s*"((?:[^"\\]|\\.)*)/;
const SHOULD_SAY_RE = /"should_say"\s*:\s*"((?:[^"\\]|\\.)*)/;

// Keys unique to our ReAct schema — deliberately specific so a plain reply that
// merely quotes a common word isn't misread as JSON and hidden.
const JSON_MARKERS = ['"should_say"', '"is_final"', '"action_input"'];

/** Decode JSON string escapes; tolerant of a trailing partial escape. */
export function unescapeJsonString(s: string): string {
  let out = '';
  let i = 0;
  while (i < s.length) {
    const c = s[i]!;
    if (c === '\\' && i + 1 < s.length) {
      const n = s[i + 1]!;
      switch (n) {
        case 'n': out += '\n'; break;
        case 't': out += '\t'; break;
        case 'r': out += '\r'; break;
        case 'b': out += '\b'; break;
        case 'f': out += '\f'; break;
        case '"': out += '"'; break;
        case '\\': out += '\\'; break;
        case '/': out += '/'; break;
        case 'u': {
          const hex = i + 6 <= s.length ? s.slice(i + 2, i + 6) : null;
          const code = hex !== null && /^[0-9a-fA-F]{4}$/.test(hex) ? parseInt(hex, 16) : null;
          if (code !== null) {
            out += String.fromCharCode(code);
            i += 6;
            continue;
          }
          out += n;
          break;
        }
        default: out += n;
      }
      i += 2;
    } else {
      out += c;
      i++;
    }
  }
  return out;
}

/** Live split of the growing raw buffer into thinking + visible text. */
export function parseReActStream(buffer: string): StreamState {
  const thinking = (() => {
    const m = THOUGHT_RE.exec(buffer);
    return m ? unescapeJsonString(m[1]!) : '';
  })();
  const sayMatch = SHOULD_SAY_RE.exec(buffer);
  const say = sayMatch ? unescapeJsonString(sayMatch[1]!) : null;

  const looksJson =
    buffer.trimStart().startsWith('{') || JSON_MARKERS.some(mk => buffer.includes(mk));

  const visible =
    say !== null ? say // JSON form: surface ONLY should_say, never structure
    : looksJson ? '' // JSON forming — nothing until should_say lands
    : buffer; // genuine plain-language reply — stream straight through
  return { thinking: thinking.trim(), visible };
}

// ── Response sanitizer ──────────────────────────────────────────────────────
//
// Raw model output (JSON object / fenced JSON / prose) → { chatText, reasoningText }.
// Only chatText is stored and replayed; raw JSON in history poisons the context.

export interface SanitizedResponse {
  chatText: string;
  reasoningText: string | null;
}

const CONVERSATIONAL_KEYS = [
  'should_say', 'response', 'answer', 'reply', 'say', 'final_answer',
  'message', 'text', 'content',
];
const REASONING_KEYS = ['thought', 'reasoning', 'thinking', 'plan'];

const META_REASONING_LEAD = new RegExp(
  "^(?:\\s*(?:i will use|i'll use|i am going to use|i need to (?:gather|search|use|" +
    'fetch|find|look|check)|let me (?:search|use|gather|look|check)|the user (?:is ' +
    'asking|wants|needs|asked|would like)|i have access to|i am a large language ' +
    'model|i should (?:use|search|gather))\\b[^.!?\\n]*[.!?\\n]+)+',
  'i',
);

const SHOULD_SAY_VALUE_RE = /"should_say"\s*:\s*"((?:[^"\\]|\\.)*)"/;
const MARKDOWN_JSON_FENCE_RE = /```[a-zA-Z]*|```/g;
const STRUCTURAL_KEY_RE =
  /"(is_final|action|action_input|thought|response|should_say)"\s*:\s*("[^"]*"|true|false|null|\{[^}]*\}|\[[^\]]*\])?/g;
const DANGLING_PUNCT_RE = /[{}\[\]]|^[\s,:]+|[\s,:]+$/g;
const CODE_FENCE_RE = /```[a-zA-Z]*\n?|```/g;
const CONTROL_HEADER_RE = /<\|[a-zA-Z_]+\|>|## Instructions?\b/g;
const SPEAKER_LABEL_RE = /^(Assistant|AI|Mias)\s*:\s*/i;

/** First balanced top-level `{ … }`, string-aware. */
function extractJsonObject(text: string): string | null {
  const start = text.indexOf('{');
  if (start === -1) return null;
  let depth = 0;
  let inString = false;
  let escaped = false;
  for (let i = start; i < text.length; i++) {
    const c = text[i]!;
    if (escaped) { escaped = false; continue; }
    if (c === '\\' && inString) { escaped = true; continue; }
    if (c === '"') { inString = !inString; continue; }
    if (!inString && c === '{') depth++;
    else if (!inString && c === '}') {
      depth--;
      if (depth === 0) return text.slice(start, i + 1);
    }
  }
  return null;
}

function firstStringField(obj: Record<string, unknown>, keys: string[]): string | null {
  for (const key of keys) {
    const v = obj[key];
    if (typeof v === 'string' && v.trim().length > 0) return v;
  }
  return null;
}

/** Strip leading agent-scaffolding sentences; keep original if it would empty the text. */
export function stripMetaReasoning(input: string): string {
  if (input.trim().length === 0) return input;
  const stripped = input.trimStart().replace(META_REASONING_LEAD, '').trim();
  return stripped.length > 0 ? stripped : input.trim();
}

/** Last-resort cleaner for malformed-JSON residue around conversational text. */
export function stripJsonResidue(input: string): string {
  if (input.trim().length === 0) return input;
  const say = SHOULD_SAY_VALUE_RE.exec(input);
  if (say) {
    return say[1]!
      .replace(/\\n/g, '\n').replace(/\\t/g, '\t').replace(/\\"/g, '"').replace(/\\\\/g, '\\')
      .trim();
  }
  let out = input
    .replace(MARKDOWN_JSON_FENCE_RE, '')
    .replace(STRUCTURAL_KEY_RE, '')
    .replace(DANGLING_PUNCT_RE, ' ')
    .replace(/\s+/g, ' ')
    .trim();
  out = out.replace(/^[{},"\s]+|[{},"\s]+$/g, '');
  return out.trim();
}

function cleanupPlain(text: string): string {
  let out = text.replace(CODE_FENCE_RE, '').replace(CONTROL_HEADER_RE, '').trim();
  out = out.replace(SPEAKER_LABEL_RE, '').trim();
  if (out.startsWith('{')) {
    const idx = out.indexOf('}');
    const afterBrace = idx >= 0 ? out.slice(idx + 1).trim() : '';
    out = afterBrace.length > 0
      ? afterBrace
      : out.replace(/^\{/, '').replace(/\}$/, '').trim();
  }
  return out;
}

/** Raw model output → clean user-visible text + display-only reasoning. */
export function sanitizeResponse(raw: string): SanitizedResponse {
  const trimmed = raw.trim();
  if (trimmed.length === 0) return { chatText: '', reasoningText: null };

  const jsonStr = extractJsonObject(trimmed);
  if (jsonStr !== null) {
    try {
      const obj = JSON.parse(jsonStr) as Record<string, unknown>;
      const say = firstStringField(obj, CONVERSATIONAL_KEYS);
      const thought = firstStringField(obj, REASONING_KEYS);
      if (say !== null) {
        return {
          chatText: say.trim(),
          reasoningText: thought !== null && thought.trim().length > 0 ? thought.trim() : jsonStr,
        };
      }
      if (thought !== null) {
        return { chatText: thought.trim(), reasoningText: jsonStr };
      }
    } catch {
      // fall through to residue cleanup
    }
  }
  const cleaned = stripMetaReasoning(stripJsonResidue(cleanupPlain(trimmed)));
  return { chatText: cleaned, reasoningText: null };
}

// ── Runaway-repetition circuit breaker ──────────────────────────────────────

const REP_TAIL = 80;

/** Detects the three classic runaway-loop shapes in the streamed tail. */
export function isRunawayRepetition(text: string): boolean {
  const tail = text.slice(-REP_TAIL);
  if (tail.length < 48) return false;

  // (a) a single non-space char repeated 12+ times in a row
  let run = 1;
  let maxRun = 1;
  for (let i = 1; i < tail.length; i++) {
    if (tail[i] === tail[i - 1] && !/\s/.test(tail[i]!)) {
      run++;
      if (run > maxRun) maxRun = run;
    } else {
      run = 1;
    }
  }
  if (maxRun >= 12) return true;

  // (b) gibberish clusters — long tail, almost no distinct characters
  const distinct = new Set(tail.replace(/\s/g, '')).size;
  if (distinct >= 1 && distinct <= 5) return true;

  // (c) a 2–6 char cluster repeated 6+ times at the end
  for (let p = 2; p <= 6; p++) {
    if (tail.length < p * 6) continue;
    const unit = tail.slice(tail.length - p);
    let count = 0;
    let idx = tail.length;
    while (idx - p >= 0 && tail.slice(idx - p, idx) === unit) {
      count++;
      idx -= p;
    }
    if (count >= 6) return true;
  }
  return false;
}

/**
 * Drop a trailing repeated run so the saved message isn't garbage. Covers the
 * same shapes the detector flags: single-char runs AND repeated 2–6 char
 * clusters (the Kotlin original only trimmed single chars — a detected "haha…"
 * loop survived the trim; fixed here).
 */
export function trimRepetition(text: string): string {
  let out = text.trimEnd();
  let bestEnd = out.length;
  for (let p = 1; p <= 6 && p <= out.length; p++) {
    const unit = out.slice(out.length - p);
    let end = out.length;
    while (end - p >= 0 && out.slice(end - p, end) === unit) end -= p;
    // Trim only a genuine run (≥ 6 chars would be removed).
    if (out.length - end >= 6) bestEnd = Math.min(bestEnd, end);
  }
  if (bestEnd < out.length) out = out.slice(0, bestEnd);
  out = out.trimEnd();
  return out.length > 0 ? out : 'I got stuck repeating myself there — could you rephrase that?';
}

// ── Personas (port of core/common model/Persona.kt) ─────────────────────────
//
// Device-local named system prompts; switching one only changes the prompt on
// the next turn, never the model.

export interface Persona {
  id: string;
  name: string;
  tagline: string;
  systemPrompt: string;
}

export const PERSONAS: Persona[] = [
  {
    id: 'default',
    name: 'Mias',
    tagline: 'Warm, balanced everyday assistant',
    systemPrompt:
      "You are Mias, a personal assistant that runs entirely on the user's device.\n" +
      'Speak with a calm, supportive, and professional tone — like a trusted\n' +
      'colleague who listens carefully and replies with care.\n' +
      'Think before answering. Keep replies concise by default; expand only when\n' +
      "the user asks for depth. When you don't know or can't do something, say so\n" +
      'plainly.',
  },
  {
    id: 'coder',
    name: 'Engineer',
    tagline: 'Precise, code-first answers',
    systemPrompt:
      'You are Mias in engineering mode. Give precise, technically correct answers.\n' +
      'Prefer working code with minimal prose; use fenced code blocks and name the\n' +
      'language. State assumptions briefly, call out edge cases and pitfalls, and\n' +
      'never invent APIs — if unsure, say so.',
  },
  {
    id: 'tutor',
    name: 'Tutor',
    tagline: 'Patient, step-by-step explanations',
    systemPrompt:
      'You are Mias in tutor mode. Explain clearly and patiently, building from\n' +
      'first principles. Break things into small steps, use a simple example, and\n' +
      'check understanding with a short question at the end. Avoid jargon unless you\n' +
      'define it.',
  },
  {
    id: 'brainstorm',
    name: 'Brainstorm',
    tagline: 'Lots of fast, varied ideas',
    systemPrompt:
      'You are Mias in brainstorm mode. Generate many varied ideas quickly. Favor a\n' +
      "short, numbered list over long paragraphs, span different angles, and don't\n" +
      'self-censor early — quantity first, then a one-line note on the most promising.',
  },
  {
    id: 'concise',
    name: 'Concise',
    tagline: 'Short, direct, no filler',
    systemPrompt:
      'You are Mias in concise mode. Answer in as few words as possible while staying\n' +
      'correct. No preamble, no filler, no restating the question. Use a short list\n' +
      'only when it genuinely helps.',
  },
];

/** Resolve by id, falling back to the default persona for unknown/blank ids. */
export function personaById(id: string | null | undefined): Persona {
  return PERSONAS.find(p => p.id === id) ?? PERSONAS[0]!;
}

// ── Turn context composition (port of the ChatViewModel join) ───────────────

export interface TurnContextParts {
  memory?: string;
  skill?: string;
  rag?: string;
  hindsight?: string;
}

/** Join non-blank context blocks in the canonical order (memory, skill, rag, hindsight). */
export function composeRetrievalContext(parts: TurnContextParts): string {
  return [parts.memory, parts.skill, parts.rag, parts.hindsight]
    .filter((s): s is string => typeof s === 'string' && s.trim().length > 0)
    .join('\n\n');
}

/** Title fallback exactly like the Kotlin app: first user text, 40 chars. */
export function deriveConversationTitle(messages: ChatMessage[]): string {
  const firstUser = messages.find(m => m.role === 'user');
  return firstUser ? firstUser.text.slice(0, 40) : 'New Conversation';
}

// ── Chat session (streaming assembly) ───────────────────────────────────────
//
// The platform-free heart of a chat turn: consumes inference step events
// (native module on-device, or a network transport) and assembles the message
// list with live thinking/visible split, sanitized finalization, repetition
// breaking, stop, and error fallback. UI layers subscribe and render.

export type ChatRole = 'user' | 'assistant' | 'thought' | 'action' | 'error';

export interface ChatMessage {
  id: string;
  role: ChatRole;
  text: string;
  reasoning: string | null;
  isStreaming: boolean;
  createdAt: number;
}

/** Inference step events — mirrors the native MiasInference.step payloads. */
export type InferenceStep =
  | { kind: 'token'; text: string } // incremental delta
  | { kind: 'thought'; text: string }
  | { kind: 'action'; tool: string; text: string }
  | { kind: 'observation'; text: string }
  | { kind: 'final'; text: string }
  | { kind: 'modelSwitch'; from: string; to: string }
  | { kind: 'error'; text: string }
  | { kind: 'done' };

export interface ChatSessionOptions {
  showReActSteps?: boolean;
  now?: () => number;
  makeId?: () => string;
}

let idCounter = 0;
const defaultMakeId = () => `m${++idCounter}-${Date.now().toString(36)}`;

export class ChatSession {
  private messages: ChatMessage[] = [];
  private listeners = new Set<(messages: ChatMessage[]) => void>();
  private streamingId: string | null = null;
  private rawBuffer = '';
  private thinking = '';
  private visible = '';
  private turnActive = false;
  private showReActSteps: boolean;
  private now: () => number;
  private makeId: () => string;
  /** Set when the repetition breaker fires; the caller should stop generation. */
  onRunaway: (() => void) | null = null;

  constructor(opts: ChatSessionOptions = {}) {
    this.showReActSteps = opts.showReActSteps ?? false;
    this.now = opts.now ?? Date.now;
    this.makeId = opts.makeId ?? defaultMakeId;
  }

  subscribe(cb: (messages: ChatMessage[]) => void): () => void {
    this.listeners.add(cb);
    cb(this.messages);
    return () => this.listeners.delete(cb);
  }

  getMessages(): ChatMessage[] {
    return this.messages;
  }

  get isProcessing(): boolean {
    return this.turnActive;
  }

  setShowReActSteps(show: boolean): void {
    this.showReActSteps = show;
  }

  /** Seed the session with persisted messages (loading a conversation). */
  loadMessages(messages: ChatMessage[]): void {
    this.messages = [...messages];
    this.turnActive = false;
    this.streamingId = null;
    this.notify();
  }

  /**
   * Regenerate support: drop the last user message and everything after it,
   * returning that user text for a fresh send. Null while processing or when
   * no user turn exists. (Mirrors the Kotlin regenerate(): keep everything
   * before the last user turn; replay that prompt through the full pipeline.)
   */
  rollbackLastTurn(): string | null {
    if (this.turnActive) return null;
    const idx = this.messages.map(m => m.role).lastIndexOf('user');
    if (idx < 0) return null;
    const userText = this.messages[idx]!.text;
    this.messages = this.messages.slice(0, idx);
    this.notify();
    return userText;
  }

  /** Begin a turn: appends the user message and arms the streaming assembly. */
  startTurn(userText: string): void {
    this.push({
      id: this.makeId(), role: 'user', text: userText,
      reasoning: null, isStreaming: false, createdAt: this.now(),
    });
    this.rawBuffer = '';
    this.thinking = '';
    this.visible = '';
    this.streamingId = null;
    this.turnActive = true;
  }

  /** Feed one inference step event (from any source: native, LAN, Bridge). */
  feed(step: InferenceStep): void {
    if (!this.turnActive) return;
    switch (step.kind) {
      case 'token': {
        this.rawBuffer += step.text;
        const parsed = parseReActStream(this.rawBuffer);
        this.thinking = parsed.thinking;
        this.visible = parsed.visible;
        if (isRunawayRepetition(this.rawBuffer)) {
          this.visible = trimRepetition(this.visible);
          this.upsertStreaming();
          this.onRunaway?.();
          this.stop();
          return;
        }
        this.upsertStreaming();
        break;
      }
      case 'thought':
        if (this.showReActSteps) {
          this.push({
            id: this.makeId(), role: 'thought', text: step.text,
            reasoning: null, isStreaming: false, createdAt: this.now(),
          });
        }
        break;
      case 'action':
        if (this.showReActSteps) {
          this.push({
            id: this.makeId(), role: 'action', text: `${step.tool}(${step.text})`,
            reasoning: null, isStreaming: false, createdAt: this.now(),
          });
        }
        break;
      case 'observation':
        // A tool ran; the agent loops. Reset so the next turn parses fresh.
        this.rawBuffer = '';
        this.visible = '';
        break;
      case 'final': {
        const sanitized = sanitizeResponse(step.text);
        const reasoning =
          this.thinking.trim().length > 0 ? this.thinking.trim()
          : sanitized.reasoningText;
        this.finalize(sanitized.chatText, reasoning);
        break;
      }
      case 'modelSwitch':
        if (this.showReActSteps) {
          this.push({
            id: this.makeId(), role: 'action', text: `Switched: ${step.from} → ${step.to}`,
            reasoning: null, isStreaming: false, createdAt: this.now(),
          });
        }
        break;
      case 'error': {
        // Keep whatever readable text was streamed; otherwise apologise.
        const fallback =
          this.visible.trim().length > 0 ? this.visible
          : this.rawBuffer.trim().length > 0 ? this.rawBuffer.trim()
          : 'Something went wrong while I was answering. Please try again.';
        if (this.streamingId !== null) {
          this.update(this.streamingId, m => ({ ...m, text: fallback, isStreaming: false }));
        } else {
          this.push({
            id: this.makeId(), role: 'error', text: fallback,
            reasoning: null, isStreaming: false, createdAt: this.now(),
          });
        }
        this.endTurn();
        break;
      }
      case 'done':
        // Stream ended without an explicit final: finalize from the buffer.
        if (this.turnActive && this.streamingId !== null) {
          const sanitized = sanitizeResponse(this.rawBuffer);
          const reasoning =
            this.thinking.trim().length > 0 ? this.thinking.trim() : sanitized.reasoningText;
          this.finalize(
            sanitized.chatText.length > 0 ? sanitized.chatText : this.visible,
            reasoning,
          );
        } else {
          this.endTurn();
        }
        break;
    }
  }

  /** Stop button: freezes the partial text and ends the turn. */
  stop(): void {
    if (this.streamingId !== null) {
      this.update(this.streamingId, m => ({ ...m, isStreaming: false }));
    }
    this.endTurn();
  }

  private finalize(text: string, reasoning: string | null): void {
    if (this.streamingId !== null) {
      this.update(this.streamingId, m => ({
        ...m, text, reasoning, isStreaming: false,
      }));
    } else {
      this.push({
        id: this.makeId(), role: 'assistant', text,
        reasoning, isStreaming: false, createdAt: this.now(),
      });
    }
    this.endTurn();
  }

  private endTurn(): void {
    this.turnActive = false;
    this.streamingId = null;
    this.notify();
  }

  private upsertStreaming(): void {
    if (this.streamingId === null) {
      this.streamingId = this.makeId();
      this.push({
        id: this.streamingId, role: 'assistant', text: this.visible,
        reasoning: this.thinking.trim().length > 0 ? this.thinking.trim() : null,
        isStreaming: true, createdAt: this.now(),
      });
    } else {
      this.update(this.streamingId, m => ({
        ...m,
        text: this.visible,
        reasoning: this.thinking.trim().length > 0 ? this.thinking.trim() : null,
      }));
    }
  }

  private push(m: ChatMessage): void {
    this.messages = [...this.messages, m];
    this.notify();
  }

  private update(id: string, fn: (m: ChatMessage) => ChatMessage): void {
    this.messages = this.messages.map(m => (m.id === id ? fn(m) : m));
    this.notify();
  }

  private notify(): void {
    for (const cb of this.listeners) cb(this.messages);
  }
}
