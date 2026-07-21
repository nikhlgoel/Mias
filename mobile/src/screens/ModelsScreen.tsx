/**
 * Brain Market — browse the curated catalogue, install with live progress,
 * manage installed models + role assignment. Wraps the native MiasModelHub.
 */
import React, { useCallback, useEffect, useState } from 'react';
import {
  ActivityIndicator,
  FlatList,
  Pressable,
  RefreshControl,
  StyleSheet,
  Text,
  View,
  useColorScheme,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import {
  modelHub,
  type BrowseItem,
  type DownloadState,
  type InstalledModel,
} from '../native/modelHub';
import { formatBytes } from '../util/format';
import { darkTheme, lightTheme, type Theme } from '../theme';

export function ModelsScreen(): React.JSX.Element {
  const t = useColorScheme() === 'dark' ? darkTheme : lightTheme;
  const insets = useSafeAreaInsets();
  const [browse, setBrowse] = useState<BrowseItem[]>([]);
  const [installed, setInstalled] = useState<InstalledModel[]>([]);
  const [downloads, setDownloads] = useState<Record<string, DownloadState>>({});
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async () => {
    const [b, i] = await Promise.all([modelHub.browse(), modelHub.installed()]);
    setBrowse(b);
    setInstalled(i);
    setLoading(false);
  }, []);

  useEffect(() => {
    void refresh();
    const un = modelHub.onDownloads(list => {
      setDownloads(Object.fromEntries(list.map(d => [d.modelId, d])));
      // A completed download changes the installed set.
      if (list.some(d => d.status === 'COMPLETED')) void refresh();
    });
    return un;
  }, [refresh]);

  const installedIds = new Set(installed.map(m => m.id));

  return (
    <View style={[styles.root, { backgroundColor: t.bg, paddingTop: insets.top + 8 }]}>
      <Text style={[styles.header, { color: t.text }]}>Brain Market</Text>
      {loading ? (
        <ActivityIndicator style={{ marginTop: 32 }} color={t.accent} />
      ) : (
        <FlatList
          data={browse}
          keyExtractor={m => m.id}
          contentContainerStyle={styles.list}
          refreshControl={<RefreshControl refreshing={false} onRefresh={refresh} tintColor={t.accent} />}
          renderItem={({ item }) => (
            <ModelCard
              t={t}
              item={item}
              installed={installedIds.has(item.id)}
              download={downloads[item.id]}
            />
          )}
          ListEmptyComponent={
            <Text style={[styles.empty, { color: t.textMuted }]}>
              {modelHub.isAvailable ? 'No models in the catalogue.' : 'Model hub unavailable on this build.'}
            </Text>
          }
        />
      )}
    </View>
  );
}

function ModelCard({ t, item, installed, download }: {
  t: Theme;
  item: BrowseItem;
  installed: boolean;
  download?: DownloadState;
}): React.JSX.Element {
  const [busy, setBusy] = useState(false);
  const active = download && download.status !== 'COMPLETED' && download.status !== 'CANCELLED';

  const onInstall = async () => {
    setBusy(true);
    await modelHub.install(item.id).catch(() => {});
    setBusy(false);
  };

  return (
    <View style={[styles.card, { backgroundColor: t.bgRaised, borderColor: t.border }]}>
      <View style={styles.cardTop}>
        <Text style={[styles.name, { color: t.text }]}>{item.name}</Text>
        {item.isRecommendedDefault && (
          <Text style={[styles.badge, { color: t.accent, borderColor: t.accent }]}>recommended</Text>
        )}
      </View>
      <Text style={[styles.meta, { color: t.textMuted }]} numberOfLines={2}>
        {item.author} · {item.parameterCount} · {item.quantization} · {formatBytes(item.sizeBytes)}
      </Text>
      <Text style={[styles.desc, { color: t.textMuted }]} numberOfLines={2}>{item.description}</Text>

      {active && download ? (
        <View style={styles.progressRow}>
          <View style={[styles.progressTrack, { backgroundColor: t.border }]}>
            <View style={[styles.progressFill, { backgroundColor: t.accent, width: `${Math.round(download.progress * 100)}%` }]} />
          </View>
          <Text style={[styles.progressText, { color: t.textMuted }]}>
            {download.status === 'DOWNLOADING'
              ? `${Math.round(download.progress * 100)}% · ${formatBytes(download.speedBytesPerSec)}/s`
              : download.status.toLowerCase()}
          </Text>
          <Pressable onPress={() => void modelHub.cancel(item.id)} accessibilityRole="button">
            <Text style={{ color: t.danger, fontSize: 13 }}>Cancel</Text>
          </Pressable>
        </View>
      ) : installed ? (
        <Text style={[styles.installed, { color: t.accent }]}>✓ Installed</Text>
      ) : (
        <Pressable
          style={[styles.installBtn, { backgroundColor: t.accent }]}
          onPress={onInstall}
          disabled={busy}
          accessibilityRole="button"
        >
          <Text style={styles.installLabel}>{busy ? 'Starting…' : 'Install'}</Text>
        </Pressable>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, paddingHorizontal: 12 },
  header: { fontSize: 22, fontWeight: '600', marginBottom: 8, paddingHorizontal: 4 },
  list: { gap: 10, paddingBottom: 24 },
  empty: { textAlign: 'center', marginTop: 48, fontSize: 14 },
  card: { borderRadius: 14, borderWidth: StyleSheet.hairlineWidth, padding: 12 },
  cardTop: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  name: { fontSize: 16, fontWeight: '600', flexShrink: 1 },
  badge: { fontSize: 10, borderWidth: 1, borderRadius: 8, paddingHorizontal: 6, paddingVertical: 1 },
  meta: { fontSize: 12, marginTop: 4 },
  desc: { fontSize: 13, marginTop: 6, lineHeight: 18 },
  installBtn: { alignSelf: 'flex-start', marginTop: 10, borderRadius: 16, paddingHorizontal: 18, paddingVertical: 7 },
  installLabel: { color: '#FFFFFF', fontSize: 13, fontWeight: '600' },
  installed: { marginTop: 10, fontSize: 13, fontWeight: '600' },
  progressRow: { flexDirection: 'row', alignItems: 'center', gap: 8, marginTop: 10 },
  progressTrack: { flex: 1, height: 6, borderRadius: 3, overflow: 'hidden' },
  progressFill: { height: 6, borderRadius: 3 },
  progressText: { fontSize: 11, minWidth: 90 },
});
