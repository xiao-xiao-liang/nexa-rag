package com.nexarag.chat.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会话摘要数据库实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("chat_conversation_summary")
public class ChatConversationSummary {

    @TableId(value = "summary_id", type = IdType.INPUT)
    /** 摘要 ID。 */
    private String summaryId;

    /**
     * 会话 ID。
     */
    private String conversationId;

    /**
     * 用户 ID。
     */
    private String userId;

    /**
     * 摘要内容。
     */
    private String content;

    /**
     * 摘要覆盖的最后消息 ID。
     */
    private String lastMessageId;

    /**
     * 摘要版本号。
     */
    private Long summaryVersion;

    /**
     * 创建时间。
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间。
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标记。
     */
    @TableLogic
    private Integer delFlag;
}
