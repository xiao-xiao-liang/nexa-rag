package com.nexarag.workflow.dispatcher.chat;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import com.nexarag.retrieval.config.RetrievalProperties;
import com.nexarag.retrieval.model.RetrievalChunk;
import com.nexarag.workflow.service.EvidenceQualityEvaluator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static com.nexarag.workflow.constants.ChatWorkflowNodeConstants.RERANK_NODE;
import static com.nexarag.workflow.constants.ChatWorkflowNodeConstants.SECTION_EXPANSION_NODE;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.EVIDENCE_EXPANSION_REASON;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.FUSED_RETRIEVAL_RESULTS;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.MAX_RETRIEVAL_ROUND;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.RETRIEVAL_ROUND;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.TRACE_ID;

/**
 * 检索融合路由器，负责判断候选质量并准备一次扩召参数。
 */
@Component
@Slf4j
public class RetrievalFusionDispatcher implements EdgeAction {

    private final RetrievalProperties retrievalProperties;
    private final EvidenceQualityEvaluator evidenceQualityEvaluator;

    @Autowired
    public RetrievalFusionDispatcher(RetrievalProperties retrievalProperties,
                                     EvidenceQualityEvaluator evidenceQualityEvaluator) {
        this.retrievalProperties = retrievalProperties;
        this.evidenceQualityEvaluator = evidenceQualityEvaluator;
    }

    /**
     * 兼容直接构造路由器的既有调用方。
     *
     * @param retrievalProperties 检索运行配置
     */
    public RetrievalFusionDispatcher(RetrievalProperties retrievalProperties) {
        this(retrievalProperties, new EvidenceQualityEvaluator(retrievalProperties));
    }

    /**
     * 根据融合候选和当前轮次选择章节扩展或重排序。
     *
     * @param state Graph 当前状态
     * @return 下一节点名称
     */
    @Override
    public String apply(OverAllState state) {
        List<RetrievalChunk> results = state.value(FUSED_RETRIEVAL_RESULTS, List.of());
        int round = state.value(RETRIEVAL_ROUND, 1);
        int maxRound = state.value(MAX_RETRIEVAL_ROUND, 2);
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
        log.info("检索候选触发章节扩展，traceId={}，候选数={}，nextRound={}，原因={}，正文上限={}，Token预算={}",
                state.value(TRACE_ID, ""), results.size(), round + 1, expansionReason,
                retrievalProperties.getCandidate().getExpansionEvidenceLimit(),
                retrievalProperties.getCandidate().getEvidenceTokenBudget());

        // 2. 返回章节扩展节点执行唯一一次受限正文补充
        return SECTION_EXPANSION_NODE;
    }
}
