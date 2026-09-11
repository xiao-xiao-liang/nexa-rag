package com.nexarag.workflow.service;

import com.nexarag.retrieval.config.RetrievalProperties;
import com.nexarag.retrieval.model.RetrievalChunk;
import com.nexarag.workflow.model.EvidenceQuality;
import com.nexarag.workflow.constants.WorkflowConstants;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 回答证据质量评估器，统一判断是否需要章节扩展以及哪些正文可进入最终回答。
 */
@Component
public class EvidenceQualityEvaluator {

    private final RetrievalProperties retrievalProperties;

    public EvidenceQualityEvaluator(RetrievalProperties retrievalProperties) {
        this.retrievalProperties = retrievalProperties;
    }

    /**
     * 评估初始召回结果是否需要通过章节范围补充正文。
     *
     * @param chunks 融合后的初始召回结果
     * @return READY 表示不需要扩展，其余值为扩展原因
     */
    public String expansionReason(List<RetrievalChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "EMPTY";
        }
        List<RetrievalChunk> bodyChunks = bodyChunks(chunks);
        if (bodyChunks.isEmpty()) {
            return "NAVIGATION_ONLY";
        }
        int estimatedTokens = bodyChunks.stream().mapToInt(this::estimateTokens).sum();
        if (estimatedTokens < retrievalProperties.getCandidate().getExpansionMinimumBodyTokens()) {
            return "TOO_SHORT";
        }
        double highestScore = bodyChunks.stream().mapToDouble(RetrievalChunk::score).max().orElse(0D);
        if (highestScore < retrievalProperties.getCandidate().getExpansionConfidenceThreshold()) {
            return "LOW_CONFIDENCE";
        }
        return "READY";
    }

    /**
     * 接纳全部正文。模型输入窗口由最终回答节点按照实际模型路由统一控制，
     * 此处不得以固定全局预算提前丢弃完整父片段。
     *
     * @param rankedChunks 重排序结果
     * @return 可供回答使用的证据质量结果
     */
    public EvidenceQuality accept(List<RetrievalChunk> rankedChunks) {
        List<RetrievalChunk> bodies = bodyChunks(rankedChunks);
        if (bodies.isEmpty()) {
            return EvidenceQuality.insufficient("NO_RAW_BODY");
        }

        int estimatedTokens = bodies.stream().mapToInt(this::estimateTokens).sum();
        // 短正文只用于触发章节扩展，扩展后仍无更多正文时不能丢弃已经命中的原始证据。
        String reason = estimatedTokens < retrievalProperties.getCandidate().getExpansionMinimumBodyTokens()
                ? "SHORT_BODY_ACCEPTED" : "ACCEPTED";
        return new EvidenceQuality(List.copyOf(bodies), true, reason, estimatedTokens);
    }

    private List<RetrievalChunk> bodyChunks(List<RetrievalChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        return chunks.stream()
                .filter(chunk -> chunk != null && !isNavigation(chunk) && StringUtils.hasText(chunk.content()))
                .toList();
    }

    private boolean isNavigation(RetrievalChunk chunk) {
        return WorkflowConstants.SECTION_NAVIGATION_CHANNEL.equals(chunk.channel());
    }

    private int estimateTokens(RetrievalChunk chunk) {
        return Math.max(1, (chunk.content().length() + WorkflowConstants.CHARACTERS_PER_TOKEN - 1)
                / WorkflowConstants.CHARACTERS_PER_TOKEN);
    }
}
