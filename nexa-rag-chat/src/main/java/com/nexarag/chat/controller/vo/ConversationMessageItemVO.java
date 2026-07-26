package com.nexarag.chat.controller.vo;

import com.nexarag.chat.enums.ChatMessageRole;
import com.nexarag.chat.enums.ChatMessageStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 面向历史消息的安全消息投影。
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMessageItemVO {

    /** 消息 ID。 */
    private String messageId;

    /** 会话内消息序号。 */
    private long sequence;

    /** 消息角色。 */
    private ChatMessageRole role;

    /** 消息状态。 */
    private ChatMessageStatus status;

    /** 消息正文。 */
    private String content;

    /** 创建时间。 */
    private LocalDateTime createdTime;

    /** 更新时间。 */
    private LocalDateTime updatedTime;
}
