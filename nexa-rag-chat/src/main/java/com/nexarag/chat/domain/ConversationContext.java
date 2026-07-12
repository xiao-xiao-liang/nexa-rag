package com.nexarag.chat.domain;

import java.util.List;

/**
 * 模型调用前使用的活跃会话上下文快照。
 *
 * @param conversationId 会话 ID
 * @param userId 用户 ID
 * @param summary 最新摘要
 * @param summaryLastMessageId 摘要覆盖的最后消息 ID
 * @param recentMessages 最近消息
 * @param lastMessageId 快照中的最后消息 ID
 * @param version 快照版本
 */
public record ConversationContext(String conversationId, String userId, String summary,
                                  String summaryLastMessageId, List<ChatMessageVO> recentMessages,
                                  String lastMessageId, long version) {

    /**
     * 创建上下文并复制消息列表，避免调用方修改快照内容。
     */
    public ConversationContext {
        recentMessages = recentMessages == null ? List.of() : List.copyOf(recentMessages);
    }
}
