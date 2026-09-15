package com.nexarag.model.execution.telemetry;

import com.nexarag.infra.observability.langfuse.LangfuseGenerationScope;
import com.nexarag.infra.observability.langfuse.LangfuseTelemetry;
import com.nexarag.infra.observability.langfuse.model.LangfuseGenerationResult;
import com.nexarag.model.client.vllm.VllmTokenizerClient;
import com.nexarag.model.config.ModelProfileProperties;
import com.nexarag.model.entity.ModelCallLog;
import com.nexarag.model.enums.ModelBizType;
import com.nexarag.model.enums.ModelRequestType;
import com.nexarag.model.enums.TokenUsageSource;
import com.nexarag.model.execution.ModelExecutionCommand;
import com.nexarag.model.gateway.rerank.RerankModelRequest;
import com.nexarag.model.route.ModelRouteDecision;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Generation 分段 Token 属性回归测试。
 */
class GenerationTelemetryCollectorTest {

    @Test
    void shouldWriteOnlyNumericBreakdownAndTemplateOverheadToGeneration() {
        LangfuseTelemetry telemetry = mock(LangfuseTelemetry.class);
        LangfuseGenerationScope scope = mock(LangfuseGenerationScope.class);
        VllmTokenizerClient tokenizerClient = mock(VllmTokenizerClient.class);
        ModelProfileProperties profile = ModelProfileProperties.builder().provider("VLLM")
                .modelName("Qwen3.5-4B").build();
        RagTokenBreakdown rawBreakdown = new RagTokenBreakdown("system secret", "summary secret", List.of(),
                "question secret", "evidence secret", "tool secret", 512, 10, 3, 7,
                null, null, null, null, null, null, null, RagTokenBreakdownStatus.UNAVAILABLE);
        RagTokenBreakdown exactBreakdown = rawBreakdown.withExactTokenCounts(10, 5, 20, 8, 50, 2);
        when(telemetry.startGeneration(any(), eq(Context.root()))).thenReturn(scope);
        when(tokenizerClient.countBreakdown(profile, rawBreakdown)).thenReturn(Mono.just(exactBreakdown));
        GenerationTelemetryCollector collector = new GenerationTelemetryCollector(telemetry, tokenizerClient);
        ModelExecutionCommand<String> command = new ModelExecutionCommand<>("business-trace", ModelBizType.CHAT,
                "conversation-1", "local-qwen", ModelRequestType.CHAT, decision -> "ok", value -> 0,
                value -> 0, value -> 0, value -> TokenUsageSource.PROVIDER_USAGE, rawBreakdown, "generation-1",
                "rag.answer", Context.root());

        collector.start(command, new ModelRouteDecision("local", profile, false),
                ModelCallLog.builder().callId("call-1").build()).complete(100, 20, 120, 50L);

        org.mockito.ArgumentCaptor<LangfuseGenerationResult> resultCaptor =
                org.mockito.ArgumentCaptor.forClass(LangfuseGenerationResult.class);
        verify(scope).finish(resultCaptor.capture());
        LangfuseGenerationResult result = resultCaptor.getValue();
        assertThat(result.inputTokens()).isEqualTo(100);
        assertThat(result.finishedAt()).isNotNull();
        assertThat(result.attributes()).containsEntry("nexa.rag_token_status", "COMPLETE")
                .containsEntry("nexa.rag_system_tokens", 10)
                .containsEntry("nexa.rag_retrieval_tokens", 50)
                .containsEntry("nexa.rag_template_overhead_tokens", 5)
                .doesNotContainKeys("systemContent", "summaryContent", "questionContent", "retrievalContent",
                        "toolContent");
    }

    @Test
    void shouldNameRerankGenerationAndKeepItsWorkflowContext() {
        LangfuseTelemetry telemetry = mock(LangfuseTelemetry.class);
        LangfuseGenerationScope scope = mock(LangfuseGenerationScope.class);
        VllmTokenizerClient tokenizerClient = mock(VllmTokenizerClient.class);
        ModelProfileProperties profile = ModelProfileProperties.builder().provider("VLLM")
                .modelName("Qwen3.5-4B-Reranker").build();
        Context workflowContext = Context.root().with(ContextKey.named("test.langfuse.context"), "present");
        RerankModelRequest request = RerankModelRequest.builder()
                .traceId("business-trace")
                .bizType(ModelBizType.RERANK)
                .bizId("chat-rerank")
                .routeKey("rerank")
                .query("ignored")
                .candidates(List.of())
                .langfuseContext(workflowContext)
                .build();
        ModelExecutionCommand<?> command = ModelExecutionCommand.ofRerank(request, decision -> null);
        when(telemetry.startGeneration(any(), eq(workflowContext))).thenReturn(scope);
        GenerationTelemetryCollector collector = new GenerationTelemetryCollector(telemetry, tokenizerClient);

        collector.start(command, new ModelRouteDecision("local", profile, false),
                ModelCallLog.builder().callId("call-1").build());

        org.mockito.ArgumentCaptor<com.nexarag.infra.observability.langfuse.model.LangfuseGenerationCommand> commandCaptor =
                org.mockito.ArgumentCaptor.forClass(com.nexarag.infra.observability.langfuse.model.LangfuseGenerationCommand.class);
        verify(telemetry).startGeneration(commandCaptor.capture(), eq(workflowContext));
        assertThat(commandCaptor.getValue().name()).isEqualTo("rag.rerank-model");
    }
}
