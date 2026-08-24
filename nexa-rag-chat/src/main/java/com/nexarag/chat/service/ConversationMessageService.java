package com.nexarag.chat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nexarag.chat.domain.ChatGenerationTurnBO;
import com.nexarag.chat.domain.ChatMessageVO;
import com.nexarag.chat.entity.ChatMessage;
import com.nexarag.common.web.CursorPageVO;

import java.util.List;

/**
 * 管理聊天消息生命周期。
 */
public interface ConversationMessageService extends IService<ChatMessage> {

    /** 保存已完成的用户消息。 */
    ChatMessageVO appendUserMessage(String conversationId, String userId, String content);

    /** 创建生成中的助手消息占位记录。 */
    ChatMessageVO startAssistantMessage(String conversationId, String userId);

    /**
     * 原子创建一轮生成所需的用户消息和助手占位消息。
     *
     * @param conversationId 会话 ID
     * @param userId 用户 ID
     * @param userContent 用户问题
     * @param generationId 生成任务 ID
     * @return 本轮创建的两条消息
     */
    ChatGenerationTurnBO beginGenerationTurn(String conversationId, String userId,
                                             String userContent, String generationId);

    /** 生成期间持久化引用清单，确保异常收口时仍可恢复引用。 */
    void updateGeneratingAssistantReferences(String messageId, String referencesJson);

    /** 更新助手消息为完成状态。 */
    void completeAssistantMessage(String messageId, String content, String thinkingContent,
                                  Integer promptTokens, Integer completionTokens, Integer totalTokens,
                                  String referencesJson);

    /** 更新助手消息为完成状态，并保存工具运行卡终态快照。 */
    void completeAssistantMessage(String messageId, String content, String thinkingContent,
                                  Integer promptTokens, Integer completionTokens, Integer totalTokens,
                                  String referencesJson, String toolOperationsJson);

    /** 更新助手消息为失败状态，并保存已生成的部分回答。 */
    void failAssistantMessage(String messageId, String partialContent,
                              String failureCode, String failureMessage);

    /** 更新助手消息为失败状态，并保存工具运行卡终态快照。 */
    void failAssistantMessage(String messageId, String partialContent,
                              String failureCode, String failureMessage, String toolOperationsJson);

    /** 更新助手消息为失败状态，并保存引用与工具运行卡终态快照。 */
    void failAssistantMessage(String messageId, String partialContent,
                              String failureCode, String failureMessage, String referencesJson,
                              String toolOperationsJson);

    /** 更新助手消息为取消状态，并保存已生成的部分回答。 */
    void cancelAssistantMessage(String messageId, String partialContent);

    /** 更新助手消息为取消状态，并保存工具运行卡终态快照。 */
    void cancelAssistantMessage(String messageId, String partialContent, String toolOperationsJson);

    /** 更新助手消息为取消状态，并保存引用与工具运行卡终态快照。 */
    void cancelAssistantMessage(String messageId, String partialContent, String referencesJson,
                                String toolOperationsJson);

    /** 分页限制查询会话历史消息。 */
    List<ChatMessageVO> listHistory(String conversationId, String userId, int limit);

    /**
     * 按消息序号向前分页查询会话历史。
     *
     * @param conversationId 会话 ID
     * @param userId 当前用户 ID
     * @param beforeSequence 仅查询小于该序号的消息；为空时查询最新消息
     * @param size 本次查询数量
     * @return 升序排列的历史消息游标页
     */
    CursorPageVO<ChatMessageVO> pageHistory(String conversationId, String userId,
                                            Long beforeSequence, int size);

    /** 统计摘要边界之后已完成的用户消息数量。 */
    long countCompletedUserMessagesAfterSequence(String conversationId, String userId, long afterSequence);

    /** 查询摘要边界之后可用于上下文的已完成消息。 */
    List<ChatMessageVO> listContextMessagesAfterSequence(String conversationId, String userId,
                                                          long afterSequence, int limit);
}
