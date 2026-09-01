package com.nexarag.retrieval.retriever;

import com.nexarag.document.model.entity.DocumentChunk;
import com.nexarag.document.service.DocumentChunkService;
import com.nexarag.retrieval.config.RetrievalProperties;
import com.nexarag.retrieval.model.RetrievalChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** 父子片段上下文扩展器测试。 */
class ParentContextExpansionRetrieverTest {

    @Test
    void expandShouldUseFullParentWhenParentFitsConfiguredLimit() {
        DocumentChunkService documentChunkService = mock(DocumentChunkService.class);
        DocumentChunk parent = chunk("parent_1", null, 1, 101L, "完整父片段正文");
        when(documentChunkService.listByIds(any())).thenReturn(List.of(parent));
        when(documentChunkService.listByParentChunkIds(List.of("parent_1"))).thenReturn(List.of());
        ParentContextExpansionRetriever retriever = new ParentContextExpansionRetriever(documentChunkService,
                new RetrievalProperties());

        List<RetrievalChunk> expanded = retriever.expand(List.of(hit("child_1", "parent_1", 2, 101L, "命中子片段")));

        assertThat(expanded).singleElement().satisfies(chunk -> {
            assertThat(chunk.chunkId()).isEqualTo("parent_1");
            assertThat(chunk.content()).isEqualTo("完整父片段正文");
            assertThat(chunk.channel()).isEqualTo(ParentContextExpansionRetriever.PARENT_CONTEXT_CHANNEL);
            assertThat(chunk.documentVersionId()).isEqualTo(101L);
        });
    }

    @Test
    void expandShouldKeepHitAndAppendNeighborsWhenParentExceedsBudget() {
        DocumentChunkService documentChunkService = mock(DocumentChunkService.class);
        RetrievalProperties properties = new RetrievalProperties();
        properties.getCandidate().setParentContextFullParentMaxTokens(100);
        properties.getCandidate().setEvidenceTokenBudget(500);
        DocumentChunk parent = chunk("parent_1", null, 1, 101L, "父正文".repeat(1001));
        List<DocumentChunk> siblings = List.of(
                chunk("child_0", "parent_1", 2, 101L, "前文"),
                chunk("child_1", "parent_1", 3, 101L, "命中"),
                chunk("child_2", "parent_1", 4, 101L, "后文"),
                chunk("child_history", "parent_1", 5, 102L, "历史版本后文"));
        when(documentChunkService.listByIds(any())).thenReturn(List.of(parent));
        when(documentChunkService.listByParentChunkIds(List.of("parent_1"))).thenReturn(siblings);
        ParentContextExpansionRetriever retriever = new ParentContextExpansionRetriever(documentChunkService, properties);

        List<RetrievalChunk> expanded = retriever.expand(List.of(hit("child_1", "parent_1", 3, 101L, "命中")));

        assertThat(expanded).extracting(RetrievalChunk::chunkId)
                .containsExactly("child_1", "child_0", "child_2");
        assertThat(expanded.get(1).channel()).isEqualTo(ParentContextExpansionRetriever.PARENT_NEIGHBOR_CHANNEL);
        assertThat(expanded).allMatch(chunk -> Long.valueOf(101L).equals(chunk.documentVersionId()));
    }

    private DocumentChunk chunk(String chunkId, String parentChunkId, int chunkOrder, Long documentVersionId, String text) {
        return DocumentChunk.builder()
                .chunkId(chunkId)
                .parentChunkId(parentChunkId)
                .documentId(1L)
                .documentVersionId(documentVersionId)
                .chunkOrder(chunkOrder)
                .text(text)
                .build();
    }

    private RetrievalChunk hit(String chunkId, String parentChunkId, int chunkOrder, Long documentVersionId, String content) {
        return new RetrievalChunk(chunkId, 1L, chunkOrder, parentChunkId, "Java 集合", "知识库", content,
                0.9D, "VECTOR", 1, documentVersionId);
    }
}
