/**
 * Home — a calm dashboard: device health (SoC/battery), installed-model count,
 * knowledge size, and quick entry points. Read-only overview.
 */
import React, { useEffect, useState } from 'react';
import { ScrollView, StyleSheet, Text, View, useColorScheme } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { thermal, type DeviceHealth } from '../native/thermal';
import { modelHub } from '../native/modelHub';
import { dataStore } from '../native/data';
import { darkTheme, lightTheme, type Theme } from '../theme';

export function HomeScreen(): React.JSX.Element {
  const t = useColorScheme() === 'dark' ? darkTheme : lightTheme;
  const insets = useSafeAreaInsets();
  const [health, setHealth] = useState<DeviceHealth>({ available: false });
  const [modelCount, setModelCount] = useState(0);
  const [docCount, setDocCount] = useState(0);

  useEffect(() => {
    let alive = true;
    const load = async () => {
      const [h, models, docs] = await Promise.all([
        thermal.getHealth(),
        modelHub.installed(),
        dataStore.documentCount(),
      ]);
      if (!alive) return;
      setHealth(h);
      setModelCount(models.length);
      setDocCount(docs);
    };
    void load();
    const id = setInterval(load, 5000);
    return () => {
      alive = false;
      clearInterval(id);
    };
  }, []);

  return (
    <ScrollView
      style={[styles.root, { backgroundColor: t.bg }]}
      contentContainerStyle={{ padding: 16, paddingTop: insets.top + 24 }}
    >
      <Text style={[styles.hi, { color: t.text }]}>Mias</Text>
      <Text style={[styles.sub, { color: t.textMuted }]}>Private, on-device assistant</Text>

      <View style={styles.grid}>
        <Stat t={t} label="Models" value={String(modelCount)} />
        <Stat t={t} label="Knowledge" value={`${docCount} doc${docCount === 1 ? '' : 's'}`} />
        <Stat
          t={t}
          label="Battery"
          value={health.available && health.batteryLevel != null ? `${health.batteryLevel}%` : '—'}
        />
        <Stat
          t={t}
          label="SoC temp"
          value={health.available && health.socTempCelsius != null ? `${health.socTempCelsius.toFixed(0)}°C` : '—'}
        />
      </View>

      <Text style={[styles.tip, { color: t.textMuted }]}>
        Use the tabs below: chat on-device or offload to your desktop, manage
        models in the Brain Market, and grow your knowledge base.
      </Text>
    </ScrollView>
  );
}

function Stat({ t, label, value }: { t: Theme; label: string; value: string }) {
  return (
    <View style={[styles.stat, { backgroundColor: t.bgRaised, borderColor: t.border }]}>
      <Text style={[styles.statValue, { color: t.text }]}>{value}</Text>
      <Text style={[styles.statLabel, { color: t.textMuted }]}>{label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  hi: { fontSize: 30, fontWeight: '700' },
  sub: { fontSize: 14, marginTop: 4, marginBottom: 24 },
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: 12 },
  stat: {
    width: '47%', borderRadius: 14, borderWidth: StyleSheet.hairlineWidth,
    paddingVertical: 18, paddingHorizontal: 14,
  },
  statValue: { fontSize: 22, fontWeight: '600' },
  statLabel: { fontSize: 12, marginTop: 4 },
  tip: { fontSize: 13, lineHeight: 20, marginTop: 28 },
});
