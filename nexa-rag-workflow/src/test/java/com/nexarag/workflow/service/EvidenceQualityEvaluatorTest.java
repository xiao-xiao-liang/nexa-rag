package com.nexarag.workflow.service;

import com.nexarag.retrieval.config.RetrievalProperties;
import com.nexarag.retrieval.model.RetrievalChunk;
import com.nexarag.workflow.model.EvidenceQuality;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 回答证据质量评估器测试。
 */
class EvidenceQualityEvaluatorTest {

    @Test
    void acceptShouldKeepMatchedShortBodyAfterExpansion() {
        RetrievalProperties properties = new RetrievalProperties();
        properties.getCandidate().setExpansionMinimumBodyTokens(64);
        EvidenceQualityEvaluator evaluator = new EvidenceQualityEvaluator(properties);
        RetrievalChunk shortBody = new RetrievalChunk("chunk-1", 1L, 0, null, "测试文档", "知识库",
                "VERSION_ONE_ONLY_TOKEN 对应的版本一标识。", 0.9D, "BM25", 1, 101L);

        EvidenceQuality quality = evaluator.accept(List.of(shortBody));

        assertThat(quality.acceptedChunks()).containsExactly(shortBody);
        assertThat(quality.sufficient()).isTrue();
        assertThat(quality.reason()).isEqualTo("SHORT_BODY_ACCEPTED");
    }
}
