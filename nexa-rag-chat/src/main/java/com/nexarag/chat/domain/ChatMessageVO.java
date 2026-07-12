package com.nexarag.chat.domain;

import com.nexarag.chat.enums.ChatMessageRole;
import com.nexarag.chat.enums.ChatMessageStatus;

import java.time.LocalDateTime;

/**
 * 完整聊天消息领域对象。
 *
 * @param messageId 消息 ID
 * @param conversationId 会话 ID
 * @param userId 用户 ID
 * @param sequence 会话内消息序号
 * @param role 消息角色
 * @param status 消息状态
 * @param content 消息正文
 * @param thinkingContent 思考内容
 * @param referencesJson 引用信息 JSON
 * @param promptTokens 输入 Token 数
 * @param completionTokens 输出 Token 数
 * @param totalTokens 总 Token 数
 * @param failureCode 失败编码
 * @param failureMessage 失败信息
 * @param createdTime 创建时间
 * @param updatedTime 更新时间
 */
public record ChatMessageVO(String messageId, String conversationId, String userId, long sequence,
                            ChatMessageRole role, ChatMessageStatus status, String content,
                            String thinkingContent, String referencesJson, Integer promptTokens,
                            Integer completionTokens, Integer totalTokens, String failureCode,
                            String failureMessage, LocalDateTime createdTime, LocalDateTime updatedTime) {

    /**
     * 判断消息是否可以进入模型上下文。
     *
     * @return 用户完成消息或助手完成消息返回 true
     */
    public boolean usableForContext() {
        return status == ChatMessageStatus.COMPLETED
                && (role == ChatMessageRole.USER || role == ChatMessageRole.ASSISTANT)
                && content != null
                && !content.isBlank();
    }
}
