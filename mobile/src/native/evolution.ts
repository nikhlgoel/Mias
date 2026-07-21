/**
 * Typed JS surface over the native `MiasEvolution` module — self-learning
 * (consolidate memories, analyze conversations, optimize). Heavy work runs in
 * core/evolution; this triggers a manual run, reports status, and schedules the
 * periodic background job.
 */
import { NativeModules } from 'react-native';

interface NativeEvolutionModule {
  isRunning(): Promise<boolean>;
  runNow(): Promise<string>;
  scheduleBackground(): Promise<void>;
}

const native: NativeEvolutionModule | undefined = NativeModules.MiasEvolution;

export interface EvolutionSummary {
  id: string;
  success: boolean;
  insights: number;
  tasks: string[];
  errors: string[];
}

export const evolution = {
  isAvailable: native != null,

  async isRunning(): Promise<boolean> {
    return (await native?.isRunning().catch(() => false)) ?? false;
  },

  async runNow(): Promise<EvolutionSummary | null> {
    if (!native) return null;
    try {
      return JSON.parse(await native.runNow()) as EvolutionSummary;
    } catch {
      return null;
    }
  },

  async scheduleBackground(): Promise<void> {
    await native?.scheduleBackground().catch(() => {});
  },
};
