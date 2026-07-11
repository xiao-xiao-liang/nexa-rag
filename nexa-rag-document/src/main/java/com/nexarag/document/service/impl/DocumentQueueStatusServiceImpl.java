package com.nexarag.document.service.impl;

import com.nexarag.document.converter.DocumentConverter;
import com.nexarag.document.entity.Document;
import com.nexarag.document.entity.DocumentQueueInfo;
import com.nexarag.document.service.DocumentQueueStatusService;
import com.nexarag.document.service.DocumentService;
import com.nexarag.document.vo.DocumentProcessStatusVO;
import com.nexarag.infra.queue.document.DocumentPipelineQueue;
import com.nexarag.infra.queue.document.DocumentPipelineQueueStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 文档队列状态服务实现，读取 MySQL 稳定状态并叠加 Redis 实时队列信息。
 */
@Service
@RequiredArgsConstructor
public class DocumentQueueStatusServiceImpl implements DocumentQueueStatusService {

    private final DocumentService documentService;
    private final DocumentPipelineQueue documentPipelineQueue;

    /**
     * 查询文档处理状态。
     *
     * @param documentId 文档ID
     * @return 文档处理状态响应
     */
    @Override
    public DocumentProcessStatusVO getProcessStatus(Long documentId) {
        // 1. 读取 MySQL 中的文档稳定状态
        Document document = documentService.getRequiredDocument(documentId);

        // 2. 查询 Redis 中的实时队列状态
        Optional<DocumentPipelineQueueStatus> queueStatus = documentPipelineQueue.queryStatus(documentId);
        if (queueStatus.isEmpty()) {
            return DocumentConverter.toProcessStatusVO(document);
        }

        // 3. 合并稳定状态和实时队列状态
        DocumentPipelineQueueStatus status = queueStatus.get();
        DocumentQueueInfo queueInfo = new DocumentQueueInfo(status.queuePosition(), status.waitingCount(),
                status.running(), status.workerId(), status.leaseTtlSeconds());
        return DocumentConverter.toProcessStatusVO(document, queueInfo);
    }
}
