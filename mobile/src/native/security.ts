/**
 * Typed JS surface over the native `MiasSecurity` module (SecurityBridgeModule.kt).
 * Secrets stay in the native ZkVault; values cross the bridge only transiently
 * and must never be written to JS-accessible storage.
 */
import { NativeModules } from 'react-native';

interface NativeSecurityModule {
  secureGet(key: string): Promise<string | null>;
  secureSet(key: string, value: string): Promise<void>;
  secureRemove(key: string): Promise<void>;
  hasSecret(key: string): Promise<boolean>;
  authenticate(title: string, subtitle: string): Promise<boolean>;
}

const native: NativeSecurityModule | undefined = NativeModules.MiasSecurity;

export const security = {
  isAvailable: native != null,

  async secureGet(key: string): Promise<string | null> {
    return (await native?.secureGet(key).catch(() => null)) ?? null;
  },

  async secureSet(key: string, value: string): Promise<void> {
    await native?.secureSet(key, value);
  },

  async secureRemove(key: string): Promise<void> {
    await native?.secureRemove(key).catch(() => {});
  },

  async hasSecret(key: string): Promise<boolean> {
    return (await native?.hasSecret(key).catch(() => false)) ?? false;
  },

  /** Strong-biometric re-auth for sensitive actions. False on cancel/unavailable. */
  async authenticate(title = '', subtitle = ''): Promise<boolean> {
    if (!native) return false;
    try {
      return await native.authenticate(title, subtitle);
    } catch {
      return false;
    }
  },
};
