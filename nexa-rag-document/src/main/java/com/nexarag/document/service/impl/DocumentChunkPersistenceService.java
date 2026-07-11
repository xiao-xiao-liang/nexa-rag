package com.nexarag.document.service.impl;

import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.service.DocumentChunkService;
import com.nexarag.document.service.DocumentService;
import com.nexarag.document.splitter.ChunkDraft;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 文档切分结果持久化服务，负责在短事务内替换片段并完成文档状态流转。
 */
@Service
@RequiredArgsConstructor
public class DocumentChunkPersistenceService {

    private final DocumentChunkService documentChunkService;
    private final DocumentService documentService;

    /**
     * 替换文档片段并将文档推进到切分完成状态。
     *
     * @param documentId 文档ID
     * @param drafts     文档片段草稿
     */
    @Transactional(rollbackFor = Exception.class)
    public void replaceChunksAndMarkChunked(Long documentId, List<ChunkDraft> drafts) {
        // 1. 替换文档的全部有效片段
        documentChunkService.replaceDocumentChunks(documentId, drafts);

        // 2. 原子推进文档状态，失败时回滚本次片段替换
        if (!documentService.markChunked(documentId)) {
            throw new ServiceException("更新文档切分完成状态失败，documentId=" + documentId);
        }
    }
}
