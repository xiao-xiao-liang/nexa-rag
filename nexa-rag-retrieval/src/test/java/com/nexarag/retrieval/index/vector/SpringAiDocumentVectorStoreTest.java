package com.nexarag.retrieval.index.vector;

import com.nexarag.retrieval.config.RetrievalProperties;
import com.nexarag.retrieval.model.IndexableChunk;
import com.nexarag.retrieval.model.VectorIndexSearchResult;
import com.nexarag.retrieval.model.VectorIndexWriteResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SpringAiDocumentVectorStore 单元测试。
 */
class SpringAiDocumentVectorStoreTest {

    @Test
    void replaceDocumentShouldDeleteExistingVectorsAndWriteBusinessMetadata() {
        VectorStore vectorStore = mock(VectorStore.class);
        SpringAiDocumentVectorStore documentVectorStore = new SpringAiDocumentVectorStore(vectorStore, properties(1));
        IndexableChunk chunk = chunk("c728ab1e-fa29-4c6f-8ef6-45fa0bd0b9e7");

        List<VectorIndexWriteResult> results = documentVectorStore.replaceDocument(101L, List.of(chunk));

        ArgumentCaptor<Filter.Expression> filterCaptor = ArgumentCaptor.forClass(Filter.Expression.class);
        verify(vectorStore).delete(filterCaptor.capture());
        assertThat(filterCaptor.getValue().type()).isEqualTo(Filter.ExpressionType.EQ);
        assertThat(((Filter.Key) filterCaptor.getValue().left()).key()).isEqualTo("documentId");
        assertThat(((Filter.Value) filterCaptor.getValue().right()).value()).isEqualTo(101L);

        ArgumentCaptor<List<Document>> documentsCaptor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(documentsCaptor.capture());
        Document document = documentsCaptor.getValue().getFirst();
        assertThat(document.getId()).isEqualTo(chunk.chunkId());
        assertThat(document.getText()).isEqualTo("第一章 > 这是用于索引的文本");
        assertThat(document.getMetadata()).containsEntry("documentId", 101L)
                .containsEntry("parentChunkId", "a728ab1e-fa29-4c6f-8ef6-45fa0bd0b9e7")
                .containsEntry("chunkOrder", 2)
                .containsEntry("sectionId", 15L)
                .containsEntry("text", "这是原始正文")
                .containsEntry("metadataJson", "{\"source\":\"test\"}")
                .doesNotContainKey("chunkId");
        assertThat(results).containsExactly(new VectorIndexWriteResult(chunk.chunkId(), chunk.chunkId(), true, null));
    }

    @Test
    void searchShouldRestoreBusinessResultFromDocumentIdAndMetadata() {
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(Document.builder()
                .id("c728ab1e-fa29-4c6f-8ef6-45fa0bd0b9e7")
                .text("索引文本")
                .metadata(Map.of(
                        "documentId", 101L,
                        "parentChunkId", "a728ab1e-fa29-4c6f-8ef6-45fa0bd0b9e7",
                        "chunkOrder", 2,
                        "sectionId", 15L,
                        "text", "这是原始正文",
                        "metadataJson", "{\"source\":\"test\"}"))
                .score(0.92D)
                .build()));
        SpringAiDocumentVectorStore documentVectorStore = new SpringAiDocumentVectorStore(vectorStore, properties(10));

        List<VectorIndexSearchResult> results = documentVectorStore.search("问题", 5);

        assertThat(results).containsExactly(new VectorIndexSearchResult(
                "c728ab1e-fa29-4c6f-8ef6-45fa0bd0b9e7", 101L,
                "a728ab1e-fa29-4c6f-8ef6-45fa0bd0b9e7", 2, 15L,
                "这是原始正文", "{\"source\":\"test\"}", 0.92D));
        ArgumentCaptor<SearchRequest> requestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getQuery()).isEqualTo("问题");
        assertThat(requestCaptor.getValue().getTopK()).isEqualTo(5);
    }

    @Test
    void replaceDocumentShouldRespectEmbeddingBatchSize() {
        VectorStore vectorStore = mock(VectorStore.class);
        SpringAiDocumentVectorStore documentVectorStore = new SpringAiDocumentVectorStore(vectorStore, properties(1));

        documentVectorStore.replaceDocument(101L, List.of(
                chunk("c728ab1e-fa29-4c6f-8ef6-45fa0bd0b9e7"),
                chunk(UUID.randomUUID().toString())));

        verify(vectorStore, times(2)).add(any());
    }

    @Test
    void replaceDocumentShouldDeleteExistingVectorsWhenNoIndexableChunkRemains() {
        VectorStore vectorStore = mock(VectorStore.class);
        SpringAiDocumentVectorStore documentVectorStore = new SpringAiDocumentVectorStore(vectorStore, properties(10));

        List<VectorIndexWriteResult> results = documentVectorStore.replaceDocument(101L, List.of());

        assertThat(results).isEmpty();
        verify(vectorStore).delete(any(Filter.Expression.class));
        verify(vectorStore, times(0)).add(any());
    }

    private RetrievalProperties properties(int maxBatchSize) {
        RetrievalProperties properties = new RetrievalProperties();
        properties.getEmbedding().setMaxBatchSize(maxBatchSize);
        return properties;
    }

    private IndexableChunk chunk(String chunkId) {
        return new IndexableChunk(chunkId, 101L, 2,
                "a728ab1e-fa29-4c6f-8ef6-45fa0bd0b9e7", 15L,
                "这是原始正文", "第一章 > 这是用于索引的文本", "{\"source\":\"test\"}", 8);
    }
}
