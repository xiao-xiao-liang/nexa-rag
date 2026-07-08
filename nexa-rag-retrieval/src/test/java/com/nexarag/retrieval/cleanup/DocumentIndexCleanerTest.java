package com.nexarag.retrieval.cleanup;

import com.nexarag.retrieval.dto.DocumentIndexCleanupResult;
import com.nexarag.retrieval.index.keyword.KeywordIndexClient;
import com.nexarag.retrieval.index.vector.VectorIndexClient;
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
        when(vectorIndexClient.deleteByDocumentId(1L)).thenReturn(2);
        when(keywordIndexClient.deleteByDocumentId(1L)).thenReturn(3);
        DocumentIndexCleaner cleaner = new DocumentIndexCleanerImpl(vectorIndexClient, keywordIndexClient);

        DocumentIndexCleanupResult result = cleaner.cleanup(1L);

        assertThat(result.success()).isTrue();
        assertThat(result.vectorDeletedCount()).isEqualTo(2);
        assertThat(result.keywordDeletedCount()).isEqualTo(3);
        verify(vectorIndexClient).deleteByDocumentId(1L);
        verify(keywordIndexClient).deleteByDocumentId(1L);
    }
}
