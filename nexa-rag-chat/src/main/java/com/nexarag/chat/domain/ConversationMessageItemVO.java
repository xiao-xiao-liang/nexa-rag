package com.nexarag.chat.domain;

import com.nexarag.chat.enums.ChatMessageRole;
import com.nexarag.chat.enums.ChatMessageStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

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

    /** 生成任务 ID。 */
    private String generationId;

    /** 工具运行卡终态快照 JSON。 */
    private String toolOperationsJson;

    /** 消息内引用公开摘要。 */
    private List<ChatCitationSummaryVO> citations;

    /** 创建时间。 */
    private LocalDateTime createdTime;

    /** 更新时间。 */
    private LocalDateTime updatedTime;
}
