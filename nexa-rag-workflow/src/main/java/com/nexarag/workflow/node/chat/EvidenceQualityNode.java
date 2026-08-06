package com.nexarag.workflow.node.chat;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.nexarag.retrieval.model.RetrievalChunk;
import com.nexarag.workflow.model.EvidenceQuality;
import com.nexarag.workflow.service.EvidenceQualityEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.ACCEPTED_EVIDENCE_RESULTS;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.EVIDENCE_QUALITY;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.RERANKED_RETRIEVAL_RESULTS;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.TRACE_ID;

/**
 * 证据质量节点，仅接纳预算内的原始正文；证据不足时清空上下文以触发现有的保守拒答提示词。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class EvidenceQualityNode implements NodeAction {

    private final EvidenceQualityEvaluator evidenceQualityEvaluator;

    /**
     * 评估重排序后的候选并输出最终可回答证据。
     *
     * @param state Workflow 当前状态
     * @return 已接纳正文及质量判定
     */
    @Override
    public Map<String, Object> apply(OverAllState state) {
        // 1. 评估重排序候选，导航记录和超预算正文均不能进入回答上下文
        List<RetrievalChunk> rankedChunks = state.value(RERANKED_RETRIEVAL_RESULTS, List.of());
        EvidenceQuality quality = evidenceQualityEvaluator.accept(rankedChunks);

        // 2. 不足时返回空正文，沿用回答提示词中的“现有资料不足”拒答路径
        log.info("回答证据判定完成，traceId={}，候选数={}，接纳正文数={}，接纳片段ID={}，估算Token数={}，充分={}，原因={}",
                state.value(TRACE_ID, ""), rankedChunks.size(), quality.acceptedChunks().size(),
                quality.acceptedChunks().stream().map(RetrievalChunk::chunkId).toList(), quality.estimatedTokenCount(),
                quality.sufficient(), quality.reason());
        return Map.of(ACCEPTED_EVIDENCE_RESULTS, quality.acceptedChunks(), EVIDENCE_QUALITY, quality);
    }
}
