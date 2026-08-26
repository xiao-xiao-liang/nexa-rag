package com.nexarag.boot.controller;

import com.nexarag.auth.context.UserContext;
import com.nexarag.chat.id.ChatIdGenerator;
import com.nexarag.chat.domain.ChatCitationDTO;
import com.nexarag.chat.domain.ChatCitationDetailVO;
import com.nexarag.chat.service.impl.ChatCitationService;
import com.nexarag.common.exception.AbstractException;
import com.nexarag.common.exception.ClientException;
import com.nexarag.common.trace.TraceIdContext;
import com.nexarag.workflow.request.ChatWorkflowRequest;
import com.nexarag.workflow.service.WorkflowService;
import com.nexarag.workflow.stream.ChatGenerationTaskManager;
import com.nexarag.workflow.stream.ChatGenerationEventPublisher;
import com.nexarag.workflow.stream.ChatStreamEvent;
import com.nexarag.workflow.stream.ChatStreamEventType;
import com.nexarag.workflow.stream.ChatStreamResumeService;
import com.nexarag.document.service.KnowledgeBaseService;
import com.nexarag.document.service.DocumentChunkService;
import com.nexarag.document.service.DocumentService;
import com.nexarag.document.model.entity.Document;
import com.nexarag.document.model.entity.DocumentChunk;
import com.nexarag.infra.enums.ExternalDocumentSourceType;
import com.nexarag.common.web.Result;
import com.nexarag.common.web.Results;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static com.nexarag.workflow.constants.ChatWorkflowGraphConstants.CHAT_CONVERSATION_GRAPH_NAME;

/**
 * Chat 流式对话控制器，负责身份注入、SSE 映射和生成任务取消。
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final WorkflowService workflowService;
    private final ChatGenerationTaskManager taskManager;
    private final ChatGenerationEventPublisher eventPublisher;
    private final ChatStreamResumeService resumeService;
    private final ChatIdGenerator idGenerator;
    private final KnowledgeBaseService knowledgeBaseService;
    private final ChatCitationService citationService;
    private final DocumentChunkService documentChunkService;
    private final DocumentService documentService;
    private final Scheduler chatWorkflowScheduler;

    /**
     * 发起流式对话。
     *
     * @param request 对话请求
     * @return SSE 事件流
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatStreamEvent>> stream(@RequestBody ChatStreamRequest request) {
        if (request == null || request.content() == null || request.content().isBlank()) {
            throw new ClientException("消息内容不能为空");
        }
        // 1. 从鉴权上下文读取用户并生成请求级标识
        var currentUser = UserContext.getCurrUser();
        String userId = currentUser.userId();
        String generationId = idGenerator.nextId();
        String traceId = TraceIdContext.getTraceId();
        log.debug("用户原始问题：{}", request.content());
        List<Long> knowledgeBaseIds = List.copyOf(knowledgeBaseService.validateRequestedKnowledgeBases(request.knowledgeBaseIds()));
        ChatWorkflowRequest workflowRequest = new ChatWorkflowRequest(
                userId, currentUser.tenantId(), request.conversationId(), request.content(), generationId, traceId,
                knowledgeBaseIds);

        // 2. 先打开本实例事件订阅，再驱动 Graph 执行，避免丢失首个工具快照
        return Flux.defer(() -> {
                    Flux<ChatStreamEvent> realtimeEvents = eventPublisher.open(generationId);
                    Flux<ChatStreamEvent> workflowCompletion = Flux.defer(() -> workflowService
                                    .stream(CHAT_CONVERSATION_GRAPH_NAME, workflowRequest.toInitialState()))
                            // Graph 的构建与前置节点均可能阻塞，必须整体脱离 Servlet 请求线程。
                            .subscribeOn(chatWorkflowScheduler)
                            // 所有客户端事件均由 eventPublisher 发布；Graph 包装的节点异常必须重新传播。
                            .flatMap(response -> response.isError()
                                    ? Mono.fromFuture(response.getOutput()).then()
                                    : Mono.empty())
                            .thenMany(Flux.empty());
                    return Flux.merge(realtimeEvents, workflowCompletion)
                            .map(this::toSse);
                })
                .onErrorResume(exception -> {
                    log.error("Chat SSE 请求执行失败，generationId={}，traceId={}", generationId, traceId, exception);
                    String errorCode = exception instanceof AbstractException abstractException
                            ? abstractException.getErrorCode() : "CHAT_WORKFLOW_ERROR";
                    String errorMessage = exception instanceof AbstractException abstractException
                            ? abstractException.getErrorMessage() : "对话工作流执行失败，请稍后重试";
                    try {
                        if (!taskManager.fail(generationId, errorCode, errorMessage)) {
                            eventPublisher.complete(generationId);
                        }
                    } catch (Exception finalizationException) {
                        log.error("Chat SSE 失败最终化异常，generationId={}，traceId={}", generationId, traceId,
                                finalizationException);
                    }
                    return Flux.just(toSse(new ChatStreamEvent(ChatStreamEventType.ERROR, null,
                            request.conversationId(), traceId, generationId, null, errorCode, errorMessage)));
                });
    }

    /**
     * 恢复断线前正在进行的回答，并连接本实例后续的实时事件。
     *
     * @param generationId 生成任务 ID
     * @param afterVersion 客户端已接收的最大事件版本
     * @return SSE 事件流
     */
    @GetMapping(value = "/generations/{generationId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatStreamEvent>> resume(@PathVariable String generationId,
                                                          @RequestParam(defaultValue = "0") long afterVersion) {
        String userId = UserContext.getCurrUser().userId();
        return Flux.defer(() -> {
            // 1. 先创建本实例订阅，再读取 Redis 重放事件
            Flux<ChatStreamEvent> realtimeEvents = eventPublisher.open(generationId);
            List<ChatStreamEvent> replayEvents = resumeService.resume(generationId, userId, afterVersion);
            replayEvents.forEach(event -> eventPublisher.markDelivered(generationId, event.eventVersion()));

            // 2. 已有终态事件时只返回重放；否则连接后续实时事件
            Flux<ChatStreamEvent> replayFlux = Flux.fromIterable(replayEvents);
            boolean terminal = replayEvents.stream().anyMatch(this::isTerminal);
            AtomicLong lastDeliveredVersion = new AtomicLong(Math.max(afterVersion, 0L));
            Flux<ChatStreamEvent> events = terminal ? replayFlux : Flux.concat(replayFlux, realtimeEvents);
            return events
                    .filter(event -> shouldDeliverEvent(event, lastDeliveredVersion))
                    .map(this::toSse);
        });
    }

    /**
     * 主动取消生成任务。
     *
     * @param generationId 生成任务 ID
     */
    @DeleteMapping("/generations/{generationId}")
    public void cancel(@PathVariable String generationId) {
        String userId = UserContext.getCurrUser().userId();
        if (!taskManager.cancel(generationId, userId)) {
            throw new ClientException("生成任务不存在或无权取消");
        }
    }

    /**
     * 读取当前用户可访问的单条引用预览。
     *
     * @param messageId 助手消息 ID
     * @param citationId 消息内引用编号
     * @return 引用预览与受控跳转地址
     */
    @GetMapping("/messages/{messageId}/citations/{citationId}")
    public Result<ChatCitationDetailVO> citation(@PathVariable String messageId, @PathVariable int citationId) {
        String userId = UserContext.getCurrUser().userId();
        ChatCitationDTO citation = citationService.getOwnedCitation(messageId, userId, citationId);
        Document document = requireOwnedDocument(citation.documentId());
        DocumentChunk chunk = documentChunkService.getById(citation.chunkId());
        if (chunk == null || !document.getDocumentId().equals(chunk.getDocumentId())) {
            throw new ClientException("引用分块不存在或已失效");
        }
        String documentPath = "/knowledge-base/" + document.getKnowledgeBaseId()
                + "/documents/" + document.getDocumentId();
        String sourceUrl = document.getSourceType() == null || document.getSourceType() == ExternalDocumentSourceType.LOCAL
                ? null : document.getSourceUrl();
        return Results.success(new ChatCitationDetailVO(citation.citationId(), document.getTitle(),
                chunk.getChunkOrder(), chunk.getText(), documentPath, sourceUrl));
    }

    private ServerSentEvent<ChatStreamEvent> toSse(ChatStreamEvent event) {
        ServerSentEvent.Builder<ChatStreamEvent> builder = ServerSentEvent.<ChatStreamEvent>builder(event)
                .event(event.type().name());
        if (event.eventVersion() > 0) {
            builder.id(String.valueOf(event.eventVersion()));
        }
        return builder.build();
    }

    private Document requireOwnedDocument(Long documentId) {
        if (documentId == null) {
            throw new ClientException("引用文档不存在或已失效");
        }
        Document document = documentService.getRequiredDocument(documentId);
        // 文档已由 documentService 查询，无需再次按文档 ID 查询；只补齐当前租户的知识库归属校验。
        knowledgeBaseService.getRequiredKnowledgeBase(document.getKnowledgeBaseId());
        return document;
    }

    private boolean isTerminal(ChatStreamEvent event) {
        return event.type() == ChatStreamEventType.COMPLETE
                || event.type() == ChatStreamEventType.CANCELLED
                || event.type() == ChatStreamEventType.ERROR;
    }

    /**
     * 按事件版本过滤重放窗口与本地 sink 中重复的事件。
     *
     * @param event 待发送事件
     * @param lastDeliveredVersion 当前连接已发送的最大版本
     * @return true 表示当前连接应继续发送该事件
     */
    private boolean shouldDeliverEvent(ChatStreamEvent event, AtomicLong lastDeliveredVersion) {
        if (event.eventVersion() <= 0) {
            return true;
        }
        while (true) {
            long previousVersion = lastDeliveredVersion.get();
            if (event.eventVersion() <= previousVersion) {
                return false;
            }
            if (lastDeliveredVersion.compareAndSet(previousVersion, event.eventVersion())) {
                return true;
            }
        }
    }
}
