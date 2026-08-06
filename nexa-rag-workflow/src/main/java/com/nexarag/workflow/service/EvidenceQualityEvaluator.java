package com.nexarag.workflow.service;

import com.nexarag.retrieval.config.RetrievalProperties;
import com.nexarag.retrieval.model.RetrievalChunk;
import com.nexarag.workflow.model.EvidenceQuality;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 回答证据质量评估器，统一判断是否需要章节扩展以及哪些原始正文可进入最终回答。
 */
@Component
public class EvidenceQualityEvaluator {

    private static final String NAVIGATION_CHANNEL = "SECTION_NAVIGATION";
    private static final int CHARACTERS_PER_TOKEN = 4;

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
     * 仅接纳原始正文，并在不截断正文的前提下遵守 Token 预算。
     *
     * @param rankedChunks 重排序结果
     * @return 可供回答使用的证据质量结果
     */
    public EvidenceQuality accept(List<RetrievalChunk> rankedChunks) {
        List<RetrievalChunk> bodies = bodyChunks(rankedChunks);
        if (bodies.isEmpty()) {
            return EvidenceQuality.insufficient("NO_RAW_BODY");
        }

        // 1. 只整体接纳未超过预算的原始正文，禁止截断或改写正文
        int tokenBudget = retrievalProperties.getCandidate().getEvidenceTokenBudget();
        int usedTokens = 0;
        List<RetrievalChunk> accepted = new ArrayList<>();
        for (RetrievalChunk body : bodies) {
            int bodyTokens = estimateTokens(body);
            if (bodyTokens > tokenBudget - usedTokens) {
                continue;
            }
            accepted.add(body);
            usedTokens += bodyTokens;
        }
        if (accepted.isEmpty()) {
            return EvidenceQuality.insufficient("TOKEN_BUDGET");
        }
        if (usedTokens < retrievalProperties.getCandidate().getExpansionMinimumBodyTokens()) {
            return EvidenceQuality.insufficient("TOO_SHORT_AFTER_EXPANSION");
        }
        return new EvidenceQuality(List.copyOf(accepted), true, "ACCEPTED", usedTokens);
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
        return NAVIGATION_CHANNEL.equals(chunk.channel());
    }

    private int estimateTokens(RetrievalChunk chunk) {
        return Math.max(1, (chunk.content().length() + CHARACTERS_PER_TOKEN - 1) / CHARACTERS_PER_TOKEN);
    }
}
