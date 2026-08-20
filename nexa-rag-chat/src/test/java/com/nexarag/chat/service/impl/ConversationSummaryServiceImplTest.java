package com.nexarag.chat.service.impl;

import com.nexarag.chat.cache.ConversationContextLock;
import com.nexarag.chat.domain.ChatConversationVO;
import com.nexarag.chat.domain.ChatMessageVO;
import com.nexarag.chat.entity.ChatConversationSummary;
import com.nexarag.chat.enums.ChatMessageRole;
import com.nexarag.chat.enums.ChatMessageStatus;
import com.nexarag.chat.enums.ConversationStatus;
import com.nexarag.chat.id.ChatIdGenerator;
import com.nexarag.chat.mapper.ChatConversationSummaryMapper;
import com.nexarag.chat.service.ConversationMessageService;
import com.nexarag.chat.service.ConversationService;
import com.nexarag.model.gateway.ModelGateway;
import com.nexarag.model.gateway.chat.ChatModelResponse;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会话摘要服务增量生成测试。
 */
@ExtendWith(MockitoExtension.class)
class ConversationSummaryServiceImplTest {

    @Mock
    private ChatConversationSummaryMapper mapper;
    @Mock
    private ConversationService conversationService;
    @Mock
    private ConversationMessageService messageService;
    @Mock
    private ModelGateway modelGateway;
    @Mock
    private ChatIdGenerator chatIdGenerator;
    @Mock
    private ConversationContextLock contextLock;
    @Mock
    private LambdaQueryChainWrapper<ChatConversationSummary> summaryQuery;
    @Spy
    @InjectMocks
    private ConversationSummaryServiceImpl summaryService;

    @BeforeEach
    void injectBaseMapper() {
        ReflectionTestUtils.setField(summaryService, "baseMapper", mapper);
        doReturn(summaryQuery).when(summaryService).lambdaQuery();
        when(summaryQuery.eq(any(), any())).thenReturn(summaryQuery);
        when(summaryQuery.orderByDesc((SFunction<ChatConversationSummary, ?>) any(SFunction.class)))
                .thenReturn(summaryQuery);
        when(summaryQuery.last("LIMIT 1")).thenReturn(summaryQuery);
        when(summaryQuery.oneOpt()).thenReturn(Optional.empty());
    }

    @Test
    void shouldGenerateNewSummaryAfterNineUserTurns() {
        doAnswer(invocation -> invocation.getArgument(2, java.util.function.Supplier.class).get())
                .when(contextLock).execute(any(), any(), any(java.util.function.Supplier.class));
        when(conversationService.getOwned("c1", "u1"))
                .thenReturn(new ChatConversationVO("c1", "u1", "测试会话", ConversationStatus.ACTIVE,
                        null, null, 0, LocalDateTime.now(), LocalDateTime.now()));
        List<ChatMessageVO> messages = new ArrayList<>();
        for (int i = 1; i <= 18; i++) {
            messages.add(new ChatMessageVO("m" + i, "c1", "u1", i,
                    i % 2 == 1 ? ChatMessageRole.USER : ChatMessageRole.ASSISTANT,
                    ChatMessageStatus.COMPLETED, "消息" + i, null, null,
                    null, null, null, null, null, null, null, LocalDateTime.now(), LocalDateTime.now()));
        }
        when(messageService.listHistory("c1", "u1", 1000)).thenReturn(messages);
        when(chatIdGenerator.nextId()).thenReturn("s1");
        when(modelGateway.chat(any())).thenReturn(ChatModelResponse.builder().content("摘要内容").build());
        when(mapper.insert(any(ChatConversationSummary.class))).thenReturn(1);

        var result = summaryService.generate("c1", "u1");

        verify(modelGateway).chat(any());
        assertThat(result.content()).isEqualTo("摘要内容");
        assertThat(result.lastMessageId()).isEqualTo("m18");
        verify(mapper).insert(any(ChatConversationSummary.class));
    }
}
