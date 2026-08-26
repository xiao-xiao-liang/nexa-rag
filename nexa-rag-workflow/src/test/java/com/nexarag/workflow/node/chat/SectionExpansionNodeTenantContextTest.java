package com.nexarag.workflow.node.chat;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.nexarag.document.service.KnowledgeBaseService;
import com.nexarag.retrieval.model.RetrievalChunk;
import com.nexarag.retrieval.retriever.SectionExpansionRetriever;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.FUSED_RETRIEVAL_RESULTS;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.RETRIEVAL_KNOWLEDGE_BASE_IDS;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.REWRITTEN_QUESTION;
import static com.nexarag.workflow.constants.ChatWorkflowStateKeys.TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 章节扩展节点的租户上下文测试。
 */
class SectionExpansionNodeTenantContextTest {

    @Test
    void applyShouldUseTenantFromWorkflowStateForDocumentFiltering() {
        SectionExpansionRetriever retriever = mock(SectionExpansionRetriever.class);
        KnowledgeBaseService knowledgeBaseService = mock(KnowledgeBaseService.class);
        RetrievalChunk chunk = new RetrievalChunk("chunk-001", 1L, null, null, null, null,
                "正文", 1D, "SECTION_EXPANSION", 1);
        when(knowledgeBaseService.validateRequestedKnowledgeBases("tenant-001", List.of()))
                .thenReturn(Set.of());
        when(retriever.retrieve("退款规则")).thenReturn(List.of(chunk));
        when(knowledgeBaseService.filterDocumentIdsInTenantScope("tenant-001", List.of(1L), Set.of()))
                .thenReturn(Set.of(1L));

        Map<String, Object> result = new SectionExpansionNode(retriever, knowledgeBaseService)
                .apply(new OverAllState(Map.of(TENANT_ID, "tenant-001", REWRITTEN_QUESTION, "退款规则",
                        RETRIEVAL_KNOWLEDGE_BASE_IDS, List.of(), FUSED_RETRIEVAL_RESULTS, List.of())));

        assertThat(result.get(FUSED_RETRIEVAL_RESULTS)).isEqualTo(List.of(chunk));
        verify(knowledgeBaseService).validateRequestedKnowledgeBases("tenant-001", List.of());
        verify(knowledgeBaseService).filterDocumentIdsInTenantScope("tenant-001", List.of(1L), Set.of());
    }
}
