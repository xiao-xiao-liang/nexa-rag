package com.nexarag.chat.service.impl;

import com.nexarag.chat.domain.ChatConversationVO;
import com.nexarag.chat.entity.ChatConversation;
import com.nexarag.chat.id.ChatIdGenerator;
import com.nexarag.chat.mapper.ChatConversationMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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
}
