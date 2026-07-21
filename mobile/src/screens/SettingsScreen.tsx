/**
 * Settings — HuggingFace token, desktop offload endpoint, default persona,
 * storage used. All persisted through the native prefs/model-hub/security
 * modules (same stores the Kotlin app used).
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
import { PERSONAS } from '@mias/domain';
import { prefsStore } from '../native/prefs';
import { modelHub } from '../native/modelHub';
import { evolution } from '../native/evolution';
import { formatBytes } from '../util/format';
import { darkTheme, lightTheme, type Theme } from '../theme';

export function SettingsScreen(): React.JSX.Element {
  const t = useColorScheme() === 'dark' ? darkTheme : lightTheme;
  const insets = useSafeAreaInsets();
  const [hfToken, setHfToken] = useState('');
  const [host, setHost] = useState('');
  const [port, setPort] = useState('8401');
  const [token, setToken] = useState('');
  const [personaId, setPersonaId] = useState('default');
  const [storage, setStorage] = useState(0);
  const [saved, setSaved] = useState<string | null>(null);
  const [evolving, setEvolving] = useState(false);
  const [evoStatus, setEvoStatus] = useState<string | null>(null);

  useEffect(() => {
    void (async () => {
      const [p, hf, used] = await Promise.all([
        prefsStore.get(),
        modelHub.getHfToken(),
        modelHub.storageUsed(),
      ]);
      setHost(p.desktopHost);
      setPort(String(p.desktopPort));
      setToken(p.desktopToken);
      setPersonaId(p.personaId);
      setHfToken(hf);
      setStorage(used);
    })();
  }, []);

  const flash = useCallback((msg: string) => {
    setSaved(msg);
    setTimeout(() => setSaved(null), 1800);
  }, []);

  const saveDesktop = async () => {
    await prefsStore.setDesktopEndpoint(host.trim(), Number.parseInt(port, 10) || 8401, token.trim());
    flash('Desktop endpoint saved');
  };
  const saveHf = async () => {
    await modelHub.setHfToken(hfToken.trim());
    flash('HuggingFace token saved');
  };
  const selectPersona = async (id: string) => {
    setPersonaId(id);
    await prefsStore.setPersonaId(id);
    flash('Default persona set');
  };

  const runEvolution = async () => {
    if (evolving) return;
    setEvolving(true);
    setEvoStatus('Learning from your recent activity…');
    await evolution.scheduleBackground();
    const summary = await evolution.runNow();
    setEvolving(false);
    setEvoStatus(
      summary == null
        ? 'Self-learning unavailable on this build.'
        : summary.success
          ? `Done — ${summary.insights} insight${summary.insights === 1 ? '' : 's'} from ${summary.tasks.length} task${summary.tasks.length === 1 ? '' : 's'}.`
          : `Finished with issues: ${summary.errors[0] ?? 'unknown'}`,
    );
  };

  return (
    <ScrollView
      style={[styles.root, { backgroundColor: t.bg }]}
      contentContainerStyle={{ padding: 16, paddingTop: insets.top + 8, paddingBottom: 40 }}
    >
      <Text style={[styles.header, { color: t.text }]}>Settings</Text>

      <Section t={t} title="Default persona">
        <View style={styles.personaWrap}>
          {PERSONAS.map(p => (
            <Pressable
              key={p.id}
              style={[styles.chip, { borderColor: p.id === personaId ? t.accent : t.border, backgroundColor: t.bgRaised }]}
              onPress={() => void selectPersona(p.id)}
              accessibilityRole="button"
            >
              <Text style={{ color: p.id === personaId ? t.accent : t.text, fontSize: 13 }}>{p.name}</Text>
            </Pressable>
          ))}
        </View>
      </Section>

      <Section t={t} title="Desktop offload (LAN)">
        <Field t={t} placeholder="host" value={host} onChange={setHost} />
        <Field t={t} placeholder="port" value={port} onChange={setPort} keyboard="number-pad" />
        <Field t={t} placeholder="token (optional)" value={token} onChange={setToken} secure />
        <SaveButton t={t} onPress={saveDesktop} />
      </Section>

      <Section t={t} title="HuggingFace token">
        <Text style={[styles.hint, { color: t.textMuted }]}>
          Optional — needed only for gated/private model downloads.
        </Text>
        <Field t={t} placeholder="hf_…" value={hfToken} onChange={setHfToken} secure />
        <SaveButton t={t} onPress={saveHf} />
      </Section>

      <Section t={t} title="Self-learning">
        <Text style={[styles.hint, { color: t.textMuted }]}>
          Consolidates memories and learns from your conversations on-device. Runs
          automatically when idle; you can also run it now.
        </Text>
        <Pressable
          style={[styles.save, { backgroundColor: t.accent, opacity: evolving ? 0.6 : 1 }]}
          onPress={runEvolution}
          disabled={evolving}
          accessibilityRole="button"
        >
          <Text style={styles.saveLabel}>{evolving ? 'Learning…' : 'Run now'}</Text>
        </Pressable>
        {evoStatus != null && <Text style={[styles.hint, { color: t.textMuted, marginTop: 8 }]}>{evoStatus}</Text>}
      </Section>

      <Section t={t} title="Storage">
        <Text style={[styles.hint, { color: t.textMuted }]}>
          Models on device: {formatBytes(storage)}
        </Text>
      </Section>

      {saved != null && <Text style={[styles.saved, { color: t.accent }]}>{saved}</Text>}
    </ScrollView>
  );
}

function Section({ t, title, children }: { t: Theme; title: string; children: React.ReactNode }) {
  return (
    <View style={styles.section}>
      <Text style={[styles.sectionTitle, { color: t.text }]}>{title}</Text>
      {children}
    </View>
  );
}

function Field({ t, placeholder, value, onChange, secure, keyboard }: {
  t: Theme;
  placeholder: string;
  value: string;
  onChange: (s: string) => void;
  secure?: boolean;
  keyboard?: 'number-pad';
}) {
  return (
    <TextInput
      style={[styles.input, { color: t.text, backgroundColor: t.bgRaised, borderColor: t.border }]}
      placeholder={placeholder}
      placeholderTextColor={t.textMuted}
      value={value}
      onChangeText={onChange}
      autoCapitalize="none"
      autoCorrect={false}
      secureTextEntry={secure}
      keyboardType={keyboard}
    />
  );
}

function SaveButton({ t, onPress }: { t: Theme; onPress: () => void }) {
  return (
    <Pressable style={[styles.save, { backgroundColor: t.accent }]} onPress={onPress} accessibilityRole="button">
      <Text style={styles.saveLabel}>Save</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  header: { fontSize: 22, fontWeight: '600', marginBottom: 16 },
  section: { marginBottom: 24 },
  sectionTitle: { fontSize: 15, fontWeight: '600', marginBottom: 10 },
  hint: { fontSize: 12, marginBottom: 8, lineHeight: 17 },
  input: {
    borderWidth: StyleSheet.hairlineWidth, borderRadius: 10,
    paddingHorizontal: 12, paddingVertical: 10, fontSize: 14, marginBottom: 8,
  },
  personaWrap: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  chip: { borderWidth: 1, borderRadius: 14, paddingHorizontal: 12, paddingVertical: 6 },
  save: { alignSelf: 'flex-start', borderRadius: 12, paddingHorizontal: 22, paddingVertical: 8, marginTop: 2 },
  saveLabel: { color: '#FFFFFF', fontSize: 13, fontWeight: '600' },
  saved: { fontSize: 13, textAlign: 'center', marginTop: 4 },
});
