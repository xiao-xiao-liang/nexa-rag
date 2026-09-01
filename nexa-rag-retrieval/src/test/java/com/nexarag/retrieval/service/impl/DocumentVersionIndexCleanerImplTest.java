package com.nexarag.retrieval.service.impl;

import com.nexarag.retrieval.index.keyword.KeywordIndexClient;
import com.nexarag.retrieval.index.vector.DocumentVectorStore;
import com.nexarag.retrieval.repository.SectionNavigationIndexRepository;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** 文档版本索引清理器测试。 */
class DocumentVersionIndexCleanerImplTest {

    @Test
    void cleanupShouldDeleteOnlySpecifiedDocumentVersionAcrossAllIndexes() {
        DocumentVectorStore vectorStore = mock(DocumentVectorStore.class);
        KeywordIndexClient keywordIndexClient = mock(KeywordIndexClient.class);
        SectionNavigationIndexRepository navigationIndexRepository = mock(SectionNavigationIndexRepository.class);
        DocumentVersionIndexCleanerImpl cleaner = new DocumentVersionIndexCleanerImpl(vectorStore, keywordIndexClient,
                navigationIndexRepository);

        cleaner.cleanup(1L, 2L);

        verify(vectorStore).deleteByDocumentVersionId(1L, 2L);
        verify(keywordIndexClient).deleteByDocumentVersionId(1L, 2L, null);
        verify(navigationIndexRepository).deleteByDocumentVersionId(1L, 2L);
    }
}
