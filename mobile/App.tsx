/**
 * Mias — React Native app root.
 *
 * Stage S2: the Chat vertical slice (on-device streaming + desktop LAN
 * offload). The native BiometricGateActivity has already authenticated the
 * user before this tree mounts.
 */
import React from 'react';
import { StatusBar, useColorScheme } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { ChatScreen } from './src/chat/ChatScreen';

function App(): React.JSX.Element {
  const isDark = useColorScheme() === 'dark';
  return (
    <SafeAreaProvider>
      <StatusBar barStyle={isDark ? 'light-content' : 'dark-content'} />
      <ChatScreen />
    </SafeAreaProvider>
  );
}

export default App;
