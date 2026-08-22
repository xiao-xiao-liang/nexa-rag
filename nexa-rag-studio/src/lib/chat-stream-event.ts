import type { ChatCitationSummaryVO, ChatMessageVO, ChatStreamEvent } from "../types";

/** 仅用于 Markdown 渲染阶段识别引用按钮的受控链接前缀。 */
export const CITATION_LINK_PREFIX = "https://citation.local/";

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

/**
 * 将 SSE 中不可变的引用摘要写入对应助手消息。
 *
 * @param messages 当前消息列表
 * @param messageId 目标助手消息 ID
 * @param citations 后端下发的引用编号
 * @return 更新后的消息列表
 */
export function applyCitationSnapshot(messages: ChatMessageVO[], messageId: string,
                                      citations: ChatCitationSummaryVO[] | undefined): ChatMessageVO[] {
  if (!citations) {
    return messages;
  }
  return messages.map((message) => message.messageId === messageId
    ? { ...message, citations }
    : message);
}

/** 将模型按约定输出的 [n] 标记转换为受控的引用链接。 */
export function linkifyCitationMarkers(content: string, citationIds: Set<number>): string {
  return content.replace(/\[([1-9]\d*)\]/g, (match, rawId: string) => {
    const citationId = Number(rawId);
    return citationIds.has(citationId)
      ? `[${citationId}](${CITATION_LINK_PREFIX}${citationId})`
      : match;
  });
}
