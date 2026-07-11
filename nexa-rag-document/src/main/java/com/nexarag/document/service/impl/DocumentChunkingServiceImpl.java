package com.nexarag.document.service.impl;

import com.nexarag.common.exception.ClientException;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.entity.Document;
import com.nexarag.document.enums.DocumentStatus;
import com.nexarag.document.error.DocumentErrorCode;
import com.nexarag.document.service.DocumentChunkService;
import com.nexarag.document.service.DocumentChunkingService;
import com.nexarag.document.service.DocumentService;
import com.nexarag.document.service.DocumentSplitContextBuilder;
import com.nexarag.document.splitter.ChunkDraft;
import com.nexarag.document.splitter.DocumentSplitContext;
import com.nexarag.document.splitter.DocumentSplitter;
import com.nexarag.document.splitter.DocumentSplitterFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 文档切分阶段服务实现，负责推进状态、选择切分器并保存 chunk。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentChunkingServiceImpl implements DocumentChunkingService {

    private static final String FAILURE_STAGE_CHUNK = "CHUNK";
    private static final String FAILURE_REASON_CHUNK = "文档切分失败";

    private final DocumentService documentService;
    private final DocumentSplitContextBuilder contextBuilder;
    private final DocumentSplitterFactory splitterFactory;
    private final DocumentChunkService documentChunkService;
    private final DocumentChunkPersistenceService chunkPersistenceService;
    private final DocumentProcessFailureService processFailureService;

    /**
     * 执行文档切分阶段。
     *
     * @param documentId 文档ID
     * @return 保存的片段数量
     */
    @Override
    public int chunk(Long documentId) {
        Document document = documentService.getRequiredDocument(documentId);
        if (document.getStatus() == DocumentStatus.CHUNKED) {
            return Math.toIntExact(documentChunkService.countByDocumentId(documentId));
        }
        if (document.getStatus() != DocumentStatus.PARSED) {
            throw new ClientException("文档状态不允许切分，documentId=" + documentId + "，status=" + document.getStatus(),
                    DocumentErrorCode.DOCUMENT_STATUS_INVALID);
        }

        markChunking(document);
        try {
            // 1. 构造上下文并选择切分器
            DocumentSplitContext context = contextBuilder.build(document);
            DocumentSplitter splitter = splitterFactory.getRequired(context.config().splitStrategy());
            List<ChunkDraft> drafts = splitter.split(context);
            if (drafts.isEmpty()) {
                throw new ServiceException("文档切分结果为空，documentId=" + documentId,
                        DocumentErrorCode.DOCUMENT_PROCESS_CONFIG_INVALID);
            }

            // 2. 在短事务内保存片段并推进到 CHUNKED
            chunkPersistenceService.replaceChunksAndMarkChunked(documentId, drafts);
            log.info("文档切分阶段执行完成，documentId={}，chunkCount={}", documentId, drafts.size());
            return drafts.size();
        } catch (RuntimeException exception) {
            Document failureDocument = processFailureService.recordFailure(documentId, FAILURE_STAGE_CHUNK,
                    FAILURE_REASON_CHUNK, exception.getMessage());
            if (failureDocument.getStatus() == DocumentStatus.QUEUED) {
                throw new ServiceException("文档切分失败，documentId=" + documentId, exception,
                        DocumentErrorCode.DOCUMENT_STATUS_INVALID);
            }
            log.error("文档切分失败且不再重试，documentId={}，status={}",
                    documentId, failureDocument.getStatus(), exception);
            return 0;
        }
    }

    private void markChunking(Document document) {
        // 1. 通过条件更新抢占切分任务，避免多个 Worker 重复处理
        if (!documentService.markChunking(document.getDocumentId())) {
            throw new ClientException("文档状态已变化，请刷新后重试，documentId=" + document.getDocumentId(),
                    DocumentErrorCode.DOCUMENT_STATUS_INVALID);
        }
        document.setStatus(DocumentStatus.CHUNKING);
    }
}
