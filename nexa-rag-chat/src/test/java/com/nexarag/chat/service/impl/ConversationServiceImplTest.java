package com.nexarag.chat.service.impl;

import com.nexarag.chat.domain.ChatConversationVO;
import com.nexarag.chat.cache.ConversationContextCache;
import com.nexarag.chat.cache.ConversationContextLock;
import com.nexarag.chat.entity.ChatConversation;
import com.nexarag.chat.mapper.ChatMessageMapper;
import com.nexarag.chat.id.ChatIdGenerator;
import com.nexarag.chat.mapper.ChatConversationMapper;
import com.nexarag.common.exception.ClientException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 会话生命周期服务测试。
 */
@ExtendWith(MockitoExtension.class)
class ConversationServiceImplTest {

    @Mock
    private ChatConversationMapper mapper;
    @Mock
    private ChatIdGenerator chatIdGenerator;
    @Mock
    private ChatMessageMapper chatMessageMapper;
    @Mock
    private ConversationContextLock contextLock;
    @Mock
    private ConversationContextCache contextCache;
    @Spy
    @InjectMocks
    private ConversationServiceImpl conversationService;

    @BeforeEach
    void injectBaseMapper() {
        ReflectionTestUtils.setField(conversationService, "baseMapper", mapper);
    }

    @Test
    void shouldCreateActiveConversationWithDefaultTitle() {
        when(chatIdGenerator.nextId()).thenReturn("c1");
        when(mapper.insert(any(ChatConversation.class))).thenReturn(1);

        ChatConversationVO result = conversationService.create("u1", null);

        assertThat(result.getConversationId()).isEqualTo("c1");
        assertThat(result.getTitle()).isEqualTo("新会话");
        assertThat(result.getStatus()).isEqualTo(com.nexarag.chat.enums.ConversationStatus.ACTIVE);
        verify(mapper).insert(any(ChatConversation.class));
    }

    @Test
    void shouldLogicallyDeleteConversationMessagesAndEvictContext() {
        ChatConversation conversation = ChatConversation.builder()
                .conversationId("c1")
                .userId("u1")
                .status("ACTIVE")
                .version(0)
                .build();
        when(chatMessageMapper.selectCount(any())).thenReturn(0L);
        when(chatMessageMapper.delete(any())).thenReturn(2);
        doReturn(conversation).when(conversationService).getOwnedConversation("c1", "u1");
        doReturn(true).when(conversationService).updateById(conversation);
        doReturn(true).when(conversationService).removeById("c1");
        doAnswer(invocation -> {
            invocation.getArgument(2, Runnable.class).run();
            return null;
        }).when(contextLock).execute(anyString(), anyString(), any(Runnable.class));

        conversationService.delete("c1", "u1");

        assertThat(conversation.getStatus()).isEqualTo("DELETED");
        verify(conversationService).removeById("c1");
        verify(chatMessageMapper).delete(any());
        verify(contextCache).evict("u1", "c1");
    }

    @Test
    void shouldRejectDeletionWhenAssistantMessageIsGenerating() {
        ChatConversation conversation = ChatConversation.builder()
                .conversationId("c1")
                .userId("u1")
                .status("ACTIVE")
                .build();
        when(chatMessageMapper.selectCount(any())).thenReturn(1L);
        doReturn(conversation).when(conversationService).getOwnedConversation("c1", "u1");
        doAnswer(invocation -> {
            invocation.getArgument(2, Runnable.class).run();
            return null;
        }).when(contextLock).execute(anyString(), anyString(), any(Runnable.class));

        assertThatThrownBy(() -> conversationService.delete("c1", "u1"))
                .isInstanceOf(ClientException.class)
                .hasMessage("当前会话正在生成回答，暂不允许删除");

        verifyNoInteractions(contextCache);
        verify(chatMessageMapper).selectCount(any());
    }
}
