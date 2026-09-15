package com.nexarag.boot.service;

import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.nexarag.infra.observability.langfuse.otel.LangfuseOtelContextCodec;
import com.nexarag.infra.observability.langfuse.aop.LangfuseTelemetryAspect;
import com.nexarag.infra.observability.langfuse.aop.LangfuseTrace;
import com.nexarag.workflow.request.ChatWorkflowRequest;
import com.nexarag.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.nexarag.workflow.constants.ChatWorkflowGraphConstants.CHAT_CONVERSATION_GRAPH_NAME;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.LANGFUSE_OTEL_CONTEXT_CARRIER;

/**
 * 已完成身份上下文注入的对话工作流流式入口。
 */
@Service
@RequiredArgsConstructor
public class ChatWorkflowStreamService {

    private final WorkflowService workflowService;

    @Qualifier("chatWorkflowScheduler")
    private final Scheduler chatWorkflowScheduler;

    /**
     * 启动对话工作流，并将当前 Langfuse Root Trace 的可序列化父上下文载体传入 Graph 状态。
     *
     * @param request 已由 Controller 可信构造的工作流请求
     * @return Graph 流式响应
     */
    @LangfuseTrace(name = "rag.chat", correlationId = "#request.traceId", sessionId = "#request.conversationId",
            userId = "#request.userId", attributes = {"nexa.generation_id=#request.generationId",
            "nexa.tenant_id=#request.tenantId"})
    public Flux<GraphResponse<StreamingOutput<?>>> stream(ChatWorkflowRequest request) {
        return Flux.deferContextual(contextView -> {
            Map<String, Object> initialState = new LinkedHashMap<>(request.toInitialState());
            Object context = contextView.getOrDefault(LangfuseTelemetryAspect.OTEL_CONTEXT_KEY, null);
            if (context instanceof io.opentelemetry.context.Context otelContext) {
                String carrier = LangfuseOtelContextCodec.encode(otelContext);
                if (!carrier.isBlank()) {
                    initialState.put(LANGFUSE_OTEL_CONTEXT_CARRIER, carrier);
                }
            }
            return workflowService.stream(CHAT_CONVERSATION_GRAPH_NAME, initialState)
                    .subscribeOn(chatWorkflowScheduler);
        });
    }
}
