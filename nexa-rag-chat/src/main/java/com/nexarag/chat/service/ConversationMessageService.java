package com.nexarag.chat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nexarag.chat.domain.ChatMessageVO;
import com.nexarag.chat.entity.ChatMessage;

import java.util.List;

/**
 * 管理聊天消息生命周期。
 */
public interface ConversationMessageService extends IService<ChatMessage> {

    /** 保存已完成的用户消息。 */
    ChatMessageVO appendUserMessage(String conversationId, String userId, String content);

    /** 创建生成中的助手消息占位记录。 */
    ChatMessageVO startAssistantMessage(String conversationId, String userId);

    /** 更新助手消息为完成状态。 */
    void completeAssistantMessage(String messageId, String content, String thinkingContent,
                                  Integer promptTokens, Integer completionTokens, Integer totalTokens,
                                  String referencesJson);

    /** 更新助手消息为失败状态，并保存已生成的部分回答。 */
    void failAssistantMessage(String messageId, String partialContent,
                              String failureCode, String failureMessage);

    /** 更新助手消息为取消状态，并保存已生成的部分回答。 */
    void cancelAssistantMessage(String messageId, String partialContent);

    /** 分页限制查询会话历史消息。 */
    List<ChatMessageVO> listHistory(String conversationId, String userId, int limit);
}
