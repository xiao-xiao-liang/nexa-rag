package com.nexarag.chat.service.impl;

import com.nexarag.chat.cache.ConversationContextCache;
import com.nexarag.chat.domain.ChatConversationVO;
import com.nexarag.chat.domain.ConversationContext;
import com.nexarag.chat.enums.ConversationStatus;
import com.nexarag.chat.cache.ConversationContextLock;
import com.nexarag.chat.service.ConversationMessageService;
import com.nexarag.chat.service.ConversationService;
import com.nexarag.chat.service.ConversationSummaryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会话上下文服务缓存优先行为测试。
 */
@ExtendWith(MockitoExtension.class)
class ConversationContextServiceImplTest {

    @Mock
    private ConversationService conversationService;
    @Mock
    private ConversationMessageService messageService;
    @Mock
    private ConversationSummaryService summaryService;
    @Mock
    private ConversationContextCache contextCache;
    @Mock
    private ConversationContextLock contextLock;
    @InjectMocks
    private ConversationContextServiceImpl contextService;

    @Test
    void shouldReturnCachedContextWithoutReadingDatabaseMessages() {
        ChatConversationVO conversation = new ChatConversationVO(
                "c1", "u1", "新会话", ConversationStatus.ACTIVE, null, null, 0, null, null);
        ConversationContext cached = new ConversationContext("c1", "u1", "摘要", "m0",
                List.of(), "m1", 1L);
        when(conversationService.getOwned("c1", "u1")).thenReturn(conversation);
        when(contextCache.get("u1", "c1")).thenReturn(Optional.of(cached));

        ConversationContext result = contextService.loadForTurn("c1", "u1");

        assertThat(result).isEqualTo(cached);
        verify(messageService, never()).listHistory("c1", "u1", 16);
    }
}
