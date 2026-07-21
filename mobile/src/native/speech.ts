/**
 * Typed JS surface over the native `MiasSpeech` module. STT emits partial/final
 * transcript events; TTS speaks. Requests RECORD_AUDIO on start.
 */
import { NativeEventEmitter, NativeModules, PermissionsAndroid, Platform } from 'react-native';

const EVENT_NAME = 'MiasSpeech.event';

interface NativeSpeechModule {
  startListening(languageCode: string): Promise<void>;
  stopListening(): Promise<string>;
  cancel(): Promise<void>;
  speak(text: string): Promise<void>;
  stopSpeaking(): Promise<void>;
  addListener(eventName: string): void;
  removeListeners(count: number): void;
}

const native: NativeSpeechModule | undefined = NativeModules.MiasSpeech;

export type SpeechEvent =
  | { kind: 'partial'; text: string; confidence: number }
  | { kind: 'final'; text: string; confidence: number }
  | { kind: 'state'; state: string }
  | { kind: 'error'; text: string };

let emitter: NativeEventEmitter | null = null;
function getEmitter(): NativeEventEmitter | null {
  if (!native) return null;
  if (!emitter) emitter = new NativeEventEmitter();
  return emitter;
}

async function ensureMicPermission(): Promise<boolean> {
  if (Platform.OS !== 'android') return true;
  try {
    const granted = await PermissionsAndroid.request(
      PermissionsAndroid.PERMISSIONS.RECORD_AUDIO,
    );
    return granted === PermissionsAndroid.RESULTS.GRANTED;
  } catch {
    return false;
  }
}

export const speech = {
  isAvailable: native != null,

  /** Start STT; forwards partial/final/state/error events to `onEvent`. Returns a stop fn. */
  async start(
    languageCode: string,
    onEvent: (e: SpeechEvent) => void,
  ): Promise<() => void> {
    const em = getEmitter();
    if (!native || !em) {
      onEvent({ kind: 'error', text: 'Voice input is not available on this build.' });
      return () => {};
    }
    if (!(await ensureMicPermission())) {
      onEvent({ kind: 'error', text: 'Microphone permission denied.' });
      return () => {};
    }
    const sub = em.addListener(EVENT_NAME, (e: SpeechEvent) => onEvent(e));
    await native.startListening(languageCode).catch((err: unknown) => {
      onEvent({ kind: 'error', text: err instanceof Error ? err.message : 'Voice input failed.' });
    });
    return () => {
      native.stopListening().catch(() => {});
      sub.remove();
    };
  },

  async stop(): Promise<void> {
    await native?.stopListening().catch(() => {});
  },

  speak: (text: string) => native?.speak(text) ?? Promise.resolve(),
  stopSpeaking: () => native?.stopSpeaking() ?? Promise.resolve(),
};
