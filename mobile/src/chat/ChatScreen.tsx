/**
 * Chat — the S2 vertical slice. Thin view over the shared domain logic:
 * `ChatSession` (packages/domain) assembles the conversation from inference
 * step events; this screen only renders state and routes user actions.
 *
 * Two backends, one session:
 *  - Local  → native `MiasInference` module (streamed ReAct steps, on-device)
 *  - LAN    → `McpClient` (packages/bridge-protocol) to desktop/server.py
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
import { ChatSession, type ChatMessage } from '@mias/domain';
import { McpClient } from '@mias/bridge-protocol';
import { localInference } from '../native/inference';
import { darkTheme, lightTheme, type Theme } from '../theme';

type Backend = 'local' | 'lan';

let requestCounter = 0;
const nextRequestId = () => `req-${++requestCounter}-${Date.now().toString(36)}`;

export function ChatScreen(): React.JSX.Element {
  const scheme = useColorScheme();
  const t = scheme === 'dark' ? darkTheme : lightTheme;
  const insets = useSafeAreaInsets();

  const session = useMemo(() => new ChatSession(), []);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [busy, setBusy] = useState(false);
  const [backend, setBackend] = useState<Backend>('local');
  const [lanHost, setLanHost] = useState('');
  const [lanToken, setLanToken] = useState('');
  const stopRef = useRef<(() => void) | null>(null);
  const listRef = useRef<FlatList<ChatMessage>>(null);

  useEffect(() => session.subscribe(setMessages), [session]);
  useEffect(() => {
    if (localInference.isAvailable) localInference.warmUp().catch(() => {});
  }, []);
  useEffect(() => {
    const id = setTimeout(() => listRef.current?.scrollToEnd({ animated: true }), 50);
    return () => clearTimeout(id);
  }, [messages.length]);

  const endTurn = useCallback(() => {
    stopRef.current = null;
    setBusy(false);
  }, []);

  const onSend = useCallback(async () => {
    const text = input.trim();
    if (busy || text.length === 0) return;
    setInput('');
    setBusy(true);
    session.startTurn(text);

    if (backend === 'local') {
      const requestId = nextRequestId();
      const stop = localInference.send(requestId, text, '', step => {
        session.feed(step);
        if (step.kind === 'final' || step.kind === 'error' || step.kind === 'done') endTurn();
      });
      stopRef.current = () => {
        stop();
        session.stop();
        endTurn();
      };
      session.onRunaway = () => stopRef.current?.();
    } else {
      const host = lanHost.trim();
      if (host.length === 0) {
        session.feed({ kind: 'error', text: 'Set the desktop address first (host:port).' });
        endTurn();
        return;
      }
      const url = `http://${host.includes(':') ? host : `${host}:8401`}/rpc`;
      const client = new McpClient({ url, token: lanToken.trim() || undefined });
      stopRef.current = () => {
        session.stop();
        endTurn();
      };
      try {
        const answer = await client.generate(text);
        session.feed({ kind: 'final', text: answer });
      } catch (err) {
        session.feed({
          kind: 'error',
          text: err instanceof Error ? err.message : 'Desktop offload failed.',
        });
      } finally {
        endTurn();
      }
    }
  }, [backend, busy, endTurn, input, lanHost, lanToken, session]);

  const onStop = useCallback(() => stopRef.current?.(), []);

  return (
    <KeyboardAvoidingView
      style={[styles.root, { backgroundColor: t.bg, paddingTop: insets.top }]}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <Header t={t} backend={backend} onToggle={setBackend} />
      {backend === 'lan' && (
        <View style={[styles.lanRow, { borderColor: t.border }]}>
          <TextInput
            style={[styles.lanInput, { color: t.text, borderColor: t.border, backgroundColor: t.bgRaised }]}
            placeholder="desktop host:port"
            placeholderTextColor={t.textMuted}
            autoCapitalize="none"
            autoCorrect={false}
            value={lanHost}
            onChangeText={setLanHost}
          />
          <TextInput
            style={[styles.lanInput, { color: t.text, borderColor: t.border, backgroundColor: t.bgRaised }]}
            placeholder="token (optional)"
            placeholderTextColor={t.textMuted}
            autoCapitalize="none"
            autoCorrect={false}
            secureTextEntry
            value={lanToken}
            onChangeText={setLanToken}
          />
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
              : 'Desktop offload over LAN. Set the address above, then send.'}
          </Text>
        }
      />
      <View style={[styles.inputRow, { borderColor: t.border, paddingBottom: Math.max(insets.bottom, 8) }]}>
        <TextInput
          style={[styles.input, { color: t.text, backgroundColor: t.bgRaised, borderColor: t.border }]}
          placeholder="Message Mias…"
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

function Header({ t, backend, onToggle }: {
  t: Theme;
  backend: Backend;
  onToggle: (b: Backend) => void;
}): React.JSX.Element {
  return (
    <View style={[styles.header, { borderColor: t.border }]}>
      <Text style={[styles.title, { color: t.text }]}>Mias</Text>
      <View style={[styles.toggle, { borderColor: t.border }]}>
        {(['local', 'lan'] as const).map(b => (
          <Pressable
            key={b}
            style={[styles.toggleItem, backend === b && { backgroundColor: t.bgRaised }]}
            onPress={() => onToggle(b)}
            accessibilityRole="button"
          >
            <Text style={{ color: backend === b ? t.accent : t.textMuted, fontSize: 13 }}>
              {b === 'local' ? 'On-device' : 'Desktop LAN'}
            </Text>
          </Pressable>
        ))}
      </View>
    </View>
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
    paddingHorizontal: 16, paddingVertical: 10, borderBottomWidth: StyleSheet.hairlineWidth,
  },
  title: { fontSize: 20, fontWeight: '600' },
  toggle: { flexDirection: 'row', borderWidth: StyleSheet.hairlineWidth, borderRadius: 16, overflow: 'hidden' },
  toggleItem: { paddingHorizontal: 12, paddingVertical: 6 },
  lanRow: { flexDirection: 'row', gap: 8, paddingHorizontal: 12, paddingVertical: 8, borderBottomWidth: StyleSheet.hairlineWidth },
  lanInput: {
    flex: 1, borderWidth: StyleSheet.hairlineWidth, borderRadius: 10,
    paddingHorizontal: 10, paddingVertical: 6, fontSize: 13,
  },
  list: { flex: 1 },
  listContent: { padding: 12, gap: 8 },
  empty: { textAlign: 'center', marginTop: 48, fontSize: 14, lineHeight: 21, paddingHorizontal: 24 },
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
  sendBtn: {
    width: 38, height: 38, borderRadius: 19, alignItems: 'center', justifyContent: 'center',
  },
  sendLabel: { color: '#FFFFFF', fontSize: 15 },
});
