/**
 * @format
 */

// Must be first: polyfills global crypto.getRandomValues so @noble (the Bridge
// SecureChannel/CPace crypto in @mias/bridge-protocol) has secure randomness in
// Hermes. Nothing crypto-related must run before this import.
import 'react-native-get-random-values';

import { AppRegistry } from 'react-native';
import App from './App';
import { name as appName } from './app.json';

AppRegistry.registerComponent(appName, () => App);
