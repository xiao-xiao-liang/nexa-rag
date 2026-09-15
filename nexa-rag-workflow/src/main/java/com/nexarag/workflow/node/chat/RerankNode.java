package com.nexarag.workflow.node.chat;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.nexarag.infra.observability.langfuse.LangfuseSpanScope;
import com.nexarag.infra.observability.langfuse.LangfuseTelemetry;
import com.nexarag.infra.observability.langfuse.model.LangfuseObservationType;
import com.nexarag.infra.observability.langfuse.otel.LangfuseOtelContextCodec;
import com.nexarag.model.enums.ModelBizType;
import com.nexarag.model.gateway.ModelGateway;
import com.nexarag.model.gateway.rerank.RerankCandidate;
import com.nexarag.model.gateway.rerank.RerankModelRequest;
import com.nexarag.model.gateway.rerank.RerankModelResponse;
import com.nexarag.retrieval.config.RetrievalProperties;
import com.nexarag.retrieval.model.RetrievalChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.nexarag.infra.observability.langfuse.constants.LangfuseOtelAttributeConstant.GENERATION_ID_ATTRIBUTE;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.*;
import static com.nexarag.workflow.constants.ChatWorkflowTelemetryConstant.*;

/**
 * 对话检索重排序节点，负责调用 Rerank 模型并截取最终候选。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RerankNode implements NodeAction {

    private final ModelGateway modelGateway;
    private final RetrievalProperties retrievalProperties;
    private final LangfuseTelemetry telemetry;

    /**
     * 对融合候选执行重排序，空候选直接短路。
     *
     * @param state Workflow 当前状态
     * @return 包含重排序结果的状态增量
     */
    @Override
    public Map<String, Object> apply(OverAllState state) {
        // 1. 读取问题和融合候选
        String question = state.value(REWRITTEN_QUESTION, "");
        List<RetrievalChunk> chunks = state.value(FUSED_RETRIEVAL_RESULTS, List.of());
        LangfuseSpanScope span = telemetry.startSpan(RERANK_SPAN_NAME, LangfuseObservationType.EVALUATOR, Map.of(
                        GENERATION_ID_ATTRIBUTE, state.value(GENERATION_ID, ""),
                        RERANK_CANDIDATE_COUNT_ATTRIBUTE, chunks.size(),
                        RERANK_ACCEPTED_THRESHOLD_ATTRIBUTE, retrievalProperties.getCandidate().getAcceptedRerankScore()),
                LangfuseOtelContextCodec.decode(state.value(LANGFUSE_OTEL_CONTEXT_CARRIER, "")));
        try {
            if (chunks.isEmpty()) {
                log.info("召回结果为空，跳过重排序，traceId={}", state.value(TRACE_ID, ""));
                span.addAttributes(Map.of(RERANK_SKIPPED_ATTRIBUTE, true));
                return Map.of(RERANKED_RETRIEVAL_RESULTS, List.of());
            }

            // 2. 调用重排序模型
            List<RerankCandidate> candidates = chunks.stream()
                    .map(chunk -> new RerankCandidate(chunk.chunkId(), chunk.content(), Map.of()))
                    .toList();
            var response = modelGateway.rerank(RerankModelRequest.builder()
                    .traceId(state.value(TRACE_ID, ""))
                    .bizType(ModelBizType.RERANK)
                    .bizId(RERANK_BIZ_ID)
                    .routeKey(RERANK_ROUTE_KEY)
                    .query(question)
                    .candidates(candidates)
                    .langfuseContext(span.context())
                    .build());
            if (response == null || response.scores() == null) {
                List<RetrievalChunk> fallbackChunks = chunks.stream()
                        .limit(retrievalProperties.getCandidate().getRerankCandidateLimit())
                        .toList();
                log.warn("检索重排序未返回分数，使用融合排序结果，traceId={}，候选数={}，保留数={}",
                        state.value(TRACE_ID, ""), chunks.size(), fallbackChunks.size());
                span.addAttributes(Map.of(RERANK_ACCEPTED_COUNT_ATTRIBUTE, fallbackChunks.size(),
                        RERANK_FALLBACK_ATTRIBUTE, true));
                return Map.of(RERANKED_RETRIEVAL_RESULTS, fallbackChunks);
            }

            // 3. 按模型分数排序并截取最终证据
            Map<String, Double> scores = response.scores().stream()
                    .collect(Collectors.toMap(RerankModelResponse.RerankScore::id, RerankModelResponse.RerankScore::score, Math::max));
            List<RetrievalChunk> rankedChunks = chunks.stream()
                    .sorted(Comparator.comparingDouble(chunk -> -scores.getOrDefault(chunk.chunkId(), chunk.score())))
                    .filter(chunk -> scores.getOrDefault(chunk.chunkId(), chunk.score()) >= retrievalProperties.getCandidate().getAcceptedRerankScore())
                    .limit(retrievalProperties.getCandidate().getRerankCandidateLimit())
                    .toList();
            log.info("重排序完成，traceId={}，输入候选数={}，通过阈值并保留数={}",
                    state.value(TRACE_ID, ""), chunks.size(), rankedChunks.size());
            span.addAttributes(Map.of(RERANK_ACCEPTED_COUNT_ATTRIBUTE, rankedChunks.size(),
                    RERANK_FALLBACK_ATTRIBUTE, false));
            return Map.of(RERANKED_RETRIEVAL_RESULTS, rankedChunks);
        } catch (RuntimeException exception) {
            span.fail(exception);
            throw exception;
        } finally {
            span.close();
        }
    }
}
