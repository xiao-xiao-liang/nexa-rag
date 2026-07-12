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
 * 聊天消息数据库实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("chat_message")
public class ChatMessage {

    @TableId(value = "message_id", type = IdType.INPUT)
    /** 消息 ID。 */
    private String messageId;

    /**
     * 会话 ID。
     */
    private String conversationId;

    /**
     * 用户 ID。
     */
    private String userId;

    /**
     * 会话内消息序号。
     */
    private Long sequence;

    /**
     * 消息角色。
     */
    private String role;

    /**
     * 消息状态。
     */
    private String status;

    /**
     * 消息正文。
     */
    private String content;

    /**
     * 思考内容。
     */
    private String thinkingContent;

    /**
     * 引用信息 JSON。
     */
    private String referencesJson;

    /**
     * 输入 Token 数。
     */
    private Integer promptTokens;

    /**
     * 输出 Token 数。
     */
    private Integer completionTokens;

    /**
     * 总 Token 数。
     */
    private Integer totalTokens;

    /**
     * 失败编码。
     */
    private String failureCode;

    /**
     * 失败信息。
     */
    private String failureMessage;

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
