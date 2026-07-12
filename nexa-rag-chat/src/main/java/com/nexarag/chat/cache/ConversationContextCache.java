package com.nexarag.chat.cache;

import com.nexarag.chat.domain.ConversationContext;

import java.util.Optional;

/**
 * 活跃会话上下文缓存抽象。
 */
public interface ConversationContextCache {

    Optional<ConversationContext> get(String userId, String conversationId);

    void put(ConversationContext context);

    void evict(String userId, String conversationId);

    void refreshTtl(String userId, String conversationId);
}
