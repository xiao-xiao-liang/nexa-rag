package com.nexarag.chat.controller.vo;

import com.nexarag.chat.enums.ConversationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 面向会话列表的安全会话投影。
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationListItemVO {

    /** 会话 ID。 */
    private String conversationId;

    /** 会话标题。 */
    private String title;

    /** 会话状态。 */
    private ConversationStatus status;

    /** 最近消息时间。 */
    private LocalDateTime lastMessageTime;

    /** 创建时间。 */
    private LocalDateTime createdTime;

    /** 更新时间。 */
    private LocalDateTime updatedTime;
}
