/**
 * Typed JS surface over the native `MiasModelHub` module. Model catalogue,
 * install with live progress events, role assignment, storage, HF token.
 */
import { NativeEventEmitter, NativeModules } from 'react-native';

const EVENT_NAME = 'MiasModelHub.download';

interface NativeModelHubModule {
  installedModels(): Promise<string>;
  browseCurated(): Promise<string>;
  install(modelId: string): Promise<void>;
  pauseDownload(modelId: string): Promise<void>;
  resumeDownload(modelId: string): Promise<void>;
  cancelDownload(modelId: string): Promise<void>;
  assignRole(modelId: string, role: string): Promise<void>;
  uninstall(modelId: string): Promise<void>;
  totalStorageUsed(): Promise<number>;
  setHuggingFaceToken(token: string): Promise<void>;
  getHuggingFaceToken(): Promise<string>;
  addListener(eventName: string): void;
  removeListeners(count: number): void;
}

const native: NativeModelHubModule | undefined = NativeModules.MiasModelHub;

export type ModelRole =
  | 'CHAT' | 'CODE' | 'RESEARCH' | 'CREATIVE' | 'SURVIVAL'
  | 'REASONING' | 'VISION' | 'EMBEDDING';

export interface InstalledModel {
  id: string;
  name: string;
  author: string;
  sizeOnDisk: number;
  parameterCount: string;
  quantization: string;
  capabilityRoles: ModelRole[];
  assignedRoles: ModelRole[];
}

export interface BrowseItem {
  id: string;
  name: string;
  author: string;
  description: string;
  sizeBytes: number;
  parameterCount: string;
  quantization: string;
  roles: ModelRole[];
  isInstalled: boolean;
  isRecommendedDefault: boolean;
}

export type DownloadStatus =
  | 'QUEUED' | 'DOWNLOADING' | 'PAUSED' | 'VERIFYING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';

export interface DownloadState {
  modelId: string;
  status: DownloadStatus;
  bytesDownloaded: number;
  totalBytes: number;
  speedBytesPerSec: number;
  progress: number;
  error?: string;
}

let emitter: NativeEventEmitter | null = null;
function getEmitter(): NativeEventEmitter | null {
  if (!native) return null;
  if (!emitter) emitter = new NativeEventEmitter();
  return emitter;
}

export const modelHub = {
  isAvailable: native != null,

  async installed(): Promise<InstalledModel[]> {
    if (!native) return [];
    try {
      return JSON.parse(await native.installedModels()) as InstalledModel[];
    } catch {
      return [];
    }
  },

  async browse(): Promise<BrowseItem[]> {
    if (!native) return [];
    try {
      return JSON.parse(await native.browseCurated()) as BrowseItem[];
    } catch {
      return [];
    }
  },

  install: (id: string) => native?.install(id) ?? Promise.resolve(),
  pause: (id: string) => native?.pauseDownload(id) ?? Promise.resolve(),
  resume: (id: string) => native?.resumeDownload(id) ?? Promise.resolve(),
  cancel: (id: string) => native?.cancelDownload(id) ?? Promise.resolve(),
  assignRole: (id: string, role: ModelRole) => native?.assignRole(id, role) ?? Promise.resolve(),
  uninstall: (id: string) => native?.uninstall(id) ?? Promise.resolve(),

  async storageUsed(): Promise<number> {
    return (await native?.totalStorageUsed().catch(() => 0)) ?? 0;
  },

  async getHfToken(): Promise<string> {
    return (await native?.getHuggingFaceToken().catch(() => '')) ?? '';
  },
  setHfToken: (token: string) => native?.setHuggingFaceToken(token) ?? Promise.resolve(),

  /** Subscribe to live download progress (map of active downloads). */
  onDownloads(cb: (downloads: DownloadState[]) => void): () => void {
    const em = getEmitter();
    if (!em) return () => {};
    const sub = em.addListener(EVENT_NAME, (e: { kind: string; downloads?: string }) => {
      if (e.kind !== 'download' || !e.downloads) return;
      try {
        cb(JSON.parse(e.downloads) as DownloadState[]);
      } catch {
        /* ignore malformed */
      }
    });
    return () => sub.remove();
  },
};
