package com.nexarag.workflow.node.chat;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.nexarag.model.enums.ModelBizType;
import com.nexarag.model.gateway.ModelGateway;
import com.nexarag.model.gateway.rerank.RerankCandidate;
import com.nexarag.model.gateway.rerank.RerankModelRequest;
import com.nexarag.retrieval.model.RetrievalChunk;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.FUSED_RETRIEVAL_RESULTS;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.RERANKED_RETRIEVAL_RESULTS;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.REWRITTEN_QUESTION;

/**
 * 对话检索重排序节点，负责调用 Rerank 模型并截取最终候选。
 */
@Component
@RequiredArgsConstructor
public class RerankNode implements NodeAction {

    private static final int FINAL_TOP_K = 5;

    private final ModelGateway modelGateway;

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
        if (chunks.isEmpty()) {
            return Map.of(RERANKED_RETRIEVAL_RESULTS, List.of());
        }

        // 2. 调用重排序模型
        List<RerankCandidate> candidates = chunks.stream()
                .map(chunk -> new RerankCandidate(chunk.chunkId(), chunk.content(), Map.of()))
                .toList();
        var response = modelGateway.rerank(RerankModelRequest.builder()
                .traceId(UUID.randomUUID().toString())
                .bizType(ModelBizType.RERANK)
                .bizId("chat-rerank")
                .routeKey("rerank")
                .query(question)
                .candidates(candidates)
                .build());
        if (response == null || response.scores() == null) {
            return Map.of(RERANKED_RETRIEVAL_RESULTS, chunks.stream().limit(FINAL_TOP_K).toList());
        }

        // 3. 按模型分数排序并截取最终证据
        Map<String, Double> scores = response.scores().stream()
                .collect(Collectors.toMap(
                        com.nexarag.model.gateway.rerank.RerankModelResponse.RerankScore::id,
                        com.nexarag.model.gateway.rerank.RerankModelResponse.RerankScore::score,
                        Math::max));
        List<RetrievalChunk> rankedChunks = chunks.stream()
                .sorted(Comparator.comparingDouble(chunk -> -scores.getOrDefault(chunk.chunkId(), chunk.score())))
                .limit(FINAL_TOP_K)
                .toList();
        return Map.of(RERANKED_RETRIEVAL_RESULTS, rankedChunks);
    }
}
