/**
 * Mias — React Native app root.
 *
 * Stage S4: full multi-screen surface (Home, Chat, Models, Knowledge, Settings)
 * under bottom-tab navigation. The native BiometricGateActivity has already
 * authenticated the user before this tree mounts.
 */
import React from 'react';
import { StatusBar, useColorScheme } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { enableScreens } from 'react-native-screens';
import { RootTabs } from './src/navigation/RootTabs';

enableScreens();

function App(): React.JSX.Element {
  const isDark = useColorScheme() === 'dark';
  return (
    <SafeAreaProvider>
      <StatusBar barStyle={isDark ? 'light-content' : 'dark-content'} />
      <RootTabs />
    </SafeAreaProvider>
  );
}

export default App;
