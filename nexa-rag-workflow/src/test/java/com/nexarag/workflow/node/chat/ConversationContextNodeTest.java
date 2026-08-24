package com.nexarag.workflow.node.chat;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.nexarag.chat.domain.ChatGenerationTurnBO;
import com.nexarag.chat.domain.ChatMessageVO;
import com.nexarag.chat.domain.ConversationContext;
import com.nexarag.chat.enums.ChatMessageRole;
import com.nexarag.chat.enums.ChatMessageStatus;
import com.nexarag.chat.service.ConversationContextService;
import com.nexarag.chat.service.ConversationMessageService;
import com.nexarag.workflow.stream.ChatGenerationAccumulator;
import com.nexarag.workflow.stream.ChatGenerationEventPublisher;
import com.nexarag.workflow.stream.ChatGenerationTaskManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.CONVERSATION_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.GENERATION_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.TRACE_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.USER_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.USER_QUESTION;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会话上下文节点测试，验证异常收口时保留已生成的引用。
 */
class ConversationContextNodeTest {

    @Test
    void failureFinalizerShouldPersistCapturedReferences() {
        ConversationContextService contextService = mock(ConversationContextService.class);
        ConversationMessageService messageService = mock(ConversationMessageService.class);
        ChatGenerationTaskManager taskManager = mock(ChatGenerationTaskManager.class);
        ChatGenerationEventPublisher eventPublisher = mock(ChatGenerationEventPublisher.class);
        when(contextService.loadForTurn("c1", "u1"))
                .thenReturn(new ConversationContext("c1", "u1", "", "", null, List.of(), "", 1L));
        when(messageService.beginGenerationTurn("c1", "u1", "问题", "g1"))
                .thenReturn(new ChatGenerationTurnBO(message("u1", ChatMessageRole.USER, ChatMessageStatus.COMPLETED),
                        message("m1", ChatMessageRole.ASSISTANT, ChatMessageStatus.GENERATING)));
        ConversationContextNode node = new ConversationContextNode(contextService, messageService, taskManager,
                eventPublisher);

        node.apply(new OverAllState(Map.of(CONVERSATION_ID, "c1", USER_ID, "u1", USER_QUESTION, "问题",
                GENERATION_ID, "g1", TRACE_ID, "trace-001")));

        ArgumentCaptor<ChatGenerationAccumulator> accumulatorCaptor = ArgumentCaptor.forClass(ChatGenerationAccumulator.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<BiConsumer<String, String>> failureCaptor = ArgumentCaptor.forClass(BiConsumer.class);
        verify(taskManager).register(eq("g1"), eq("u1"), eq("c1"), accumulatorCaptor.capture(), any(Runnable.class),
                failureCaptor.capture());
        recordReferences(accumulatorCaptor.getValue(), "{\"version\":1,\"citations\":[{\"citationId\":1}]}");

        failureCaptor.getValue().accept("MODEL_STREAM_ERROR", "模型流中断");

        verify(messageService).failAssistantMessage(eq("m1"), eq(""), eq("MODEL_STREAM_ERROR"), eq("模型流中断"),
                eq("{\"version\":1,\"citations\":[{\"citationId\":1}]}"), any());
    }

    @Test
    void cancelFinalizerShouldPersistCapturedReferences() {
        ConversationContextService contextService = mock(ConversationContextService.class);
        ConversationMessageService messageService = mock(ConversationMessageService.class);
        ChatGenerationTaskManager taskManager = mock(ChatGenerationTaskManager.class);
        ChatGenerationEventPublisher eventPublisher = mock(ChatGenerationEventPublisher.class);
        when(contextService.loadForTurn("c1", "u1"))
                .thenReturn(new ConversationContext("c1", "u1", "", "", null, List.of(), "", 1L));
        when(messageService.beginGenerationTurn("c1", "u1", "问题", "g1"))
                .thenReturn(new ChatGenerationTurnBO(message("u1", ChatMessageRole.USER, ChatMessageStatus.COMPLETED),
                        message("m1", ChatMessageRole.ASSISTANT, ChatMessageStatus.GENERATING)));
        ConversationContextNode node = new ConversationContextNode(contextService, messageService, taskManager,
                eventPublisher);

        node.apply(new OverAllState(Map.of(CONVERSATION_ID, "c1", USER_ID, "u1", USER_QUESTION, "问题",
                GENERATION_ID, "g1", TRACE_ID, "trace-001")));

        ArgumentCaptor<ChatGenerationAccumulator> accumulatorCaptor = ArgumentCaptor.forClass(ChatGenerationAccumulator.class);
        ArgumentCaptor<Runnable> cancelCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(taskManager).register(eq("g1"), eq("u1"), eq("c1"), accumulatorCaptor.capture(), cancelCaptor.capture(),
                any());
        recordReferences(accumulatorCaptor.getValue(), "{\"version\":1,\"citations\":[{\"citationId\":1}]}");

        cancelCaptor.getValue().run();

        verify(messageService).cancelAssistantMessage(eq("m1"), eq(""),
                eq("{\"version\":1,\"citations\":[{\"citationId\":1}]}"), any());
    }

    private void recordReferences(ChatGenerationAccumulator accumulator, String referencesJson) {
        assertThatCode(() -> ChatGenerationAccumulator.class
                .getMethod("recordReferencesJson", String.class)
                .invoke(accumulator, referencesJson))
                .doesNotThrowAnyException();
    }

    private ChatMessageVO message(String messageId, ChatMessageRole role, ChatMessageStatus status) {
        LocalDateTime now = LocalDateTime.now();
        return new ChatMessageVO(messageId, "c1", "u1", 1L, role, status, "", null, null, "g1", null,
                null, null, null, null, null, now, now);
    }
}
