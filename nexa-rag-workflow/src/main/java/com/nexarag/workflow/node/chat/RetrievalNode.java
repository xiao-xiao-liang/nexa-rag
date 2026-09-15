package com.nexarag.workflow.node.chat;

import cn.dev33.satoken.exception.SaTokenContextException;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.nexarag.common.exception.ClientException;
import com.nexarag.infra.observability.langfuse.LangfuseSpanScope;
import com.nexarag.infra.observability.langfuse.LangfuseTelemetry;
import com.nexarag.infra.observability.langfuse.model.LangfuseObservationType;
import com.nexarag.infra.observability.langfuse.otel.LangfuseOtelContextCodec;
import com.nexarag.retrieval.config.RetrievalProperties;
import com.nexarag.retrieval.dto.req.ConversationRetrievalRequest;
import com.nexarag.retrieval.dto.res.IntentRecognitionResult;
import com.nexarag.retrieval.enums.RetrievalScope;
import com.nexarag.retrieval.model.RetrievalChunk;
import com.nexarag.retrieval.service.ConversationRetrievalService;
import com.nexarag.workflow.stream.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static com.nexarag.infra.observability.langfuse.constants.LangfuseOtelAttributeConstant.GENERATION_ID_ATTRIBUTE;
import static com.nexarag.workflow.constants.ChatWorkflowExecutionConstant.INITIAL_RETRIEVAL_ROUND;
import static com.nexarag.workflow.constants.ChatWorkflowExecutionConstant.RETRIEVAL_MAX_ATTEMPTS;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.*;
import static com.nexarag.workflow.constants.ChatWorkflowSystemToolConstants.KNOWLEDGE_SEARCH_SEQUENCE;
import static com.nexarag.workflow.constants.ChatWorkflowSystemToolConstants.KNOWLEDGE_SEARCH_TOOL_NAME;
import static com.nexarag.workflow.constants.ChatWorkflowTelemetryConstant.*;

/**
 * 对话检索节点，负责按当前轮次参数调用混合检索服务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetrievalNode implements NodeAction {

    private final ConversationRetrievalService retrievalService;
    private final RetrievalProperties retrievalProperties;
    private final ChatGenerationEventPublisher eventPublisher;
    private final LangfuseTelemetry telemetry;

    @Override
    public Map<String, Object> apply(OverAllState state) {
        var request = new ConversationRetrievalRequest(
                state.value(REWRITTEN_QUESTION, ""),
                state.value(INTENT_RESULT, new IntentRecognitionResult(java.util.List.of(), 0D)),
                state.value(RETRIEVAL_SCOPE, RetrievalScope.INTENT),
                state.value(RETRIEVAL_TOP_K, retrievalProperties.getCandidate().getVectorCandidateLimit()),
                state.value(RETRIEVAL_VECTOR_THRESHOLD, retrievalProperties.getCandidate().getCoarseScoreFloor()),
                state.value(RETRIEVAL_ROUND, INITIAL_RETRIEVAL_ROUND),
                requireTenantId(state.value(TENANT_ID, "")),
                state.value(RETRIEVAL_KNOWLEDGE_BASE_IDS, List.of()));
        ChatGenerationAccumulator accumulator = state.value(GENERATION_ACCUMULATOR,
                new ChatGenerationAccumulator());
        String generationId = state.value(GENERATION_ID, "");
        LangfuseSpanScope span = telemetry.startSpan(RETRIEVAL_SPAN_NAME, LangfuseObservationType.RETRIEVER, Map.of(
                        GENERATION_ID_ATTRIBUTE, generationId,
                        RETRIEVAL_ROUND_ATTRIBUTE, request.round(),
                        RETRIEVAL_TOP_K_ATTRIBUTE, request.topK(),
                        RETRIEVAL_VECTOR_THRESHOLD_ATTRIBUTE, request.vectorThreshold()),
                LangfuseOtelContextCodec.decode(state.value(
                        com.nexarag.workflow.constants.ChatWorkflowStateKeys.LANGFUSE_OTEL_CONTEXT_CARRIER,
                        "")));
        try {
            ChatToolOperationDTO runningOperation = new ChatToolOperationDTO(generationId + RETRIEVAL_OPERATION_ID_SUFFIX,
                    generationId, KNOWLEDGE_SEARCH_SEQUENCE, KNOWLEDGE_SEARCH_TOOL_NAME, ChatToolOperationStatus.RUNNING);
            accumulator.upsertOperation(runningOperation);
            publishSnapshot(state, accumulator);

            List<RetrievalChunk> results;
            String failureSummary = null;
            try {
                results = retrieveWithRetry(request, generationId);
                accumulator.upsertOperation(new ChatToolOperationDTO(runningOperation.opId(), runningOperation.processId(),
                        runningOperation.sequence(), runningOperation.name(), ChatToolOperationStatus.SUCCESS));
            } catch (RuntimeException exception) {
                if (!isRetryable(exception)) {
                    throw exception;
                }
                results = List.of();
                failureSummary = "知识库检索暂时不可用，已基于现有上下文继续回答";
                accumulator.upsertOperation(new ChatToolOperationDTO(runningOperation.opId(), runningOperation.processId(),
                        runningOperation.sequence(), runningOperation.name(), ChatToolOperationStatus.FAILED));
                log.warn("知识库检索重试耗尽，traceId={}，generationId={}", state.value(TRACE_ID, ""), generationId,
                        exception);
            }
            publishSnapshot(state, accumulator);
            // 1. 仅记录检索范围和命中数量，不记录查询词或片段正文
            log.info("知识库检索完成，traceId={}，当前检索轮次={}，scope={}，topK={}，相似度阈值={}，检索数={}",
                    state.value(TRACE_ID, ""), request.round(), request.scope(), request.topK(),
                    request.vectorThreshold(), results.size());
            span.addAttributes(Map.of(RETRIEVAL_RESULT_COUNT_ATTRIBUTE, results.size(),
                    RETRIEVAL_DEGRADED_ATTRIBUTE, failureSummary != null));
            return failureSummary == null
                    ? Map.of(RAW_RETRIEVAL_RESULTS, results)
                    : Map.of(RAW_RETRIEVAL_RESULTS, results, TOOL_FAILURE_SUMMARIES, List.of(failureSummary));
        } catch (RuntimeException exception) {
            span.fail(exception);
            throw exception;
        } finally {
            span.close();
        }
    }

    private List<RetrievalChunk> retrieveWithRetry(ConversationRetrievalRequest request, String generationId) {
        RuntimeException lastException = null;
        for (int attempt = INITIAL_RETRIEVAL_ROUND; attempt <= RETRIEVAL_MAX_ATTEMPTS; attempt++) {
            try {
                return retrievalService.retrieve(request);
            } catch (RuntimeException exception) {
                if (!isRetryable(exception)) {
                    throw exception;
                }
                lastException = exception;
                log.warn("知识库检索失败，将重试，generationId={}，attempt={}", generationId, attempt, exception);
            }
        }
        throw lastException;
    }

    /**
     * 仅对可能自行恢复的基础设施异常重试；认证上下文与请求参数错误必须立即失败。
     */
    private boolean isRetryable(RuntimeException exception) {
        return !(exception instanceof SaTokenContextException
                || exception instanceof ClientException
                || exception instanceof IllegalArgumentException
                || exception instanceof IllegalStateException);
    }

    private String requireTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("对话工作流缺少可信租户ID");
        }
        return tenantId;
    }

    private void publishSnapshot(OverAllState state, ChatGenerationAccumulator accumulator) {
        eventPublisher.publish(new ChatStreamEvent(ChatStreamEventType.SNAPSHOT, null,
                state.value(CONVERSATION_ID, ""), state.value(TRACE_ID, ""), state.value(GENERATION_ID, ""),
                state.value(ASSISTANT_MESSAGE_ID, ""), null, null, 0L, accumulator.operationsSnapshot()));
    }
}
