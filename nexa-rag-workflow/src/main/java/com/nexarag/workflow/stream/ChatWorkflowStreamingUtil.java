package com.nexarag.workflow.stream;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.nexarag.model.gateway.chat.ChatModelStreamResponse;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.ASSISTANT_CONTENT;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.COMPLETION_TOKENS;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.ERROR_CODE;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.ERROR_MESSAGE;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.FINISH_REASON;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.PROMPT_TOKENS;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.STREAM_STATUS;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.TOTAL_TOKENS;

/**
 * Chat 模型流转换工具，将模型分片转换为 Graph 流事件并回写最终状态。
 */
public final class ChatWorkflowStreamingUtil {

    private ChatWorkflowStreamingUtil() {
    }

    /**
     * 将模型流转换为 Graph 原生流输出。
     *
     * @param nodeClass 当前节点类型
     * @param state Graph 当前状态
     * @param modelStream 模型流
     * @return Graph 流输出
     */
    public static Flux<GraphResponse<StreamingOutput<ChatStreamEvent>>> toGraphStream(
            Class<?> nodeClass, OverAllState state, Flux<ChatModelStreamResponse> modelStream) {
        return toGraphStream(nodeClass, state, modelStream, new ChatGenerationAccumulator());
    }

    /**
     * 使用指定累积器将模型流转换为 Graph 原生流输出。
     *
     * @param nodeClass 当前节点类型
     * @param state Graph 当前状态
     * @param modelStream 模型流
     * @param accumulator 任务共享累积器
     * @return Graph 流输出
     */
    public static Flux<GraphResponse<StreamingOutput<ChatStreamEvent>>> toGraphStream(
            Class<?> nodeClass, OverAllState state, Flux<ChatModelStreamResponse> modelStream,
            ChatGenerationAccumulator accumulator) {
        return toGraphStream(nodeClass, state, modelStream, accumulator, event -> event);
    }

    /**
     * 使用指定发布器将模型流转换为 Graph 原生流输出。
     *
     * @param nodeClass 当前节点类型
     * @param state Graph 当前状态
     * @param modelStream 模型流
     * @param accumulator 任务共享累积器
     * @param eventPublisher 实时事件发布回调
     * @return Graph 流输出
     */
    public static Flux<GraphResponse<StreamingOutput<ChatStreamEvent>>> toGraphStream(
            Class<?> nodeClass, OverAllState state, Flux<ChatModelStreamResponse> modelStream,
            ChatGenerationAccumulator accumulator, Function<ChatStreamEvent, ChatStreamEvent> eventPublisher) {
        AtomicBoolean terminal = new AtomicBoolean();
        String nodeName = nodeClass.getSimpleName();

        return modelStream.concatMap(response -> {
                    // 1. 累积模型分片中的正文和用量
                    accumulator.append(response);
                    if (response.errorCode() != null) {
                        terminal.set(true);
                        ChatStreamEvent errorEvent = streamEvent(state,
                                ChatStreamEvent.error(response.errorCode(), response.errorMessage()));
                        ChatStreamEvent persistedError = eventPublisher.apply(errorEvent);
                        return Flux.just(event(nodeName, state, persistedError),
                                done(accumulator, "FAILED", response.errorCode(), response.errorMessage()));
                    }
                    if (response.content() != null && !response.content().isEmpty()) {
                        ChatStreamEvent answerDelta = streamEvent(state,
                                new ChatStreamEvent(ChatStreamEventType.ANSWER_DELTA, response.content(), null,
                                        null, null, null, null, null));
                        ChatStreamEvent persistedAnswerDelta = eventPublisher.apply(answerDelta);
                        return Flux.just(event(nodeName, state, persistedAnswerDelta));
                    }
                    if (response.finishReason() != null) {
                        terminal.set(true);
                        return Flux.just(done(accumulator, "COMPLETED", null, null));
                    }
                    return Flux.empty();
                })
                .onErrorResume(exception -> {
                    terminal.set(true);
                    ChatStreamEvent errorEvent = streamEvent(state,
                            ChatStreamEvent.error("MODEL_STREAM_ERROR", exception.getMessage()));
                    ChatStreamEvent persistedError = eventPublisher.apply(errorEvent);
                    return Flux.just(event(nodeName, state, persistedError),
                            done(accumulator, "FAILED", "MODEL_STREAM_ERROR", exception.getMessage()));
                })
                .concatWith(Flux.defer(() -> terminal.get()
                        ? Flux.empty() : Flux.just(done(accumulator, "COMPLETED", null, null))));
    }

    private static GraphResponse<StreamingOutput<ChatStreamEvent>> event(
            String nodeName, OverAllState state, ChatStreamEvent event) {
        return GraphResponse.of(new StreamingOutput<>(event, nodeName, state));
    }

    private static ChatStreamEvent streamEvent(OverAllState state, ChatStreamEvent event) {
        return new ChatStreamEvent(event.type(), event.content(), state.value("conversationId", ""),
                state.value("traceId", ""), state.value("generationId", ""),
                state.value("assistantMessageId", ""), event.errorCode(), event.errorMessage(),
                event.eventVersion(), event.operations());
    }

    private static GraphResponse<StreamingOutput<ChatStreamEvent>> done(
            ChatGenerationAccumulator accumulator, String status, String errorCode, String errorMessage) {
        ChatGenerationAccumulator.Snapshot snapshot = accumulator.snapshot();
        Map<String, Object> finalState = new LinkedHashMap<>();
        finalState.put(ASSISTANT_CONTENT, snapshot.content());
        finalState.put(STREAM_STATUS, status);
        putIfNotNull(finalState, FINISH_REASON, snapshot.finishReason());
        putIfNotNull(finalState, PROMPT_TOKENS, snapshot.promptTokens());
        putIfNotNull(finalState, COMPLETION_TOKENS, snapshot.completionTokens());
        putIfNotNull(finalState, TOTAL_TOKENS, snapshot.totalTokens());
        putIfNotNull(finalState, ERROR_CODE, errorCode);
        putIfNotNull(finalState, ERROR_MESSAGE, errorMessage);
        return GraphResponse.done(finalState);
    }

    private static void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }
}
