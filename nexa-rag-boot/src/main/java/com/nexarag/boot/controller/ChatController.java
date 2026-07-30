package com.nexarag.boot.controller;

import com.nexarag.auth.context.CurrentUserContext;
import com.nexarag.chat.id.ChatIdGenerator;
import com.nexarag.common.exception.AbstractException;
import com.nexarag.common.exception.ClientException;
import com.nexarag.common.trace.TraceIdContext;
import com.nexarag.workflow.request.ChatWorkflowRequest;
import com.nexarag.workflow.service.WorkflowService;
import com.nexarag.workflow.stream.ChatGenerationTaskManager;
import com.nexarag.workflow.stream.ChatStreamEvent;
import com.nexarag.workflow.stream.ChatStreamEventType;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicBoolean;

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
    private final ChatIdGenerator idGenerator;

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
        ChatWorkflowRequest workflowRequest = new ChatWorkflowRequest(
                userId, request.conversationId(), request.content(), generationId, traceId);

        // 2. 将 Graph 流式输出映射为 SSE 协议
        return Flux.defer(() -> {
                    AtomicBoolean metaSent = new AtomicBoolean();
                    Flux<ServerSentEvent<ChatStreamEvent>> events = workflowService
                            .stream(CHAT_CONVERSATION_GRAPH_NAME, workflowRequest.toInitialState())
                            .handle((response, sink) -> {
                                if (response.getOutput() == null || response.getOutput().isCompletedExceptionally()) {
                                    return;
                                }
                                Object graphOutput = response.getOutput().join();
                                if (graphOutput instanceof StreamingOutput<?> streamingOutput
                                        && streamingOutput.getOriginData() instanceof ChatStreamEvent event) {
                                    sink.next(toSse(event));
                                } else if (graphOutput instanceof NodeOutput nodeOutput
                                        && "conversationValidation".equals(nodeOutput.node())
                                        && metaSent.compareAndSet(false, true)) {
                                    String conversationId = nodeOutput.state().value("conversationId", "");
                                    sink.next(toSse(new ChatStreamEvent(ChatStreamEventType.META, null,
                                            conversationId, traceId, generationId, null, null, null)));
                                }
                            });
                    ChatStreamEvent complete = new ChatStreamEvent(ChatStreamEventType.COMPLETE, null,
                            request.conversationId(), traceId, generationId, null, null, null);
                    return events.concatWithValues(toSse(complete));
                })
                .onErrorResume(AbstractException.class, exception -> Flux.just(
                        toSse(ChatStreamEvent.error(exception.getErrorCode(), exception.getErrorMessage()))))
                .doOnCancel(() -> taskManager.cancel(generationId, userId));
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
        return ServerSentEvent.<ChatStreamEvent>builder(event)
                .event(event.type().name())
                .build();
    }
}
