package com.nexarag.boot.controller;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.nexarag.auth.context.CurrentUser;
import com.nexarag.auth.context.CurrentUserContext;
import com.nexarag.chat.id.ChatIdGenerator;
import com.nexarag.common.trace.TraceIdContext;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.workflow.service.WorkflowService;
import com.nexarag.workflow.stream.ChatGenerationTaskManager;
import com.nexarag.workflow.stream.ChatGenerationEventPublisher;
import com.nexarag.workflow.stream.ChatStreamEvent;
import com.nexarag.workflow.stream.ChatStreamResumeService;
import com.nexarag.document.service.KnowledgeBaseService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.Map;
import java.util.Set;

import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.USER_ID;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.TRACE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Chat 流式接口测试，验证用户身份来自请求上下文且 TOKEN 被映射为 SSE。
 */
class ChatControllerTest {

    @AfterEach
    void clearContext() {
        CurrentUserContext.clear();
        TraceIdContext.clear();
    }

    @Test
    void streamShouldUseCurrentUserAndReturnTokenEvent() {
        WorkflowService workflowService = mock(WorkflowService.class);
        ChatIdGenerator idGenerator = mock(ChatIdGenerator.class);
        ChatGenerationEventPublisher eventPublisher = mock(ChatGenerationEventPublisher.class);
        ChatStreamResumeService resumeService = mock(ChatStreamResumeService.class);
        KnowledgeBaseService knowledgeBaseService = mock(KnowledgeBaseService.class);
        when(idGenerator.nextId()).thenReturn("g1");
        when(knowledgeBaseService.validateRequestedKnowledgeBases(java.util.List.of())).thenReturn(Set.of());
        when(eventPublisher.open("g1")).thenReturn(Flux.empty());
        StreamingOutput<ChatStreamEvent> output = new StreamingOutput<>(
                ChatStreamEvent.token("你"), "answer", new OverAllState(Map.of()));
        when(workflowService.stream(eq("chat-conversation"), any())).thenReturn(Flux.just(GraphResponse.of(output)));
        ChatController controller = new ChatController(
                workflowService, mock(ChatGenerationTaskManager.class), eventPublisher, resumeService,
                idGenerator, knowledgeBaseService);
        CurrentUserContext.set(new CurrentUser("u1"));
        TraceIdContext.setTraceId("trace-001");

        StepVerifier.create(controller.stream(new ChatStreamRequest(null, "你好")))
                .assertNext(event -> assertThat(event.data().type().name()).isEqualTo("TOKEN"))
                .verifyComplete();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(workflowService).stream(eq("chat-conversation"), captor.capture());
        assertThat(captor.getValue())
                .containsEntry(USER_ID, "u1")
                .containsEntry(TRACE_ID, "trace-001");
    }

    @Test
    void streamShouldReturnErrorEventWhenWorkflowCannotBeStarted() {
        WorkflowService workflowService = mock(WorkflowService.class);
        ChatIdGenerator idGenerator = mock(ChatIdGenerator.class);
        ChatGenerationEventPublisher eventPublisher = mock(ChatGenerationEventPublisher.class);
        ChatStreamResumeService resumeService = mock(ChatStreamResumeService.class);
        KnowledgeBaseService knowledgeBaseService = mock(KnowledgeBaseService.class);
        when(idGenerator.nextId()).thenReturn("g1");
        when(knowledgeBaseService.validateRequestedKnowledgeBases(java.util.List.of())).thenReturn(Set.of());
        when(workflowService.stream(eq("chat-conversation"), any()))
                .thenThrow(new ServiceException("未找到流式工作流图"));
        ChatController controller = new ChatController(
                workflowService, mock(ChatGenerationTaskManager.class), eventPublisher, resumeService,
                idGenerator, knowledgeBaseService);
        CurrentUserContext.set(new CurrentUser("u1"));

        StepVerifier.create(controller.stream(new ChatStreamRequest(null, "你好")))
                .assertNext(event -> {
                    assertThat(event.data().type().name()).isEqualTo("ERROR");
                    assertThat(event.data().errorCode()).isEqualTo("B000001");
                    assertThat(event.data().errorMessage()).isEqualTo("未找到流式工作流图");
                })
                .verifyComplete();
    }
}
