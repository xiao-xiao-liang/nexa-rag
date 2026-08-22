import { ChatMessageVO } from "../../types";

export const MAX_HISTORY_CACHE_CONVERSATIONS = 20;

export type HistoryCacheEntry = {
  messages: ChatMessageVO[];
  hasMore: boolean;
  nextBeforeSequence?: number;
  lastAccessOrder: number;
};

export type HistoryCacheState = {
  entries: Record<string, HistoryCacheEntry>;
  accessOrder: number;
};

export const emptyHistoryCache = (): HistoryCacheState => ({ entries: {}, accessOrder: 0 });

export const getHistoryEntry = (cache: HistoryCacheState, conversationId: string): HistoryCacheEntry | undefined =>
  cache.entries[conversationId];

export const putHistoryPage = (
  cache: HistoryCacheState,
  conversationId: string,
  messages: ChatMessageVO[],
  hasMore: boolean,
  nextBeforeSequence?: number,
): HistoryCacheState => putEntry(cache, conversationId, messages, hasMore, nextBeforeSequence);

export const prependHistoryPage = (
  cache: HistoryCacheState,
  conversationId: string,
  messages: ChatMessageVO[],
  hasMore: boolean,
  nextBeforeSequence?: number,
): HistoryCacheState => {
  const existing = cache.entries[conversationId];
  return putEntry(cache, conversationId, [...messages, ...(existing?.messages ?? [])], hasMore, nextBeforeSequence);
};

export const removeHistoryEntry = (cache: HistoryCacheState, conversationId: string): HistoryCacheState => {
  const { [conversationId]: ignored, ...entries } = cache.entries;
  return { ...cache, entries };
};

export const touchHistoryEntry = (cache: HistoryCacheState, conversationId: string): HistoryCacheState => {
  const entry = cache.entries[conversationId];
  if (!entry) return cache;
  const accessOrder = cache.accessOrder + 1;
  return { ...cache, accessOrder, entries: { ...cache.entries, [conversationId]: { ...entry, lastAccessOrder: accessOrder } } };
};

const putEntry = (
  cache: HistoryCacheState,
  conversationId: string,
  messages: ChatMessageVO[],
  hasMore: boolean,
  nextBeforeSequence?: number,
): HistoryCacheState => {
  const accessOrder = cache.accessOrder + 1;
  const entries = {
    ...cache.entries,
    [conversationId]: {
      messages: normalizeMessages(messages),
      hasMore,
      nextBeforeSequence,
      lastAccessOrder: accessOrder,
    },
  };
  const evictionId = Object.keys(entries).length > MAX_HISTORY_CACHE_CONVERSATIONS
    ? Object.entries(entries).filter(([id]) => id !== conversationId)
      .sort(([, left], [, right]) => left.lastAccessOrder - right.lastAccessOrder)[0]?.[0]
    : undefined;
  if (evictionId) delete entries[evictionId];
  return { entries, accessOrder };
};

const normalizeMessages = (messages: ChatMessageVO[]): ChatMessageVO[] => {
  const byId = new Map(messages.map((message) => [message.messageId, message]));
  return [...byId.values()].sort((left, right) => left.sequence - right.sequence);
};
