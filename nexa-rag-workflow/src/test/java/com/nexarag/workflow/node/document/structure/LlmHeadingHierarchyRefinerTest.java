package com.nexarag.workflow.node.document.structure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.document.enums.HeadingEvidenceSource;
import com.nexarag.document.model.bo.structure.HeadingEvidenceBO;
import com.nexarag.infra.config.DocumentStructureProperties;
import com.nexarag.model.gateway.ModelGateway;
import com.nexarag.model.gateway.chat.ChatModelResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** LLM 标题层级精修器测试。 */
class LlmHeadingHierarchyRefinerTest {

    @Test
    void refineShouldApplyValidatedDecisionToLowConfidenceHeadingOnly() {
        ModelGateway modelGateway = mock(ModelGateway.class);
        when(modelGateway.chat(any())).thenReturn(ChatModelResponse.builder()
                .content("[{\"sequence\":2,\"level\":2,\"confidence\":0.93}]").build());
        LlmHeadingHierarchyRefiner refiner = new LlmHeadingHierarchyRefiner(modelGateway, new ObjectMapper(), properties());
        List<HeadingEvidenceBO> headings = List.of(
                new HeadingEvidenceBO("第一章", 1, 1, HeadingEvidenceSource.MARKDOWN, 1.0D, null),
                new HeadingEvidenceBO("未编号小节", 1, 2, HeadingEvidenceSource.PDF_LAYOUT, 0.75D, 3));

        List<HeadingEvidenceBO> refined = refiner.refine(100L, headings);

        assertThat(refined).containsExactly(
                new HeadingEvidenceBO("第一章", 1, 1, HeadingEvidenceSource.MARKDOWN, 1.0D, null),
                new HeadingEvidenceBO("未编号小节", 2, 2, HeadingEvidenceSource.LLM, 0.93D, 3));
        verify(modelGateway).chat(any());
    }

    @Test
    void refineShouldIgnoreInvalidDecisionAndKeepOriginalHeading() {
        ModelGateway modelGateway = mock(ModelGateway.class);
        when(modelGateway.chat(any())).thenReturn(ChatModelResponse.builder()
                .content("[{\"sequence\":2,\"level\":7,\"confidence\":0.99},"
                        + "{\"sequence\":99,\"level\":2,\"confidence\":0.96}]").build());
        LlmHeadingHierarchyRefiner refiner = new LlmHeadingHierarchyRefiner(modelGateway, new ObjectMapper(), properties());
        List<HeadingEvidenceBO> headings = List.of(
                new HeadingEvidenceBO("未编号小节", 1, 2, HeadingEvidenceSource.PDF_LAYOUT, 0.75D, 3));

        assertThat(refiner.refine(100L, headings)).containsExactlyElementsOf(headings);
    }

    @Test
    void refineShouldKeepOriginalHeadingsWhenDecisionCreatesLevelJump() {
        ModelGateway modelGateway = mock(ModelGateway.class);
        when(modelGateway.chat(any())).thenReturn(ChatModelResponse.builder()
                .content("[{\"sequence\":2,\"level\":4,\"confidence\":0.95}]").build());
        LlmHeadingHierarchyRefiner refiner = new LlmHeadingHierarchyRefiner(modelGateway, new ObjectMapper(), properties());
        List<HeadingEvidenceBO> headings = List.of(
                new HeadingEvidenceBO("第一章", 1, 1, HeadingEvidenceSource.MARKDOWN, 1.0D, null),
                new HeadingEvidenceBO("未编号小节", 2, 2, HeadingEvidenceSource.PDF_LAYOUT, 0.75D, 3));

        assertThat(refiner.refine(100L, headings)).containsExactlyElementsOf(headings);
    }

    private DocumentStructureProperties properties() {
        DocumentStructureProperties properties = new DocumentStructureProperties();
        properties.getLlmFallback().setRouteKey("chat");
        properties.getLlmFallback().setCandidateMaxConfidence(0.80D);
        properties.getLlmFallback().setAcceptedMinConfidence(0.85D);
        properties.getLlmFallback().setMaxCandidates(20);
        return properties;
    }
}
