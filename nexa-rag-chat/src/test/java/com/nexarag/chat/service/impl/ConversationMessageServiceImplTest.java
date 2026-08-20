package com.nexarag.chat.service.impl;

import com.nexarag.chat.cache.ConversationContextLock;
import com.nexarag.chat.domain.ChatMessageVO;
import com.nexarag.chat.entity.ChatConversation;
import com.nexarag.chat.entity.ChatMessage;
import com.nexarag.chat.enums.ChatMessageRole;
import com.nexarag.chat.enums.ChatMessageStatus;
import com.nexarag.chat.id.ChatIdGenerator;
import com.nexarag.chat.mapper.ChatMessageMapper;
import com.nexarag.chat.service.ConversationMessageService;
import com.nexarag.chat.service.ConversationService;
import com.nexarag.common.web.CursorPageVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会话消息生命周期服务测试。
 */
@ExtendWith(MockitoExtension.class)
class ConversationMessageServiceImplTest {

    @Mock
    private ChatMessageMapper mapper;
    @Mock
    private ConversationService conversationService;
    @Mock
    private ChatIdGenerator chatIdGenerator;
    @Mock
    private ConversationContextLock contextLock;
    @InjectMocks
    private ConversationMessageServiceImpl messageService;

    @BeforeEach
    void injectBaseMapper() {
        ReflectionTestUtils.setField(messageService, "baseMapper", mapper);
    }

    @Test
    void shouldAppendCompletedUserMessageWithNextSequence() {
        ChatConversation conversation = new ChatConversation();
        conversation.setConversationId("c1");
        conversation.setUserId("u1");
        conversation.setStatus("ACTIVE");
        conversation.setVersion(0);
        when(conversationService.getOwnedConversation("c1", "u1")).thenReturn(conversation);
        lenient().when(mapper.selectList(any())).thenReturn(List.of());
        lenient().when(conversationService.updateById(any(ChatConversation.class))).thenReturn(true);
        when(chatIdGenerator.nextId()).thenReturn("m1");
        doAnswer(invocation -> invocation.getArgument(2, java.util.function.Supplier.class).get())
                .when(contextLock).execute(any(), any(), any(java.util.function.Supplier.class));

        var result = messageService.appendUserMessage("c1", "u1", "你好");

        assertThat(result.messageId()).isEqualTo("m1");
        assertThat(result.sequence()).isEqualTo(1L);
        assertThat(result.content()).isEqualTo("你好");
        verify(mapper).insert(any(ChatMessage.class));
        verify(conversationService).updateById(any(ChatConversation.class));
    }

    @Test
    void shouldSavePartialContentWhenAssistantMessageFails() {
        when(mapper.update(any(ChatMessage.class), any())).thenReturn(1);

        messageService.failAssistantMessage("m1", "部分回答", "MODEL_UNAVAILABLE", "模型不可用");

        verify(mapper).update(argThat(message ->
                        "FAILED".equals(message.getStatus())
                                && "部分回答".equals(message.getContent())
                                && "MODEL_UNAVAILABLE".equals(message.getFailureCode())
                                && "模型不可用".equals(message.getFailureMessage())),
                any());
    }

    @Test
    void shouldSavePartialContentWhenAssistantMessageIsCancelled() {
        when(mapper.update(any(ChatMessage.class), any())).thenReturn(1);

        messageService.cancelAssistantMessage("m1", "已生成内容");

        verify(mapper).update(argThat(message ->
                        "CANCELLED".equals(message.getStatus())
                                && "已生成内容".equals(message.getContent())),
                any());
    }

    @Test
    void shouldExposeGenerationAndToolSnapshotFieldsForAssistantMessages() {
        assertThat(Stream.of(ChatMessage.class.getDeclaredFields()).map(field -> field.getName()))
                .contains("generationId", "toolOperationsJson");
        assertThat(Stream.of(ConversationMessageService.class.getDeclaredMethods())
                .map(method -> method.getName()))
                .contains("beginGenerationTurn");
    }

    @Test
    void shouldReturnOlderMessagesInAscendingSequenceWithCursor() {
        when(conversationService.getOwnedConversation("c1", "u1")).thenReturn(conversation("c1", "u1"));
        when(mapper.selectList(any())).thenReturn(List.of(message("m4", 4L), message("m3", 3L), message("m2", 2L)));

        CursorPageVO<ChatMessageVO> result = messageService.pageHistory("c1", "u1", 5L, 2);

        assertThat(result.getRecords()).extracting(ChatMessageVO::sequence).containsExactly(3L, 4L);
        assertThat(result.isHasMore()).isTrue();
        assertThat(result.getNextBeforeSequence()).isEqualTo(3L);
    }

    private ChatConversation conversation(String conversationId, String userId) {
        ChatConversation conversation = new ChatConversation();
        conversation.setConversationId(conversationId);
        conversation.setUserId(userId);
        conversation.setStatus("ACTIVE");
        return conversation;
    }

    private ChatMessage message(String messageId, long sequence) {
        ChatMessage message = new ChatMessage();
        message.setMessageId(messageId);
        message.setConversationId("c1");
        message.setUserId("u1");
        message.setSequence(sequence);
        message.setRole(ChatMessageRole.ASSISTANT.name());
        message.setStatus(ChatMessageStatus.COMPLETED.name());
        message.setContent("回答");
        return message;
    }
}
