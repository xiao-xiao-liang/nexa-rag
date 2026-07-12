package com.nexarag.chat.service;

import com.nexarag.chat.domain.ConversationContext;

/**
 * 构建和维护模型调用使用的会话上下文。
 */
public interface ConversationContextService {

    ConversationContext loadForTurn(String conversationId, String userId);

    ConversationContext rebuild(String conversationId, String userId);

    void evict(String conversationId, String userId);
}
