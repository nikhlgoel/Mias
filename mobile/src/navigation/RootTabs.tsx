/**
 * Bottom-tab navigation (React Navigation). The Chat tab is the default; Home
 * is the overview; Models/Knowledge/Settings manage the on-device brains.
 */
import React from 'react';
import { Text } from 'react-native';
import { NavigationContainer, DefaultTheme, DarkTheme } from '@react-navigation/native';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { useColorScheme } from 'react-native';
import { HomeScreen } from '../screens/HomeScreen';
import { ChatScreen } from '../chat/ChatScreen';
import { ModelsScreen } from '../screens/ModelsScreen';
import { VisionScreen } from '../screens/VisionScreen';
import { KnowledgeScreen } from '../screens/KnowledgeScreen';
import { SettingsScreen } from '../screens/SettingsScreen';
import { darkTheme, lightTheme } from '../theme';

const Tab = createBottomTabNavigator();

const ICONS: Record<string, string> = {
  Home: '⌂',
  Chat: '💬',
  Vision: '👁',
  Models: '🧠',
  Knowledge: '📚',
  Settings: '⚙',
};

export function RootTabs(): React.JSX.Element {
  const isDark = useColorScheme() === 'dark';
  const t = isDark ? darkTheme : lightTheme;
  const navTheme = {
    ...(isDark ? DarkTheme : DefaultTheme),
    colors: {
      ...(isDark ? DarkTheme : DefaultTheme).colors,
      primary: t.accent,
      background: t.bg,
      card: t.bgRaised,
      text: t.text,
      border: t.border,
    },
  };

  return (
    <NavigationContainer theme={navTheme}>
      <Tab.Navigator
        initialRouteName="Chat"
        screenOptions={({ route }) => ({
          headerShown: false,
          tabBarActiveTintColor: t.accent,
          tabBarInactiveTintColor: t.textMuted,
          tabBarStyle: { backgroundColor: t.bgRaised, borderTopColor: t.border },
          tabBarIcon: ({ color }) => (
            <Text style={{ color, fontSize: 18 }}>{ICONS[route.name] ?? '•'}</Text>
          ),
        })}
      >
        <Tab.Screen name="Home" component={HomeScreen} />
        <Tab.Screen name="Chat" component={ChatScreen} />
        <Tab.Screen name="Vision" component={VisionScreen} />
        <Tab.Screen name="Models" component={ModelsScreen} />
        <Tab.Screen name="Knowledge" component={KnowledgeScreen} />
        <Tab.Screen name="Settings" component={SettingsScreen} />
      </Tab.Navigator>
    </NavigationContainer>
  );
}
