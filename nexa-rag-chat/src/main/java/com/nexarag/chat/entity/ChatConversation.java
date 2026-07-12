package com.nexarag.chat.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 聊天会话数据库实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("chat_conversation")
public class ChatConversation {

    @TableId(value = "conversation_id", type = IdType.INPUT)
    /**
     * 会话 ID。
     * */
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
    private String status;

    /**
     * 最近消息 ID。
     */
    private String lastMessageId;

    /**
     * 最近消息时间。
     */
    private LocalDateTime lastMessageTime;

    /**
     * 数据版本号。
     */
    @Version
    private Integer version;

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
