/**
 * Vision — pick or capture an image, ask about it, and get a streamed on-device
 * description from the installed VISION (.task) model. Thin view over the native
 * MiasVision module.
 */
import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  Image,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
  useColorScheme,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { launchCamera, launchImageLibrary, type Asset } from 'react-native-image-picker';
import { vision } from '../native/vision';
import { darkTheme, lightTheme } from '../theme';

let visionCounter = 0;
const nextId = () => `vis-${++visionCounter}-${Date.now().toString(36)}`;

export function VisionScreen(): React.JSX.Element {
  const t = useColorScheme() === 'dark' ? darkTheme : lightTheme;
  const insets = useSafeAreaInsets();
  const [asset, setAsset] = useState<Asset | null>(null);
  const [prompt, setPrompt] = useState('');
  const [answer, setAnswer] = useState('');
  const [busy, setBusy] = useState(false);
  const [hasModel, setHasModel] = useState(true);
  const stopRef = useRef<(() => void) | null>(null);

  useEffect(() => {
    void vision.hasVisionModel().then(setHasModel);
  }, []);

  const pick = useCallback(async (from: 'library' | 'camera') => {
    const res = from === 'camera'
      ? await launchCamera({ mediaType: 'photo', quality: 0.8 })
      : await launchImageLibrary({ mediaType: 'photo', quality: 0.8 });
    const first = res.assets?.[0];
    if (first?.uri) {
      setAsset(first);
      setAnswer('');
    }
  }, []);

  const ask = useCallback(() => {
    if (busy || !asset?.uri) return;
    setBusy(true);
    setAnswer('');
    const requestId = nextId();
    const stop = vision.describe(requestId, asset.uri, prompt.trim(), step => {
      if (step.kind === 'delta') {
        setAnswer(prev => prev + step.text);
      } else if (step.kind === 'error') {
        setAnswer(step.text);
        setBusy(false);
      } else {
        setBusy(false);
      }
    });
    stopRef.current = stop;
  }, [asset, busy, prompt]);

  const onStop = useCallback(() => {
    stopRef.current?.();
    setBusy(false);
  }, []);

  return (
    <ScrollView
      style={[styles.root, { backgroundColor: t.bg }]}
      contentContainerStyle={{ padding: 16, paddingTop: insets.top + 8 }}
    >
      <Text style={[styles.header, { color: t.text }]}>Vision</Text>
      {!hasModel && (
        <Text style={[styles.warn, { color: t.textMuted }]}>
          No vision model installed — open Models and install a Vision (.task) bundle.
        </Text>
      )}

      <View style={styles.pickRow}>
        <Pressable style={[styles.pickBtn, { borderColor: t.border, backgroundColor: t.bgRaised }]} onPress={() => void pick('library')}>
          <Text style={{ color: t.text }}>🖼  Gallery</Text>
        </Pressable>
        <Pressable style={[styles.pickBtn, { borderColor: t.border, backgroundColor: t.bgRaised }]} onPress={() => void pick('camera')}>
          <Text style={{ color: t.text }}>📷  Camera</Text>
        </Pressable>
      </View>

      {asset?.uri != null && (
        <Image source={{ uri: asset.uri }} style={styles.preview} resizeMode="cover" />
      )}

      <TextInput
        style={[styles.input, { color: t.text, backgroundColor: t.bgRaised, borderColor: t.border }]}
        placeholder="Ask about the image (optional)…"
        placeholderTextColor={t.textMuted}
        value={prompt}
        onChangeText={setPrompt}
      />
      <Pressable
        style={[styles.ask, { backgroundColor: busy ? t.danger : t.accent, opacity: asset ? 1 : 0.5 }]}
        onPress={busy ? onStop : ask}
        disabled={!asset && !busy}
        accessibilityRole="button"
      >
        <Text style={styles.askLabel}>{busy ? 'Stop' : 'Describe'}</Text>
      </Pressable>

      {answer.length > 0 && (
        <View style={[styles.answer, { backgroundColor: t.assistantBubble, borderColor: t.border }]}>
          <Text style={[styles.answerText, { color: t.text }]}>{answer}</Text>
        </View>
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  header: { fontSize: 22, fontWeight: '600', marginBottom: 8 },
  warn: { fontSize: 13, marginBottom: 12, lineHeight: 18 },
  pickRow: { flexDirection: 'row', gap: 10, marginBottom: 12 },
  pickBtn: { flex: 1, borderWidth: StyleSheet.hairlineWidth, borderRadius: 12, paddingVertical: 14, alignItems: 'center' },
  preview: { width: '100%', height: 240, borderRadius: 14, marginBottom: 12 },
  input: {
    borderWidth: StyleSheet.hairlineWidth, borderRadius: 10,
    paddingHorizontal: 12, paddingVertical: 10, fontSize: 14, marginBottom: 10,
  },
  ask: { borderRadius: 12, paddingVertical: 12, alignItems: 'center' },
  askLabel: { color: '#FFFFFF', fontSize: 14, fontWeight: '600' },
  answer: { marginTop: 14, borderWidth: StyleSheet.hairlineWidth, borderRadius: 12, padding: 12 },
  answerText: { fontSize: 15, lineHeight: 22 },
});
