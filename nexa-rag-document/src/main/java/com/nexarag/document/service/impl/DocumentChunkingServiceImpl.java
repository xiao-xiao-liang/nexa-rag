package com.nexarag.document.service.impl;

import com.nexarag.common.exception.ClientException;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.enums.DocumentErrorCode;
import com.nexarag.document.enums.DocumentVersionStatus;
import com.nexarag.document.model.bo.split.DocumentSplitContext;
import com.nexarag.document.model.bo.split.DocumentSplitResult;
import com.nexarag.document.model.entity.Document;
import com.nexarag.document.model.entity.DocumentVersionDO;
import com.nexarag.document.service.*;
import com.nexarag.document.splitter.DocumentSplitter;
import com.nexarag.document.splitter.DocumentSplitterFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 文档切分阶段服务实现，负责推进状态、选择切分器并保存 chunk。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentChunkingServiceImpl implements DocumentChunkingService {

    private final DocumentService documentService;
    private final DocumentVersionService documentVersionService;
    private final DocumentSplitContextBuilder contextBuilder;
    private final DocumentSplitterFactory splitterFactory;
    private final DocumentChunkService documentChunkService;
    private final DocumentChunkPersistenceService chunkPersistenceService;

    /**
     * 执行指定版本的切分，章节和片段仅替换当前版本的数据。
     */
    @Override
    public int chunk(Long documentId, Long documentVersionId) {
        Document document = documentService.getRequiredDocument(documentId);
        DocumentVersionDO documentVersion = documentVersionService.getRequiredVersion(documentId, documentVersionId);
        if (documentVersion.getStatus() == DocumentVersionStatus.CHUNKED) {
            return documentChunkService.listByDocumentVersionId(documentVersionId).size();
        }
        if (documentVersion.getStatus() != DocumentVersionStatus.PARSED) {
            throw new ClientException("文档版本状态不允许切分，documentId=" + documentId + "，documentVersionId="
                    + documentVersionId + "，status=" + documentVersion.getStatus(), DocumentErrorCode.DOCUMENT_STATUS_INVALID);
        }

        // 1. 先推进版本状态，避免并发 Worker 重复执行切分。
        documentVersion.setStatus(DocumentVersionStatus.CHUNKING);
        if (!documentVersionService.updateById(documentVersion)) {
            throw new ClientException("文档版本状态已变化，请刷新后重试，documentId=" + documentId
                    + "，documentVersionId=" + documentVersionId, DocumentErrorCode.DOCUMENT_STATUS_INVALID);
        }

        // 2. 基于稳定文档元数据和版本快照执行切分。
        DocumentSplitContext context = contextBuilder.build(document, documentVersion);
        DocumentSplitter splitter = splitterFactory.getRequired(context.config().splitStrategy());
        DocumentSplitResult splitResult = splitter.split(context);
        if (splitResult.chunks().isEmpty()) {
            throw new ServiceException("文档版本切分结果为空，documentId=" + documentId
                    + "，documentVersionId=" + documentVersionId, DocumentErrorCode.DOCUMENT_PROCESS_CONFIG_INVALID);
        }

        // 3. 在短事务中替换当前版本的结构数据，再推进版本状态。
        chunkPersistenceService.replaceDocumentVersionStructure(documentId, documentVersionId, splitResult);
        documentVersion.setStatus(DocumentVersionStatus.CHUNKED);
        if (!documentVersionService.updateById(documentVersion)) {
            throw new ServiceException("更新文档版本切分完成状态失败，documentId=" + documentId
                    + "，documentVersionId=" + documentVersionId);
        }
        log.info("文档版本切分阶段执行完成，documentId={}，documentVersionId={}，chunkCount={}",
                documentId, documentVersionId, splitResult.chunks().size());
        return splitResult.chunks().size();
    }

}
