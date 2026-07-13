package com.nexarag.workflow.node.chat;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.nexarag.retrieval.model.RetrievalChunk;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.FUSED_RETRIEVAL_RESULTS;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.RAW_RETRIEVAL_RESULTS;

/**
 * 检索融合节点，负责按片段去重并计算 RRF 分数。
 */
@Component
public class RetrievalFusionNode implements NodeAction {
    @Override
    public Map<String, Object> apply(OverAllState state) {
        List<RetrievalChunk> chunks = state.value(RAW_RETRIEVAL_RESULTS, List.of());
        Map<String, RetrievalChunk> unique = new LinkedHashMap<>();
        Map<String, Double> scores = new LinkedHashMap<>();
        for (RetrievalChunk chunk : chunks) {
            String key = chunk.chunkId() == null ? chunk.documentId() + ":" + chunk.chunkIndex() : chunk.chunkId();
            unique.putIfAbsent(key, chunk);
            scores.merge(key, 1D / (60D + chunk.rank() + 1D), Double::sum);
        }
        List<RetrievalChunk> fused = unique.entrySet().stream()
                .sorted(Comparator.comparingDouble(entry -> -scores.get(entry.getKey())))
                .map(Map.Entry::getValue)
                .toList();
        return Map.of(FUSED_RETRIEVAL_RESULTS, fused);
    }
}
