package com.nexarag.chat.domain;

import com.nexarag.chat.enums.ConversationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户可见的聊天会话视图对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatConversationVO {

    /**
     * 会话 ID。
     */
    private String conversationId;

    /**
     * 用户 ID。
     */
    private String userId;

    /**
     * 会话标题。
     */
    private String title;

    /**
     * 会话状态。
     */
    private ConversationStatus status;

    /**
     * 最近一条消息 ID。
     */
    private String lastMessageId;

    /**
     * 最近一条消息时间。
     */
    private LocalDateTime lastMessageTime;

    /**
     * 乐观锁版本号。
     */
    private int version;

    /**
     * 创建时间。
     */
    private LocalDateTime createdTime;

    /**
     * 更新时间。
     */
    private LocalDateTime updatedTime;
}
