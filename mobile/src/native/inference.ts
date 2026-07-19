/**
 * Typed JS surface over the native `MiasInference` bridge module
 * (android/.../bridge/InferenceBridgeModule.kt).
 *
 * Guarded: in test environments (or before the native side exists on a
 * platform) the module is absent — callers get a clean `isAvailable === false`
 * instead of a crash.
 */
import { NativeEventEmitter, NativeModules } from 'react-native';
import type { InferenceStep } from '@mias/domain';

const EVENT_NAME = 'MiasInference.step';

interface NativeInferenceModule {
  warmUp(): Promise<void>;
  send(requestId: string, prompt: string, systemPrompt: string): Promise<void>;
  stop(requestId: string): void;
  addListener(eventName: string): void;
  removeListeners(count: number): void;
}

type NativeStepEvent = {
  requestId: string;
  kind: 'token' | 'thought' | 'action' | 'observation' | 'final' | 'modelSwitch' | 'error' | 'done';
  text?: string;
  tool?: string;
  from?: string;
  to?: string;
};

const nativeModule: NativeInferenceModule | undefined = NativeModules.MiasInference;

let emitter: NativeEventEmitter | null = null;
function getEmitter(): NativeEventEmitter | null {
  if (!nativeModule) return null;
  if (!emitter) emitter = new NativeEventEmitter();
  return emitter;
}

function toStep(e: NativeStepEvent): InferenceStep {
  switch (e.kind) {
    case 'token': return { kind: 'token', text: e.text ?? '' };
    case 'thought': return { kind: 'thought', text: e.text ?? '' };
    case 'action': return { kind: 'action', tool: e.tool ?? '', text: e.text ?? '' };
    case 'observation': return { kind: 'observation', text: e.text ?? '' };
    case 'final': return { kind: 'final', text: e.text ?? '' };
    case 'modelSwitch': return { kind: 'modelSwitch', from: e.from ?? '', to: e.to ?? '' };
    case 'error': return { kind: 'error', text: e.text ?? 'inference failed' };
    case 'done': return { kind: 'done' };
  }
}

export const localInference = {
  isAvailable: nativeModule != null,

  async warmUp(): Promise<void> {
    await nativeModule?.warmUp();
  },

  /**
   * Start a streamed on-device turn. Steps for this request are forwarded to
   * `onStep` until final/error/done. Returns a stop function.
   */
  send(
    requestId: string,
    prompt: string,
    systemPrompt: string,
    onStep: (step: InferenceStep) => void,
  ): () => void {
    const em = getEmitter();
    if (!nativeModule || !em) {
      onStep({ kind: 'error', text: 'On-device inference is not available on this build.' });
      return () => {};
    }
    const sub = em.addListener(EVENT_NAME, (e: NativeStepEvent) => {
      if (e.requestId !== requestId) return;
      const step = toStep(e);
      onStep(step);
      if (step.kind === 'final' || step.kind === 'error' || step.kind === 'done') {
        sub.remove();
      }
    });
    nativeModule.send(requestId, prompt, systemPrompt).catch((err: unknown) => {
      onStep({ kind: 'error', text: err instanceof Error ? err.message : String(err) });
      sub.remove();
    });
    return () => {
      nativeModule.stop(requestId);
      sub.remove();
    };
  },
};
