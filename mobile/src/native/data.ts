/**
 * Typed JS surface over the native `MiasData` module (DataBridgeModule.kt).
 * JSON strings cross the bridge; this wrapper maps them to domain types.
 * Guarded: absent module (tests / other platforms) degrades to empty results.
 */
import { NativeModules } from 'react-native';
import type { ChatMessage } from '@mias/domain';

interface NativeDataModule {
  listConversations(): Promise<string>;
  getConversation(id: string): Promise<string | null>;
  saveConversation(json: string): Promise<void>;
  deleteConversation(id: string): Promise<void>;
  getTurnContext(query: string, conversationId: string, storeUserFact: boolean): Promise<string>;
  documentCount(): Promise<number>;
  ingestDocument(name: string, text: string): Promise<string>;
  storeAssistantFact(text: string, conversationId: string): Promise<void>;
}

const native: NativeDataModule | undefined = NativeModules.MiasData;

export interface ConversationSummary {
  id: string;
  title: string;
  updatedAt: number;
  messageCount: number;
}

export interface PersistedConversation {
  id: string;
  title: string;
  createdAt: number;
  updatedAt: number;
  messages: Array<{
    id: string;
    role: 'user' | 'assistant';
    content: string;
    timestamp: number;
    reasoning?: string;
  }>;
}

export interface TurnContext {
  hindsight: string;
  rag: string;
  sources: string[];
}

export const dataStore = {
  isAvailable: native != null,

  async listConversations(): Promise<ConversationSummary[]> {
    if (!native) return [];
    try {
      return JSON.parse(await native.listConversations()) as ConversationSummary[];
    } catch {
      return [];
    }
  },

  async getConversation(id: string): Promise<PersistedConversation | null> {
    if (!native) return null;
    try {
      const raw = await native.getConversation(id);
      return raw ? (JSON.parse(raw) as PersistedConversation) : null;
    } catch {
      return null;
    }
  },

  /** Persist the session's user/assistant messages (UI-only bubbles excluded). */
  async saveConversation(
    id: string,
    title: string,
    createdAt: number,
    messages: ChatMessage[],
  ): Promise<void> {
    if (!native) return;
    const persistable = messages
      .filter(m => m.role === 'user' || m.role === 'assistant')
      .map(m => ({
        id: m.id,
        role: m.role,
        content: m.text,
        timestamp: m.createdAt,
        ...(m.reasoning != null && m.reasoning.length > 0 ? { reasoning: m.reasoning } : {}),
      }));
    if (persistable.length === 0) return;
    const payload = {
      id,
      title,
      createdAt,
      updatedAt: Date.now(),
      messages: persistable,
    };
    try {
      await native.saveConversation(JSON.stringify(payload));
    } catch {
      // Losing a save is recoverable; failing the turn isn't (Kotlin parity).
    }
  },

  async deleteConversation(id: string): Promise<void> {
    await native?.deleteConversation(id).catch(() => {});
  },

  /** Hindsight + RAG context for a turn; best-effort empty on failure. */
  async getTurnContext(query: string, conversationId: string): Promise<TurnContext> {
    if (!native) return { hindsight: '', rag: '', sources: [] };
    try {
      return JSON.parse(await native.getTurnContext(query, conversationId, true)) as TurnContext;
    } catch {
      return { hindsight: '', rag: '', sources: [] };
    }
  },

  async storeAssistantFact(text: string, conversationId: string): Promise<void> {
    await native?.storeAssistantFact(text, conversationId).catch(() => {});
  },

  async documentCount(): Promise<number> {
    return (await native?.documentCount().catch(() => 0)) ?? 0;
  },

  /** Add plain text to the global knowledge base; resolves the stored name. */
  async ingestDocument(name: string, text: string): Promise<string | null> {
    if (!native) return null;
    try {
      return await native.ingestDocument(name, text);
    } catch {
      return null;
    }
  },
};

/** Map a persisted conversation to session messages. */
export function toChatMessages(c: PersistedConversation): ChatMessage[] {
  return c.messages.map(m => ({
    id: m.id,
    role: m.role,
    text: m.content,
    reasoning: m.reasoning ?? null,
    isStreaming: false,
    createdAt: m.timestamp,
  }));
}
