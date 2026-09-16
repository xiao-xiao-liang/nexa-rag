package com.nexarag.workflow.node.chat;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.nexarag.infra.observability.langfuse.aop.LangfuseSpan;
import com.nexarag.infra.observability.langfuse.model.LangfuseObservationType;
import com.nexarag.retrieval.model.RetrievalChunk;
import com.nexarag.workflow.model.EvidenceQuality;
import com.nexarag.workflow.service.EvidenceQualityEvaluator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.ACCEPTED_EVIDENCE_RESULTS;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.EVIDENCE_QUALITY;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.RERANKED_RETRIEVAL_RESULTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 证据质量节点测试。
 */
class EvidenceQualityNodeTest {

    @Test
    void applyShouldReturnAcceptedEvidenceAndQuality() {
        RetrievalChunk chunk = new RetrievalChunk("chunk-1", 1L, 1, null, null, null,
                "命中正文", 0.8D, "VECTOR", 1);
        EvidenceQuality quality = new EvidenceQuality(List.of(chunk), true, "ACCEPTED", 2);
        EvidenceQualityEvaluator evaluator = mock(EvidenceQualityEvaluator.class);
        when(evaluator.accept(List.of(chunk))).thenReturn(quality);

        Map<String, Object> result = new EvidenceQualityNode(evaluator)
                .apply(new OverAllState(Map.of(RERANKED_RETRIEVAL_RESULTS, List.of(chunk))));

        assertThat(result).containsEntry(ACCEPTED_EVIDENCE_RESULTS, List.of(chunk))
                .containsEntry(EVIDENCE_QUALITY, quality);
    }

    @Test
    void applyShouldDeclareEvidenceEvaluatorSpan() throws NoSuchMethodException {
        LangfuseSpan span = EvidenceQualityNode.class.getMethod("apply", OverAllState.class)
                .getAnnotation(LangfuseSpan.class);

        assertThat(span.name()).isEqualTo("rag.evidence-selection");
        assertThat(span.type()).isEqualTo(LangfuseObservationType.EVALUATOR);
        assertThat(span.attributes()).contains("nexa.generation_id=#state.value('generationId', '')",
                "nexa.evidence.candidate_count=#state.value('rerankedRetrievalResults', T(java.util.List).of()).size()");
        assertThat(span.resultAttributes()).containsExactlyInAnyOrder(
                "nexa.evidence.accepted_count=#result['evidenceQuality'].acceptedChunks().size()",
                "nexa.evidence.estimated_tokens=#result['evidenceQuality'].estimatedTokenCount()",
                "nexa.evidence.sufficient=#result['evidenceQuality'].sufficient()");
    }
}
