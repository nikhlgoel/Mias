/**
 * Knowledge base — the RAG document store. Shows the document count and lets
 * the user paste text to ingest (file-picker ingest is a later addition).
 */
import React, { useCallback, useEffect, useState } from 'react';
import {
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
  useColorScheme,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { dataStore } from '../native/data';
import { darkTheme, lightTheme } from '../theme';

export function KnowledgeScreen(): React.JSX.Element {
  const t = useColorScheme() === 'dark' ? darkTheme : lightTheme;
  const insets = useSafeAreaInsets();
  const [count, setCount] = useState(0);
  const [name, setName] = useState('');
  const [text, setText] = useState('');
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const refresh = useCallback(async () => setCount(await dataStore.documentCount()), []);
  useEffect(() => {
    void refresh();
  }, [refresh]);

  const onIngest = async () => {
    const body = text.trim();
    if (body.length === 0 || busy) return;
    setBusy(true);
    const docName = name.trim() || `Note ${new Date().toLocaleString()}`;
    const stored = await dataStore.ingestDocument(docName, body);
    setBusy(false);
    if (stored != null) {
      setNotice(`Added "${stored}" — I can answer from it now.`);
      setName('');
      setText('');
      void refresh();
    } else {
      setNotice('Could not add that (is an embedding model installed?).');
    }
  };

  return (
    <ScrollView
      style={[styles.root, { backgroundColor: t.bg }]}
      contentContainerStyle={{ padding: 16, paddingTop: insets.top + 8 }}
    >
      <Text style={[styles.header, { color: t.text }]}>Knowledge</Text>
      <Text style={[styles.count, { color: t.textMuted }]}>
        {count} document{count === 1 ? '' : 's'} in your on-device knowledge base
      </Text>

      <Text style={[styles.label, { color: t.text }]}>Add a note</Text>
      <TextInput
        style={[styles.input, { color: t.text, backgroundColor: t.bgRaised, borderColor: t.border }]}
        placeholder="Title (optional)"
        placeholderTextColor={t.textMuted}
        value={name}
        onChangeText={setName}
      />
      <TextInput
        style={[styles.input, styles.multiline, { color: t.text, backgroundColor: t.bgRaised, borderColor: t.border }]}
        placeholder="Paste text to remember for future answers…"
        placeholderTextColor={t.textMuted}
        value={text}
        onChangeText={setText}
        multiline
      />
      <Pressable
        style={[styles.btn, { backgroundColor: t.accent, opacity: text.trim().length === 0 ? 0.5 : 1 }]}
        onPress={onIngest}
        disabled={busy || text.trim().length === 0}
        accessibilityRole="button"
      >
        <Text style={styles.btnLabel}>{busy ? 'Adding…' : 'Add to knowledge base'}</Text>
      </Pressable>
      {notice != null && <Text style={[styles.notice, { color: t.textMuted }]}>{notice}</Text>}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  header: { fontSize: 22, fontWeight: '600' },
  count: { fontSize: 13, marginTop: 4, marginBottom: 20 },
  label: { fontSize: 14, fontWeight: '600', marginBottom: 8 },
  input: {
    borderWidth: StyleSheet.hairlineWidth, borderRadius: 10,
    paddingHorizontal: 12, paddingVertical: 10, fontSize: 14, marginBottom: 10,
  },
  multiline: { minHeight: 120, textAlignVertical: 'top' },
  btn: { borderRadius: 12, paddingVertical: 12, alignItems: 'center', marginTop: 4 },
  btnLabel: { color: '#FFFFFF', fontSize: 14, fontWeight: '600' },
  notice: { fontSize: 13, marginTop: 12, lineHeight: 19 },
});
