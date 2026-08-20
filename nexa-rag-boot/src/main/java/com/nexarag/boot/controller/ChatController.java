package com.nexarag.boot.controller;

import com.nexarag.auth.context.CurrentUserContext;
import com.nexarag.chat.id.ChatIdGenerator;
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
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
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

import java.util.List;

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
        String userId = CurrentUserContext.getRequired().userId();
        String generationId = idGenerator.nextId();
        String traceId = TraceIdContext.getTraceId();
        log.debug("用户原始问题：{}", request.content());
        List<Long> knowledgeBaseIds = List.copyOf(knowledgeBaseService.validateRequestedKnowledgeBases(request.knowledgeBaseIds()));
        ChatWorkflowRequest workflowRequest = new ChatWorkflowRequest(
                userId, request.conversationId(), request.content(), generationId, traceId, knowledgeBaseIds);

        // 2. 先打开本实例事件订阅，再驱动 Graph 执行，避免丢失首个工具快照
        return Flux.defer(() -> {
                    Flux<ChatStreamEvent> realtimeEvents = eventPublisher.open(generationId);
                    Flux<ChatStreamEvent> legacyGraphEvents = workflowService
                            .stream(CHAT_CONVERSATION_GRAPH_NAME, workflowRequest.toInitialState())
                            .handle((response, sink) -> {
                                if (response.getOutput() == null || response.getOutput().isCompletedExceptionally()) {
                                    return;
                                }
                                Object graphOutput = response.getOutput().join();
                                if (graphOutput instanceof StreamingOutput<?> streamingOutput
                                        && streamingOutput.getOriginData() instanceof ChatStreamEvent event) {
                                    if (event.eventVersion() <= 0) {
                                        sink.next(enrichLegacyEvent(event, request.conversationId(), traceId,
                                                generationId));
                                    }
                                }
                            });
                    return Flux.merge(realtimeEvents, legacyGraphEvents).map(this::toSse);
                })
                .onErrorResume(AbstractException.class, exception -> Flux.just(
                        toSse(new ChatStreamEvent(ChatStreamEventType.ERROR, null, request.conversationId(), traceId,
                                generationId, null, exception.getErrorCode(), exception.getErrorMessage()))));
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
        String userId = CurrentUserContext.getRequired().userId();
        return Flux.defer(() -> {
            // 1. 先创建本实例订阅，再读取 Redis 重放事件
            Flux<ChatStreamEvent> realtimeEvents = eventPublisher.open(generationId);
            List<ChatStreamEvent> replayEvents = resumeService.resume(generationId, userId, afterVersion);
            replayEvents.forEach(event -> eventPublisher.markDelivered(generationId, event.eventVersion()));

            // 2. 已有终态事件时只返回重放；否则连接后续实时事件
            Flux<ChatStreamEvent> replayFlux = Flux.fromIterable(replayEvents);
            boolean terminal = replayEvents.stream().anyMatch(this::isTerminal);
            return terminal ? replayFlux.map(this::toSse)
                    : Flux.concat(replayFlux, realtimeEvents).map(this::toSse);
        });
    }

    /**
     * 主动取消生成任务。
     *
     * @param generationId 生成任务 ID
     */
    @DeleteMapping("/generations/{generationId}")
    public void cancel(@PathVariable String generationId) {
        String userId = CurrentUserContext.getRequired().userId();
        if (!taskManager.cancel(generationId, userId)) {
            throw new ClientException("生成任务不存在或无权取消");
        }
    }

    private ServerSentEvent<ChatStreamEvent> toSse(ChatStreamEvent event) {
        ServerSentEvent.Builder<ChatStreamEvent> builder = ServerSentEvent.<ChatStreamEvent>builder(event)
                .event(event.type().name());
        if (event.eventVersion() > 0) {
            builder.id(String.valueOf(event.eventVersion()));
        }
        return builder.build();
    }

    private ChatStreamEvent enrichLegacyEvent(ChatStreamEvent event, String conversationId, String traceId,
                                               String generationId) {
        return new ChatStreamEvent(event.type(), event.content(), conversationId, traceId, generationId,
                event.messageId(), event.errorCode(), event.errorMessage(), event.eventVersion(), event.operations());
    }

    private boolean isTerminal(ChatStreamEvent event) {
        return event.type() == ChatStreamEventType.COMPLETE
                || event.type() == ChatStreamEventType.CANCELLED
                || event.type() == ChatStreamEventType.ERROR;
    }
}
