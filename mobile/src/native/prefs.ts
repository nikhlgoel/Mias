/**
 * Typed JS surface over the native `MiasPrefs` module (PrefsBridgeModule.kt) —
 * the same DataStore the Kotlin app uses, so settings survive the migration.
 * Guarded: absent module falls back to in-memory defaults.
 */
import { NativeModules } from 'react-native';

interface NativePrefsModule {
  getPrefs(): Promise<string>;
  setDesktopEndpoint(host: string, port: number, token: string): Promise<void>;
  setPersonaId(id: string): Promise<void>;
  setUseDocuments(enabled: boolean): Promise<void>;
}

const native: NativePrefsModule | undefined = NativeModules.MiasPrefs;

export interface AppPrefs {
  desktopHost: string;
  desktopPort: number;
  desktopToken: string;
  personaId: string;
  useDocuments: boolean;
}

const DEFAULTS: AppPrefs = {
  desktopHost: '',
  desktopPort: 8401,
  desktopToken: '',
  personaId: 'default',
  useDocuments: true,
};

export const prefsStore = {
  isAvailable: native != null,

  async get(): Promise<AppPrefs> {
    if (!native) return { ...DEFAULTS };
    try {
      return { ...DEFAULTS, ...(JSON.parse(await native.getPrefs()) as Partial<AppPrefs>) };
    } catch {
      return { ...DEFAULTS };
    }
  },

  async setDesktopEndpoint(host: string, port: number, token: string): Promise<void> {
    await native?.setDesktopEndpoint(host, port, token).catch(() => {});
  },

  async setPersonaId(id: string): Promise<void> {
    await native?.setPersonaId(id).catch(() => {});
  },

  async setUseDocuments(enabled: boolean): Promise<void> {
    await native?.setUseDocuments(enabled).catch(() => {});
  },
};
