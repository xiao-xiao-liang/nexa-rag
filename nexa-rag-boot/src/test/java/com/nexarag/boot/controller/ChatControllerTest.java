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
import com.nexarag.document.service.DocumentChunkService;
import com.nexarag.document.service.DocumentService;
import com.nexarag.chat.service.impl.ChatCitationService;
import com.nexarag.chat.domain.ChatCitationDTO;
import com.nexarag.document.model.entity.Document;
import com.nexarag.document.model.entity.DocumentChunk;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

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
        when(eventPublisher.open("g1")).thenReturn(Flux.just(ChatStreamEvent.token("你")));
        StreamingOutput<ChatStreamEvent> output = new StreamingOutput<>(
                ChatStreamEvent.token("你"), "answer", new OverAllState(Map.of()));
        when(workflowService.stream(eq("chat-conversation"), any())).thenReturn(Flux.just(GraphResponse.of(output)));
        ChatController controller = new ChatController(
                workflowService, mock(ChatGenerationTaskManager.class), eventPublisher, resumeService,
                idGenerator, knowledgeBaseService, mock(ChatCitationService.class), mock(DocumentChunkService.class),
                mock(DocumentService.class), Schedulers.immediate());
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
        ChatGenerationTaskManager taskManager = mock(ChatGenerationTaskManager.class);
        ChatIdGenerator idGenerator = mock(ChatIdGenerator.class);
        ChatGenerationEventPublisher eventPublisher = mock(ChatGenerationEventPublisher.class);
        ChatStreamResumeService resumeService = mock(ChatStreamResumeService.class);
        KnowledgeBaseService knowledgeBaseService = mock(KnowledgeBaseService.class);
        when(idGenerator.nextId()).thenReturn("g1");
        when(knowledgeBaseService.validateRequestedKnowledgeBases(java.util.List.of())).thenReturn(Set.of());
        when(workflowService.stream(eq("chat-conversation"), any()))
                .thenThrow(new ServiceException("未找到流式工作流图"));
        when(taskManager.fail("g1", "B000001", "未找到流式工作流图")).thenReturn(true);
        ChatController controller = new ChatController(
                workflowService, taskManager, eventPublisher, resumeService,
                idGenerator, knowledgeBaseService, mock(ChatCitationService.class), mock(DocumentChunkService.class),
                mock(DocumentService.class), Schedulers.immediate());
        CurrentUserContext.set(new CurrentUser("u1"));

        StepVerifier.create(controller.stream(new ChatStreamRequest(null, "你好")))
                .assertNext(event -> {
                    assertThat(event.data().type().name()).isEqualTo("ERROR");
                    assertThat(event.data().errorCode()).isEqualTo("B000001");
                    assertThat(event.data().errorMessage()).isEqualTo("未找到流式工作流图");
                })
                .verifyComplete();
        verify(taskManager).fail("g1", "B000001", "未找到流式工作流图");
    }

    @Test
    void streamShouldSubscribeWorkflowOnBoundedElasticThread() {
        WorkflowService workflowService = mock(WorkflowService.class);
        ChatIdGenerator idGenerator = mock(ChatIdGenerator.class);
        ChatGenerationEventPublisher eventPublisher = mock(ChatGenerationEventPublisher.class);
        ChatStreamResumeService resumeService = mock(ChatStreamResumeService.class);
        KnowledgeBaseService knowledgeBaseService = mock(KnowledgeBaseService.class);
        AtomicReference<String> subscriptionThread = new AtomicReference<>();
        when(idGenerator.nextId()).thenReturn("g1");
        when(knowledgeBaseService.validateRequestedKnowledgeBases(java.util.List.of())).thenReturn(Set.of());
        when(eventPublisher.open("g1")).thenReturn(Flux.empty());
        when(workflowService.stream(eq("chat-conversation"), any())).thenReturn(Flux.defer(() -> {
            subscriptionThread.set(Thread.currentThread().getName());
            return Flux.empty();
        }));
        Scheduler scheduler = Schedulers.newBoundedElastic(1, 10, "chat-workflow");
        ChatController controller = new ChatController(
                workflowService, mock(ChatGenerationTaskManager.class), eventPublisher, resumeService,
                idGenerator, knowledgeBaseService, mock(ChatCitationService.class), mock(DocumentChunkService.class),
                mock(DocumentService.class), scheduler);
        CurrentUserContext.set(new CurrentUser("u1"));

        try {
            StepVerifier.create(controller.stream(new ChatStreamRequest(null, "你好")))
                    .verifyComplete();

            assertThat(subscriptionThread.get()).contains("chat-workflow");
        } finally {
            scheduler.dispose();
        }
    }

    @Test
    void citationShouldValidateMessageDocumentAndChunkBeforeReturningPreview() {
        ChatCitationService citationService = mock(ChatCitationService.class);
        DocumentService documentService = mock(DocumentService.class);
        DocumentChunkService chunkService = mock(DocumentChunkService.class);
        KnowledgeBaseService knowledgeBaseService = mock(KnowledgeBaseService.class);
        when(citationService.getOwnedCitation("m1", "u1", 1))
                .thenReturn(new ChatCitationDTO(1, 10L, "chunk-1", 2, "引用标题", null, 1, 0.9D, "hybrid"));
        Document document = new Document();
        document.setDocumentId(10L);
        document.setKnowledgeBaseId(3L);
        document.setTitle("费用制度");
        when(documentService.getRequiredDocument(10L)).thenReturn(document);
        when(knowledgeBaseService.getRequiredDocument(3L, 10L)).thenReturn(document);
        DocumentChunk chunk = new DocumentChunk();
        chunk.setChunkId("chunk-1");
        chunk.setDocumentId(10L);
        chunk.setChunkOrder(2);
        chunk.setText("可展示的分块正文");
        when(chunkService.getById("chunk-1")).thenReturn(chunk);
        ChatController controller = new ChatController(mock(WorkflowService.class), mock(ChatGenerationTaskManager.class),
                mock(ChatGenerationEventPublisher.class), mock(ChatStreamResumeService.class), mock(ChatIdGenerator.class),
                knowledgeBaseService, citationService, chunkService, documentService, Schedulers.immediate());
        CurrentUserContext.set(new CurrentUser("u1"));

        var response = controller.citation("m1", 1);

        assertThat(response.data().title()).isEqualTo("费用制度");
        assertThat(response.data().content()).isEqualTo("可展示的分块正文");
        assertThat(response.data().documentPath()).isEqualTo("/knowledge-base/3/documents/10");
        assertThat(response.data().sourceUrl()).isNull();
    }
}
