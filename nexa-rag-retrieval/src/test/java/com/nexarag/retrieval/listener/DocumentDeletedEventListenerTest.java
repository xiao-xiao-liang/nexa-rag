package com.nexarag.retrieval.listener;

import com.nexarag.document.event.DocumentDeletedEvent;
import com.nexarag.retrieval.dto.res.DocumentIndexCleanupResult;
import com.nexarag.retrieval.service.DocumentIndexCleaner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文档删除事件监听器测试。
 */
class DocumentDeletedEventListenerTest {

    @Test
    void shouldCleanContentAndNavigationIndexesAfterDocumentDeletion() {
        DocumentIndexCleaner cleaner = mock(DocumentIndexCleaner.class);
        when(cleaner.cleanup(1L)).thenReturn(new DocumentIndexCleanupResult(1L, 2, 3, true, null));
        DocumentDeletedEventListener listener = new DocumentDeletedEventListener(cleaner);

        listener.onDocumentDeleted(new DocumentDeletedEvent(1L));

        verify(cleaner).cleanup(1L);
    }

    @Test
    void shouldNotPropagateCleanupFailureToCompletedDocumentDeletion() {
        DocumentIndexCleaner cleaner = mock(DocumentIndexCleaner.class);
        when(cleaner.cleanup(1L)).thenThrow(new IllegalStateException("Elasticsearch不可用"));
        DocumentDeletedEventListener listener = new DocumentDeletedEventListener(cleaner);

        assertThatCode(() -> listener.onDocumentDeleted(new DocumentDeletedEvent(1L))).doesNotThrowAnyException();
    }
}
