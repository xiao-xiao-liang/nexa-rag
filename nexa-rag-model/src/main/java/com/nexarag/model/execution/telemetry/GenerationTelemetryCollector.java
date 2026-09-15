package com.nexarag.model.execution.telemetry;

import static com.nexarag.infra.observability.langfuse.constants.LangfuseOtelAttributeConstant.COMPLETED_STATE;
import static com.nexarag.model.constants.ModelTelemetryConstant.DEFAULT_CHAT_GENERATION_NAME;
import static com.nexarag.model.constants.ModelTelemetryConstant.RAG_INPUT_BUDGET_TOKENS;
import static com.nexarag.model.constants.ModelTelemetryConstant.RAG_QUESTION_TOKENS;
import static com.nexarag.model.constants.ModelTelemetryConstant.RAG_RETRIEVAL_ACCEPTED_COUNT;
import static com.nexarag.model.constants.ModelTelemetryConstant.RAG_RETRIEVAL_CANDIDATE_COUNT;
import static com.nexarag.model.constants.ModelTelemetryConstant.RAG_RETRIEVAL_SKIPPED_COUNT;
import static com.nexarag.model.constants.ModelTelemetryConstant.RAG_RETRIEVAL_TOKENS;
import static com.nexarag.model.constants.ModelTelemetryConstant.RAG_SYSTEM_TOKENS;
import static com.nexarag.model.constants.ModelTelemetryConstant.RAG_TEMPLATE_OVERHEAD_TOKENS;
import static com.nexarag.model.constants.ModelTelemetryConstant.RAG_TOKEN_STATUS;
import static com.nexarag.model.constants.ModelTelemetryConstant.RAG_TOOL_TOKENS;
import static com.nexarag.model.constants.ModelTelemetryConstant.RAG_HISTORY_TOKENS;
import static com.nexarag.model.constants.ModelTelemetryConstant.RAG_SUMMARY_TOKENS;
import static com.nexarag.model.constants.ModelTelemetryConstant.RERANK_GENERATION_NAME;
import static com.nexarag.model.constants.ModelTelemetryConstant.ROUTE_KEY;

import com.nexarag.infra.observability.langfuse.LangfuseGenerationScope;
import com.nexarag.infra.observability.langfuse.LangfuseTelemetry;
import com.nexarag.infra.observability.langfuse.model.LangfuseGenerationCommand;
import com.nexarag.infra.observability.langfuse.model.LangfuseGenerationResult;
import com.nexarag.model.client.vllm.VllmTokenizerClient;
import com.nexarag.model.execution.ModelExecutionCommand;
import com.nexarag.model.entity.ModelCallLog;
import com.nexarag.model.enums.ModelRequestType;
import com.nexarag.model.route.ModelRouteDecision;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 汇总单次模型调用的 Langfuse Generation 指标。
 */
@Component
@RequiredArgsConstructor
public class GenerationTelemetryCollector {

    private final LangfuseTelemetry telemetry;
    private final VllmTokenizerClient tokenizerClient;

    /**
     * 在已取得模型调用日志 ID 后创建 Generation，并后台启动分段 Token 统计。
     *
     * @param command  模型执行命令
     * @param decision 实际路由决策
     * @param log      模型调用日志
     * @return 本次调用的观测句柄
     */
    public ActiveGeneration start(ModelExecutionCommand<?> command, ModelRouteDecision decision, ModelCallLog log) {
        LangfuseGenerationScope scope = telemetry.startGeneration(new LangfuseGenerationCommand(generationName(command),
                decision.profile().getModelName(), decision.profile().getProvider(), log.getCallId(),
                command.generationId(), command.traceId(), Map.of(ROUTE_KEY, command.routeKey())),
                command.langfuseContext());
        CompletableFuture<RagTokenBreakdown> breakdownFuture = command.observabilityContext() == null
                ? CompletableFuture.completedFuture(null)
                : tokenizerClient.countBreakdown(decision.profile(), command.observabilityContext()).toFuture();
        return new ActiveGeneration(scope, breakdownFuture);
    }

    /**
     * 根据模型请求类型使用稳定且可筛选的 Generation 名称。
     *
     * @param command 模型执行命令
     * @return Langfuse Generation 名称
     */
    private String generationName(ModelExecutionCommand<?> command) {
        if (command.requestType() == ModelRequestType.RERANK) {
            return RERANK_GENERATION_NAME;
        }
        return StringUtils.hasText(command.observationName()) ? command.observationName() : DEFAULT_CHAT_GENERATION_NAME;
    }

    /** 单次 Generation 的可关闭运行态。 */
    public record ActiveGeneration(LangfuseGenerationScope scope,
                                   CompletableFuture<RagTokenBreakdown> breakdownFuture) {

        /**
         * 等待后台分段统计后写入成功终态，不阻塞调用方流。
         */
        public void complete(Integer inputTokens, Integer outputTokens, Integer totalTokens, Long firstTokenMs) {
            Instant finishedAt = Instant.now();
            breakdownFuture.handle((breakdown, exception) -> {
                Map<String, Object> attributes = new LinkedHashMap<>();
                if (breakdown != null) {
                    RagTokenBreakdown resolved = breakdown.withFinalPromptTokens(inputTokens);
                    attributes.put(RAG_TOKEN_STATUS, resolved.status().name());
                    putIfNotNull(attributes, RAG_INPUT_BUDGET_TOKENS, resolved.inputBudgetTokens());
                    putIfNotNull(attributes, RAG_RETRIEVAL_CANDIDATE_COUNT,
                            resolved.retrievalCandidateCount());
                    putIfNotNull(attributes, RAG_RETRIEVAL_ACCEPTED_COUNT,
                            resolved.retrievalAcceptedCount());
                    putIfNotNull(attributes, RAG_RETRIEVAL_SKIPPED_COUNT, resolved.retrievalSkippedCount());
                    putIfNotNull(attributes, RAG_SYSTEM_TOKENS, resolved.systemTokens());
                    putIfNotNull(attributes, RAG_SUMMARY_TOKENS, resolved.summaryTokens());
                    putIfNotNull(attributes, RAG_HISTORY_TOKENS, resolved.historyTokens());
                    putIfNotNull(attributes, RAG_QUESTION_TOKENS, resolved.questionTokens());
                    putIfNotNull(attributes, RAG_RETRIEVAL_TOKENS, resolved.retrievalTokens());
                    putIfNotNull(attributes, RAG_TOOL_TOKENS, resolved.toolTokens());
                    putIfNotNull(attributes, RAG_TEMPLATE_OVERHEAD_TOKENS,
                            resolved.templateOverheadTokens());
                }
                scope.finish(new LangfuseGenerationResult(inputTokens, outputTokens, totalTokens, firstTokenMs,
                        COMPLETED_STATE, attributes, finishedAt));
                return null;
            });
        }

        /** 以异常结束 Generation。 */
        public void fail(Throwable throwable) {
            scope.fail(throwable);
        }

        /** 以取消状态结束 Generation。 */
        public void cancel() {
            scope.close();
        }

        private static void putIfNotNull(Map<String, Object> attributes, String key, Object value) {
            if (value != null) {
                attributes.put(key, value);
            }
        }
    }
}
