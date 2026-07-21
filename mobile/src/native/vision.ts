/**
 * Typed JS surface over the native `MiasVision` module — on-device image
 * understanding with the installed VISION (.task) model, streamed as deltas.
 */
import { NativeEventEmitter, NativeModules } from 'react-native';

const EVENT_NAME = 'MiasVision.step';

interface NativeVisionModule {
  hasVisionModel(): Promise<boolean>;
  describe(requestId: string, imageUri: string, prompt: string): Promise<void>;
  stop(requestId: string): void;
  addListener(eventName: string): void;
  removeListeners(count: number): void;
}

const native: NativeVisionModule | undefined = NativeModules.MiasVision;

export type VisionStep =
  | { kind: 'delta'; text: string }
  | { kind: 'final' }
  | { kind: 'error'; text: string };

type NativeStep = { requestId: string; kind: VisionStep['kind']; text?: string };

let emitter: NativeEventEmitter | null = null;
function getEmitter(): NativeEventEmitter | null {
  if (!native) return null;
  if (!emitter) emitter = new NativeEventEmitter();
  return emitter;
}

export const vision = {
  isAvailable: native != null,

  async hasVisionModel(): Promise<boolean> {
    return (await native?.hasVisionModel().catch(() => false)) ?? false;
  },

  /** Stream a description for `imageUri`; returns a stop function. */
  describe(
    requestId: string,
    imageUri: string,
    prompt: string,
    onStep: (s: VisionStep) => void,
  ): () => void {
    const em = getEmitter();
    if (!native || !em) {
      onStep({ kind: 'error', text: 'Vision is not available on this build.' });
      return () => {};
    }
    const sub = em.addListener(EVENT_NAME, (e: NativeStep) => {
      if (e.requestId !== requestId) return;
      const step: VisionStep =
        e.kind === 'delta' ? { kind: 'delta', text: e.text ?? '' }
        : e.kind === 'error' ? { kind: 'error', text: e.text ?? 'vision failed' }
        : { kind: 'final' };
      onStep(step);
      if (step.kind === 'final' || step.kind === 'error') sub.remove();
    });
    native.describe(requestId, imageUri, prompt).catch((err: unknown) => {
      onStep({ kind: 'error', text: err instanceof Error ? err.message : 'vision failed' });
      sub.remove();
    });
    return () => {
      native.stop(requestId);
      sub.remove();
    };
  },
};
