package com.nexarag.boot.service;

import com.nexarag.infra.observability.langfuse.aop.LangfuseTelemetryAspect;
import com.nexarag.infra.observability.langfuse.otel.LangfuseOtelContextCodec;
import com.nexarag.workflow.request.ChatWorkflowRequest;
import com.nexarag.workflow.service.WorkflowService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.LANGFUSE_OTEL_CONTEXT_CARRIER;
import static com.nexarag.workflow.constants.ChatWorkflowGraphConstants.CHAT_CONVERSATION_GRAPH_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 对话工作流流式遥测上下文传递测试。
 */
class ChatWorkflowStreamServiceTest {

    @Test
    void shouldPassSerializableOtelContextCarrierFromReactorContextToWorkflowState() {
        WorkflowService workflowService = mock(WorkflowService.class);
        when(workflowService.stream(eq(CHAT_CONVERSATION_GRAPH_NAME), any(Map.class))).thenReturn(Flux.empty());
        ChatWorkflowStreamService service = new ChatWorkflowStreamService(workflowService, Schedulers.immediate());
        Context traceContext = Context.root().with(Span.wrap(SpanContext.create(
                "0123456789abcdef0123456789abcdef", "0123456789abcdef", TraceFlags.getSampled(),
                TraceState.getDefault())));

        service.stream(new ChatWorkflowRequest("user-001", "tenant-001", "conversation-001", "问题",
                        "generation-001", "trace-001", java.util.List.of()))
                .contextWrite(context -> context.put(LangfuseTelemetryAspect.OTEL_CONTEXT_KEY, traceContext))
                .blockLast();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> stateCaptor = ArgumentCaptor.forClass(Map.class);
        verify(workflowService).stream(eq(CHAT_CONVERSATION_GRAPH_NAME), stateCaptor.capture());
        Map<String, Object> state = stateCaptor.getValue();
        assertThat(state)
                .containsEntry(LANGFUSE_OTEL_CONTEXT_CARRIER, LangfuseOtelContextCodec.encode(traceContext))
                .doesNotContainKeys("langfuseOtelContext");
        assertThat(state.get(LANGFUSE_OTEL_CONTEXT_CARRIER)).isInstanceOf(String.class);
        assertThatNoException().isThrownBy(() -> new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(state));
    }
}
