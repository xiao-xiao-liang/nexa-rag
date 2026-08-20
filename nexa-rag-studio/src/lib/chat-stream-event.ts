import type { ChatStreamEvent } from "../types";

/**
 * 统一规范化 SSE 事件中由 JSON 传输带来的版本字段类型。
 * 后端当前会将 Long 序列化为字符串，前端去重必须始终使用数值比较。
 */
export function normalizeChatStreamEvent(event: ChatStreamEvent): ChatStreamEvent {
  const eventVersion = Number(event.eventVersion);
  return {
    ...event,
    eventVersion: Number.isFinite(eventVersion) && eventVersion > 0
      ? eventVersion
      : undefined,
  };
}
