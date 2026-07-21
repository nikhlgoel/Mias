/**
 * Chat — S2 vertical slice, upgraded in S3 to parity features:
 * persisted conversations + settings (same stores the Kotlin app used),
 * personas, Hindsight/RAG retrieval context, regenerate, stop.
 *
 * Thin view: `ChatSession` (packages/domain) assembles state; native modules
 * (inference/data/prefs) do platform work; this file renders and routes.
 */
import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  FlatList,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
  useColorScheme,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import {
  ChatSession,
  PERSONAS,
  personaById,
  composeRetrievalContext,
  deriveConversationTitle,
  type ChatMessage,
} from '@mias/domain';
import { McpClient } from '@mias/bridge-protocol';
import { localInference } from '../native/inference';
import { dataStore, toChatMessages } from '../native/data';
import { prefsStore } from '../native/prefs';
import { speech } from '../native/speech';
import { darkTheme, lightTheme, type Theme } from '../theme';

type Backend = 'local' | 'lan';

let requestCounter = 0;
const nextRequestId = () => `req-${++requestCounter}-${Date.now().toString(36)}`;
const newConversationId = () => `conv-${Date.now().toString(36)}-${Math.floor(Math.random() * 1e6).toString(36)}`;

export function ChatScreen(): React.JSX.Element {
  const scheme = useColorScheme();
  const t = scheme === 'dark' ? darkTheme : lightTheme;
  const insets = useSafeAreaInsets();

  const session = useMemo(() => new ChatSession(), []);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [busy, setBusy] = useState(false);
  const [backend, setBackend] = useState<Backend>('local');
  const [personaId, setPersonaIdState] = useState('default');
  const [showPersonas, setShowPersonas] = useState(false);
  const [showLanConfig, setShowLanConfig] = useState(false);
  const [lanHost, setLanHost] = useState('');
  const [lanPort, setLanPort] = useState('8401');
  const [lanToken, setLanToken] = useState('');
  const [listening, setListening] = useState(false);
  const stopRef = useRef<(() => void) | null>(null);
  const stopVoiceRef = useRef<(() => void) | null>(null);
  const listRef = useRef<FlatList<ChatMessage>>(null);
  const convIdRef = useRef(newConversationId());
  const convCreatedAtRef = useRef(Date.now());
  const convTitleRef = useRef<string | null>(null);

  useEffect(() => session.subscribe(setMessages), [session]);

  // Boot: load persisted prefs + the most recent conversation.
  useEffect(() => {
    let cancelled = false;
    (async () => {
      const p = await prefsStore.get();
      if (cancelled) return;
      setPersonaIdState(p.personaId);
      setLanHost(p.desktopHost);
      setLanPort(String(p.desktopPort));
      setLanToken(p.desktopToken);

      const list = await dataStore.listConversations();
      const latest = list[0];
      if (latest && !cancelled) {
        const conv = await dataStore.getConversation(latest.id);
        if (conv && !cancelled) {
          convIdRef.current = conv.id;
          convCreatedAtRef.current = conv.createdAt;
          convTitleRef.current = conv.title;
          session.loadMessages(toChatMessages(conv));
        }
      }
      if (localInference.isAvailable) localInference.warmUp().catch(() => {});
    })();
    return () => {
      cancelled = true;
    };
  }, [session]);

  useEffect(() => {
    const id = setTimeout(() => listRef.current?.scrollToEnd({ animated: true }), 50);
    return () => clearTimeout(id);
  }, [messages.length]);

  const persistTurn = useCallback(async () => {
    const msgs = session.getMessages();
    const title = convTitleRef.current ?? deriveConversationTitle(msgs);
    convTitleRef.current = title;
    await dataStore.saveConversation(convIdRef.current, title, convCreatedAtRef.current, msgs);
  }, [session]);

  const endTurn = useCallback(() => {
    stopRef.current = null;
    setBusy(false);
  }, []);

  const runTurn = useCallback(
    async (text: string) => {
      setBusy(true);
      session.startTurn(text);

      if (backend === 'local') {
        const ctx = await dataStore.getTurnContext(text, convIdRef.current);
        const retrieval = composeRetrievalContext({ rag: ctx.rag, hindsight: ctx.hindsight });
        const requestId = nextRequestId();
        const persona = personaById(personaId);
        const stop = localInference.send(requestId, text, persona.systemPrompt, retrieval, step => {
          session.feed(step);
          if (step.kind === 'final' || step.kind === 'error' || step.kind === 'done') {
            endTurn();
            void persistTurn();
            const finalText = step.kind === 'final' ? step.text : null;
            if (finalText) void dataStore.storeAssistantFact(finalText, convIdRef.current);
          }
        });
        stopRef.current = () => {
          stop();
          session.stop();
          endTurn();
          void persistTurn();
        };
        session.onRunaway = () => stopRef.current?.();
      } else {
        const host = lanHost.trim();
        if (host.length === 0) {
          session.feed({ kind: 'error', text: 'Set the desktop address in settings (⚙) first.' });
          endTurn();
          return;
        }
        const port = Number.parseInt(lanPort, 10) || 8401;
        const client = new McpClient({
          url: `http://${host}:${port}/rpc`,
          token: lanToken.trim() || undefined,
        });
        stopRef.current = () => {
          session.stop();
          endTurn();
        };
        try {
          const answer = await client.generate(text);
          session.feed({ kind: 'final', text: answer });
          void dataStore.storeAssistantFact(answer, convIdRef.current);
        } catch (err) {
          session.feed({
            kind: 'error',
            text: err instanceof Error ? err.message : 'Desktop offload failed.',
          });
        } finally {
          endTurn();
          void persistTurn();
        }
      }
    },
    [backend, endTurn, lanHost, lanPort, lanToken, personaId, persistTurn, session],
  );

  const onSend = useCallback(() => {
    const text = input.trim();
    if (busy || text.length === 0) return;
    setInput('');
    void runTurn(text);
  }, [busy, input, runTurn]);

  const onStop = useCallback(() => stopRef.current?.(), []);

  const onMic = useCallback(async () => {
    if (listening) {
      stopVoiceRef.current?.();
      stopVoiceRef.current = null;
      setListening(false);
      return;
    }
    setListening(true);
    const stop = await speech.start('', e => {
      if (e.kind === 'partial' || e.kind === 'final') {
        setInput(e.text);
      } else if (e.kind === 'error') {
        setListening(false);
      } else if (e.kind === 'state' && e.state === 'IDLE') {
        setListening(false);
      }
    });
    stopVoiceRef.current = stop;
  }, [listening]);

  const onRegenerate = useCallback(() => {
    if (busy) return;
    const text = session.rollbackLastTurn();
    if (text != null) void runTurn(text);
  }, [busy, runTurn, session]);

  const onNewChat = useCallback(() => {
    if (busy) return;
    convIdRef.current = newConversationId();
    convCreatedAtRef.current = Date.now();
    convTitleRef.current = null;
    session.loadMessages([]);
  }, [busy, session]);

  const selectPersona = useCallback((id: string) => {
    setPersonaIdState(id);
    setShowPersonas(false);
    void prefsStore.setPersonaId(id);
  }, []);

  const saveLanConfig = useCallback(() => {
    setShowLanConfig(false);
    void prefsStore.setDesktopEndpoint(
      lanHost.trim(),
      Number.parseInt(lanPort, 10) || 8401,
      lanToken.trim(),
    );
  }, [lanHost, lanPort, lanToken]);

  const persona = personaById(personaId);
  const canRegenerate = !busy && messages.some(m => m.role === 'user');

  return (
    <KeyboardAvoidingView
      style={[styles.root, { backgroundColor: t.bg, paddingTop: insets.top }]}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      {/* Header: title/persona · backend toggle · new chat */}
      <View style={[styles.header, { borderColor: t.border }]}>
        <Pressable onPress={() => setShowPersonas(s => !s)} accessibilityRole="button">
          <Text style={[styles.title, { color: t.text }]}>Mias</Text>
          <Text style={[styles.personaTag, { color: t.accent }]}>{persona.name} ▾</Text>
        </Pressable>
        <View style={styles.headerRight}>
          <View style={[styles.toggle, { borderColor: t.border }]}>
            {(['local', 'lan'] as const).map(b => (
              <Pressable
                key={b}
                style={[styles.toggleItem, backend === b && { backgroundColor: t.bgRaised }]}
                onPress={() => {
                  setBackend(b);
                  if (b === 'lan') setShowLanConfig(true);
                }}
                accessibilityRole="button"
              >
                <Text style={{ color: backend === b ? t.accent : t.textMuted, fontSize: 12 }}>
                  {b === 'local' ? 'On-device' : 'Desktop'}
                </Text>
              </Pressable>
            ))}
          </View>
          <Pressable onPress={onNewChat} accessibilityRole="button" accessibilityLabel="New chat">
            <Text style={[styles.newChat, { color: t.textMuted }]}>＋</Text>
          </Pressable>
        </View>
      </View>

      {/* Persona picker */}
      {showPersonas && (
        <View style={[styles.personaRow, { borderColor: t.border }]}>
          {PERSONAS.map(p => (
            <Pressable
              key={p.id}
              style={[
                styles.personaChip,
                { borderColor: p.id === personaId ? t.accent : t.border, backgroundColor: t.bgRaised },
              ]}
              onPress={() => selectPersona(p.id)}
              accessibilityRole="button"
            >
              <Text style={{ color: p.id === personaId ? t.accent : t.text, fontSize: 13 }}>{p.name}</Text>
              <Text style={{ color: t.textMuted, fontSize: 10 }}>{p.tagline}</Text>
            </Pressable>
          ))}
        </View>
      )}

      {/* Desktop LAN endpoint (persisted via MiasPrefs) */}
      {backend === 'lan' && showLanConfig && (
        <View style={[styles.lanRow, { borderColor: t.border }]}>
          <TextInput
            style={[styles.lanInput, styles.lanHost, { color: t.text, borderColor: t.border, backgroundColor: t.bgRaised }]}
            placeholder="desktop host"
            placeholderTextColor={t.textMuted}
            autoCapitalize="none"
            autoCorrect={false}
            value={lanHost}
            onChangeText={setLanHost}
          />
          <TextInput
            style={[styles.lanInput, styles.lanPort, { color: t.text, borderColor: t.border, backgroundColor: t.bgRaised }]}
            placeholder="port"
            placeholderTextColor={t.textMuted}
            keyboardType="number-pad"
            value={lanPort}
            onChangeText={setLanPort}
          />
          <TextInput
            style={[styles.lanInput, styles.lanHost, { color: t.text, borderColor: t.border, backgroundColor: t.bgRaised }]}
            placeholder="token (optional)"
            placeholderTextColor={t.textMuted}
            autoCapitalize="none"
            autoCorrect={false}
            secureTextEntry
            value={lanToken}
            onChangeText={setLanToken}
          />
          <Pressable
            style={[styles.lanSave, { backgroundColor: t.accent }]}
            onPress={saveLanConfig}
            accessibilityRole="button"
          >
            <Text style={styles.sendLabel}>✓</Text>
          </Pressable>
        </View>
      )}

      <FlatList
        ref={listRef}
        style={styles.list}
        contentContainerStyle={styles.listContent}
        data={messages}
        keyExtractor={m => m.id}
        renderItem={({ item }) => <Bubble m={item} t={t} />}
        ListEmptyComponent={
          <Text style={[styles.empty, { color: t.textMuted }]}>
            {backend === 'local'
              ? 'On-device chat. Send a message to start.'
              : 'Desktop offload over LAN. Configure the address, then send.'}
          </Text>
        }
        ListFooterComponent={
          canRegenerate ? (
            <Pressable onPress={onRegenerate} style={styles.regen} accessibilityRole="button">
              <Text style={{ color: t.textMuted, fontSize: 12 }}>↻ Regenerate</Text>
            </Pressable>
          ) : null
        }
      />

      <View style={[styles.inputRow, { borderColor: t.border, paddingBottom: Math.max(insets.bottom, 8) }]}>
        {speech.isAvailable && (
          <Pressable
            style={[styles.micBtn, { borderColor: listening ? t.accent : t.border, backgroundColor: t.bgRaised }]}
            onPress={onMic}
            accessibilityRole="button"
            accessibilityLabel={listening ? 'Stop voice input' : 'Voice input'}
          >
            <Text style={{ color: listening ? t.accent : t.textMuted, fontSize: 16 }}>🎤</Text>
          </Pressable>
        )}
        <TextInput
          style={[styles.input, { color: t.text, backgroundColor: t.bgRaised, borderColor: t.border }]}
          placeholder={listening ? 'Listening…' : 'Message Mias…'}
          placeholderTextColor={t.textMuted}
          value={input}
          onChangeText={setInput}
          multiline
          editable={!busy}
          onSubmitEditing={onSend}
        />
        <Pressable
          style={[styles.sendBtn, { backgroundColor: busy ? t.danger : t.accent }]}
          onPress={busy ? onStop : onSend}
          accessibilityRole="button"
          accessibilityLabel={busy ? 'Stop generating' : 'Send message'}
        >
          <Text style={styles.sendLabel}>{busy ? '■' : '➤'}</Text>
        </Pressable>
      </View>
    </KeyboardAvoidingView>
  );
}

function Bubble({ m, t }: { m: ChatMessage; t: Theme }): React.JSX.Element {
  const [showReasoning, setShowReasoning] = useState(false);
  const isUser = m.role === 'user';
  const isMeta = m.role === 'thought' || m.role === 'action';
  const bg =
    isUser ? t.userBubble
    : m.role === 'error' ? t.bgRaised
    : t.assistantBubble;
  const fg = m.role === 'error' ? t.danger : isMeta ? t.textMuted : isUser ? t.userText : t.text;

  return (
    <View style={[styles.bubbleRow, isUser ? styles.rowUser : styles.rowAssistant]}>
      <View style={[styles.bubble, { backgroundColor: bg, borderColor: t.border }]}>
        {m.reasoning != null && m.reasoning.length > 0 && (
          <Pressable onPress={() => setShowReasoning(s => !s)} accessibilityRole="button">
            <Text style={[styles.reasoningToggle, { color: t.textMuted }]}>
              {showReasoning ? '▾ Thinking' : '▸ Thinking'}
            </Text>
            {showReasoning && (
              <Text style={[styles.reasoning, { color: t.textMuted }]}>{m.reasoning}</Text>
            )}
          </Pressable>
        )}
        <Text style={[styles.bubbleText, { color: fg }, isMeta && styles.metaText]}>
          {m.text.length > 0 ? m.text : m.isStreaming ? '…' : ''}
        </Text>
        {m.isStreaming && <Text style={[styles.streamingDot, { color: t.accent }]}>●</Text>}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  header: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingHorizontal: 16, paddingVertical: 8, borderBottomWidth: StyleSheet.hairlineWidth,
  },
  title: { fontSize: 20, fontWeight: '600' },
  personaTag: { fontSize: 11 },
  headerRight: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  toggle: { flexDirection: 'row', borderWidth: StyleSheet.hairlineWidth, borderRadius: 16, overflow: 'hidden' },
  toggleItem: { paddingHorizontal: 10, paddingVertical: 6 },
  newChat: { fontSize: 20, paddingHorizontal: 2 },
  personaRow: {
    flexDirection: 'row', flexWrap: 'wrap', gap: 8,
    paddingHorizontal: 12, paddingVertical: 8, borderBottomWidth: StyleSheet.hairlineWidth,
  },
  personaChip: { borderWidth: 1, borderRadius: 12, paddingHorizontal: 10, paddingVertical: 6 },
  lanRow: {
    flexDirection: 'row', gap: 6, alignItems: 'center',
    paddingHorizontal: 12, paddingVertical: 8, borderBottomWidth: StyleSheet.hairlineWidth,
  },
  lanInput: {
    borderWidth: StyleSheet.hairlineWidth, borderRadius: 10,
    paddingHorizontal: 10, paddingVertical: 6, fontSize: 13,
  },
  lanHost: { flex: 3 },
  lanPort: { flex: 1 },
  lanSave: { width: 30, height: 30, borderRadius: 15, alignItems: 'center', justifyContent: 'center' },
  list: { flex: 1 },
  listContent: { padding: 12, gap: 8 },
  empty: { textAlign: 'center', marginTop: 48, fontSize: 14, lineHeight: 21, paddingHorizontal: 24 },
  regen: { alignSelf: 'center', paddingVertical: 10 },
  bubbleRow: { flexDirection: 'row' },
  rowUser: { justifyContent: 'flex-end' },
  rowAssistant: { justifyContent: 'flex-start' },
  bubble: {
    maxWidth: '86%', borderRadius: 14, paddingHorizontal: 12, paddingVertical: 8,
    borderWidth: StyleSheet.hairlineWidth,
  },
  bubbleText: { fontSize: 15, lineHeight: 22 },
  metaText: { fontSize: 12, fontStyle: 'italic' },
  reasoningToggle: { fontSize: 12, marginBottom: 4 },
  reasoning: { fontSize: 12, lineHeight: 17, marginBottom: 6 },
  streamingDot: { fontSize: 8, marginTop: 4 },
  inputRow: {
    flexDirection: 'row', alignItems: 'flex-end', gap: 8,
    paddingHorizontal: 12, paddingTop: 8, borderTopWidth: StyleSheet.hairlineWidth,
  },
  input: {
    flex: 1, borderWidth: StyleSheet.hairlineWidth, borderRadius: 18,
    paddingHorizontal: 14, paddingVertical: 8, fontSize: 15, maxHeight: 120,
  },
  micBtn: {
    width: 38, height: 38, borderRadius: 19, alignItems: 'center', justifyContent: 'center',
    borderWidth: StyleSheet.hairlineWidth,
  },
  sendBtn: {
    width: 38, height: 38, borderRadius: 19, alignItems: 'center', justifyContent: 'center',
  },
  sendLabel: { color: '#FFFFFF', fontSize: 15 },
});
