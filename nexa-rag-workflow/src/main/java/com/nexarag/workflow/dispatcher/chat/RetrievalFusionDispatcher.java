package com.nexarag.workflow.dispatcher.chat;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import com.nexarag.retrieval.model.RetrievalChunk;
import com.nexarag.retrieval.enums.RetrievalScope;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static com.nexarag.workflow.constants.ChatWorkflowNodeConstants.RERANK_NODE;
import static com.nexarag.workflow.constants.ChatWorkflowNodeConstants.RETRIEVAL_NODE;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.FUSED_RETRIEVAL_RESULTS;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.MAX_RETRIEVAL_ROUND;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.RETRIEVAL_ROUND;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.RETRIEVAL_SCOPE;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.RETRIEVAL_TOP_K;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.RETRIEVAL_VECTOR_THRESHOLD;

/**
 * 检索融合路由器，负责判断候选质量并准备一次扩召参数。
 */
@Component
public class RetrievalFusionDispatcher implements EdgeAction {

    private static final int EXPANSION_FACTOR = 3;
    private static final double THRESHOLD_REDUCTION = 0.1D;

    /**
     * 根据融合候选和当前轮次选择扩召或重排序。
     *
     * @param state Graph 当前状态
     * @return 下一节点名称
     */
    @Override
    public String apply(OverAllState state) {
        List<RetrievalChunk> results = state.value(FUSED_RETRIEVAL_RESULTS, List.of());
        int round = state.value(RETRIEVAL_ROUND, 1);
        int maxRound = state.value(MAX_RETRIEVAL_ROUND, 2);
        if (!results.isEmpty() || round >= maxRound) {
            return RERANK_NODE;
        }

        // 1. 扩大候选集并放宽向量阈值
        int topK = state.value(RETRIEVAL_TOP_K, 10);
        double threshold = state.value(RETRIEVAL_VECTOR_THRESHOLD, 0.5D);
        state.updateState(Map.of(
                RETRIEVAL_ROUND, round + 1,
                RETRIEVAL_TOP_K, topK * EXPANSION_FACTOR,
                RETRIEVAL_VECTOR_THRESHOLD, Math.max(0D, threshold - THRESHOLD_REDUCTION),
                RETRIEVAL_SCOPE, RetrievalScope.INTENT_AND_GLOBAL));

        // 2. 返回检索节点执行唯一一次扩召
        return RETRIEVAL_NODE;
    }
}
