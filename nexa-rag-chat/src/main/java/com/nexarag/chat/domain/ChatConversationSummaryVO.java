package com.nexarag.chat.domain;

import java.time.LocalDateTime;

/**
 * 会话阶段性摘要领域对象。
 *
 * @param summaryId 摘要 ID
 * @param conversationId 会话 ID
 * @param userId 用户 ID
 * @param content 摘要内容
 * @param lastMessageId 摘要覆盖的最后消息 ID
 * @param summaryVersion 摘要版本
 * @param createdTime 创建时间
 * @param updatedTime 更新时间
 */
public record ChatConversationSummaryVO(String summaryId, String conversationId, String userId,
                                        String content, String lastMessageId, long summaryVersion,
                                        LocalDateTime createdTime, LocalDateTime updatedTime) {
}
