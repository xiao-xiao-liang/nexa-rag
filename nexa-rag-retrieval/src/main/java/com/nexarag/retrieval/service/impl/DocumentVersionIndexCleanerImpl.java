package com.nexarag.retrieval.service.impl;

import com.nexarag.retrieval.index.keyword.KeywordIndexClient;
import com.nexarag.retrieval.index.vector.DocumentVectorStore;
import com.nexarag.retrieval.repository.SectionNavigationIndexRepository;
import com.nexarag.retrieval.service.DocumentVersionIndexCleaner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 文档历史版本索引清理实现，所有删除调用均同时携带文档ID和版本ID。
 */
@Component
@RequiredArgsConstructor
public class DocumentVersionIndexCleanerImpl implements DocumentVersionIndexCleaner {

    private final DocumentVectorStore documentVectorStore;
    private final KeywordIndexClient keywordIndexClient;
    private final SectionNavigationIndexRepository sectionNavigationIndexRepository;

    @Override
    public void cleanup(Long documentId, Long documentVersionId) {
        // 1. 逐类清理可幂等的派生索引，任一异常交由消息队列重试。
        documentVectorStore.deleteByDocumentVersionId(documentId, documentVersionId);
        keywordIndexClient.deleteByDocumentVersionId(documentId, documentVersionId, null);
        sectionNavigationIndexRepository.deleteByDocumentVersionId(documentId, documentVersionId);
    }
}
