/**
 * Mias — React Native shell (Phase R0 scaffold).
 *
 * Placeholder root. The biometric gate (R0.4), then the first real screen —
 * Chat over LAN MCP offload (R1) — land on top of this. Heavy logic stays
 * native (wrapped); this TS layer is the thin view surface (see /bridge/docs
 * and the migration plan).
 */
import React from 'react';
import { StatusBar, StyleSheet, Text, useColorScheme, View } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { BRIDGE_PROTOCOL_VERSION, MCP_PROTOCOL_VERSION } from '@mias/bridge-protocol';

function App(): React.JSX.Element {
  const isDark = useColorScheme() === 'dark';
  // Never pure #000/#FFF (eye-comfort tokens from 10-UX-UI): near-black / off-white.
  const bg = isDark ? '#0E0F12' : '#F5F5F4';
  const fg = isDark ? '#E6E7EA' : '#1A1B1E';

  return (
    <SafeAreaProvider>
      <StatusBar barStyle={isDark ? 'light-content' : 'dark-content'} />
      <View style={[styles.container, { backgroundColor: bg }]}>
        <Text style={[styles.title, { color: fg }]}>Mias</Text>
        <Text style={[styles.subtitle, { color: fg }]}>
          {`Bridge protocol v${BRIDGE_PROTOCOL_VERSION} · MCP ${MCP_PROTOCOL_VERSION}`}
        </Text>
      </View>
    </SafeAreaProvider>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  title: { fontSize: 32, fontWeight: '600' },
  subtitle: { fontSize: 14, marginTop: 8, opacity: 0.7 },
});

export default App;
