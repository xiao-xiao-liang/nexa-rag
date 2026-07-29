package com.nexarag.chat.service.impl;

import com.nexarag.chat.cache.ConversationContextCache;
import com.nexarag.chat.cache.ConversationContextLock;
import com.nexarag.chat.domain.ChatConversationSummaryVO;
import com.nexarag.chat.domain.ChatMessageVO;
import com.nexarag.chat.domain.ConversationContext;
import com.nexarag.chat.service.ConversationContextService;
import com.nexarag.chat.service.ConversationMessageService;
import com.nexarag.chat.service.ConversationService;
import com.nexarag.chat.service.ConversationSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * 活跃会话上下文服务，优先读取 Redis，未命中时从 MySQL 重建。
 */
@Service
@RequiredArgsConstructor
public class ConversationContextServiceImpl implements ConversationContextService {

    private static final int HISTORY_KEEP_TURNS = 8;
    private static final int HISTORY_MESSAGE_LIMIT = HISTORY_KEEP_TURNS * 2;

    private final ConversationService conversationService;
    private final ConversationMessageService messageService;
    private final ConversationSummaryService summaryService;
    private final ConversationContextCache contextCache;
    private final ConversationContextLock contextLock;

    @Override
    @Transactional(readOnly = true)
    public ConversationContext loadForTurn(String conversationId, String userId) {
        conversationService.getOwned(conversationId, userId);
        return contextCache.get(userId, conversationId)
                .orElseGet(() -> contextLock.execute(userId, conversationId,
                        () -> contextCache.get(userId, conversationId)
                                .orElseGet(() -> rebuildUnlocked(conversationId, userId))));
    }

    @Override
    @Transactional(readOnly = true)
    public ConversationContext rebuild(String conversationId, String userId) {
        conversationService.getOwned(conversationId, userId);
        return contextLock.execute(userId, conversationId, () -> rebuildUnlocked(conversationId, userId));
    }

    @Override
    public void evict(String conversationId, String userId) {
        conversationService.getOwned(conversationId, userId);
        contextCache.evict(userId, conversationId);
    }

    private ConversationContext rebuildUnlocked(String conversationId, String userId) {
        // 1. 查询最新摘要和最近已完成消息
        ChatConversationSummaryVO summary = summaryService.getLatest(conversationId, userId);
        List<ChatMessageVO> messages = messageService.listHistory(
                        conversationId, userId, HISTORY_MESSAGE_LIMIT)
                .stream()
                .filter(ChatMessageVO::usableForContext)
                .sorted(Comparator.comparingLong(ChatMessageVO::sequence))
                .toList();

        // 2. 组装上下文快照并计算版本
        String lastMessageId = messages.isEmpty() ? null : messages.getLast().messageId();
        long version = Math.max(summary == null ? 0L : summary.summaryVersion(),
                messages.isEmpty() ? 0L : messages.getLast().sequence());
        ConversationContext context = new ConversationContext(
                conversationId, userId, summary == null ? null : summary.content(),
                summary == null ? null : summary.lastMessageId(), messages, lastMessageId, version);

        // 3. 写入 Redis，供下一轮模型调用直接读取
        contextCache.put(context);
        return context;
    }
}
