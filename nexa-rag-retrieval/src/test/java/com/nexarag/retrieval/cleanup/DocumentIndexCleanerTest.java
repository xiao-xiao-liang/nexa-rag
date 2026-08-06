package com.nexarag.retrieval.cleanup;

import com.nexarag.retrieval.dto.res.DocumentIndexCleanupResult;
import com.nexarag.retrieval.index.keyword.KeywordIndexClient;
import com.nexarag.retrieval.index.vector.VectorIndexClient;
import com.nexarag.retrieval.repository.SectionNavigationIndexRepository;
import com.nexarag.retrieval.service.DocumentIndexCleaner;
import com.nexarag.retrieval.service.impl.DocumentIndexCleanerImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文档索引清理器测试。
 */
class DocumentIndexCleanerTest {

    @Test
    void cleanupShouldDeleteVectorAndKeywordIndex() {
        VectorIndexClient vectorIndexClient = mock(VectorIndexClient.class);
        KeywordIndexClient keywordIndexClient = mock(KeywordIndexClient.class);
        SectionNavigationIndexRepository navigationIndexRepository = mock(SectionNavigationIndexRepository.class);
        when(vectorIndexClient.deleteByDocumentId(1L)).thenReturn(2);
        when(keywordIndexClient.deleteByDocumentId(1L)).thenReturn(3);
        DocumentIndexCleaner cleaner = new DocumentIndexCleanerImpl(vectorIndexClient, keywordIndexClient,
                navigationIndexRepository);

        DocumentIndexCleanupResult result = cleaner.cleanup(1L);

        assertThat(result.success()).isTrue();
        assertThat(result.vectorDeletedCount()).isEqualTo(2);
        assertThat(result.keywordDeletedCount()).isEqualTo(3);
        verify(vectorIndexClient).deleteByDocumentId(1L);
        verify(keywordIndexClient).deleteByDocumentId(1L);
        verify(navigationIndexRepository).deleteByDocumentId(1L);
    }

    @Test
    void cleanupShouldContinueKeywordCleanupWhenVectorCleanupFails() {
        VectorIndexClient vectorIndexClient = mock(VectorIndexClient.class);
        KeywordIndexClient keywordIndexClient = mock(KeywordIndexClient.class);
        SectionNavigationIndexRepository navigationIndexRepository = mock(SectionNavigationIndexRepository.class);
        when(vectorIndexClient.deleteByDocumentId(1L)).thenThrow(new IllegalStateException("Milvus不可用"));
        when(keywordIndexClient.deleteByDocumentId(1L)).thenReturn(3);
        DocumentIndexCleaner cleaner = new DocumentIndexCleanerImpl(vectorIndexClient, keywordIndexClient,
                navigationIndexRepository);

        DocumentIndexCleanupResult result = cleaner.cleanup(1L);

        assertThat(result.success()).isFalse();
        assertThat(result.vectorDeletedCount()).isZero();
        assertThat(result.keywordDeletedCount()).isEqualTo(3);
        assertThat(result.failureReason()).contains("Milvus不可用");
        verify(keywordIndexClient).deleteByDocumentId(1L);
    }

    @Test
    void cleanupShouldKeepVectorResultWhenKeywordCleanupFails() {
        VectorIndexClient vectorIndexClient = mock(VectorIndexClient.class);
        KeywordIndexClient keywordIndexClient = mock(KeywordIndexClient.class);
        SectionNavigationIndexRepository navigationIndexRepository = mock(SectionNavigationIndexRepository.class);
        when(vectorIndexClient.deleteByDocumentId(1L)).thenReturn(2);
        when(keywordIndexClient.deleteByDocumentId(1L)).thenThrow(new IllegalStateException("Elasticsearch不可用"));
        DocumentIndexCleaner cleaner = new DocumentIndexCleanerImpl(vectorIndexClient, keywordIndexClient,
                navigationIndexRepository);

        DocumentIndexCleanupResult result = cleaner.cleanup(1L);

        assertThat(result.success()).isFalse();
        assertThat(result.vectorDeletedCount()).isEqualTo(2);
        assertThat(result.keywordDeletedCount()).isZero();
        assertThat(result.failureReason()).contains("Elasticsearch不可用");
    }
}
