package com.nexarag.workflow.dispatcher.chat;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import com.nexarag.retrieval.config.RetrievalProperties;
import com.nexarag.retrieval.model.RetrievalChunk;
import com.nexarag.workflow.service.EvidenceQualityEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static com.nexarag.workflow.constants.ChatWorkflowExecutionConstant.DEFAULT_MAX_RETRIEVAL_ROUND;
import static com.nexarag.workflow.constants.ChatWorkflowExecutionConstant.INITIAL_RETRIEVAL_ROUND;
import static com.nexarag.workflow.constants.ChatWorkflowNodeConstants.RERANK_NODE;
import static com.nexarag.workflow.constants.ChatWorkflowNodeConstants.SECTION_EXPANSION_NODE;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.*;

/**
 * 检索融合路由器，负责判断候选质量并准备一次扩召参数。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RetrievalFusionDispatcher implements EdgeAction {

    private final RetrievalProperties retrievalProperties;
    private final EvidenceQualityEvaluator evidenceQualityEvaluator;

    /**
     * 根据融合候选和当前轮次选择章节扩展或重排序。
     *
     * @param state Graph 当前状态
     * @return 下一节点名称
     */
    @Override
    public String apply(OverAllState state) {
        List<RetrievalChunk> results = state.value(FUSED_RETRIEVAL_RESULTS, List.of());
        int round = state.value(RETRIEVAL_ROUND, INITIAL_RETRIEVAL_ROUND);
        int maxRound = state.value(MAX_RETRIEVAL_ROUND, DEFAULT_MAX_RETRIEVAL_ROUND);
        String expansionReason = evidenceQualityEvaluator.expansionReason(results);
        if ("READY".equals(expansionReason) || round >= maxRound) {
            log.info("检索候选不触发章节扩展，traceId={}，候选数={}，轮次={}，原因={}",
                    state.value(TRACE_ID, ""), results.size(), round,
                    round >= maxRound ? "MAX_ROUND_REACHED" : expansionReason);
            return RERANK_NODE;
        }

        // 1. 记录扩展原因并增加轮次，避免同一请求循环扩展
        state.updateState(Map.of(
                RETRIEVAL_ROUND, round + 1,
                EVIDENCE_EXPANSION_REASON, expansionReason));
        log.info("检索候选触发章节扩展，traceId={}，候选数={}，nextRound={}，原因={}，正文上限={}",
                state.value(TRACE_ID, ""), results.size(), round + 1, expansionReason,
                retrievalProperties.getCandidate().getExpansionEvidenceLimit());

        // 2. 返回章节扩展节点执行唯一一次受限正文补充
        return SECTION_EXPANSION_NODE;
    }
}
