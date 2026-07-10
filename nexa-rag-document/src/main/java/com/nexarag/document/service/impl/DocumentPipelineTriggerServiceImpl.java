package com.nexarag.document.service.impl;

import com.nexarag.document.converter.DocumentConverter;
import com.nexarag.document.dto.ProcessDocumentRequest;
import com.nexarag.document.entity.Document;
import com.nexarag.document.service.DocumentPipelineTriggerService;
import com.nexarag.document.service.DocumentProcessTaskDispatcher;
import com.nexarag.document.service.DocumentQueueInfo;
import com.nexarag.document.service.DocumentService;
import com.nexarag.document.vo.DocumentProcessStatusVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 文档流水线触发服务实现，负责编排文档状态提交和处理队列投递。
 */
@Service
@RequiredArgsConstructor
public class DocumentPipelineTriggerServiceImpl implements DocumentPipelineTriggerService {

    private final DocumentService documentService;
    private final DocumentProcessTaskDispatcher taskDispatcher;

    /**
     * 提交文档处理并投递流水线任务。
     *
     * @param documentId 文档ID
     * @param request    文档处理请求
     * @return 文档处理状态和实时队列信息
     */
    @Override
    public DocumentProcessStatusVO submitProcess(Long documentId, ProcessDocumentRequest request) {
        // 1. 先更新文档稳定状态和处理配置，确保数据库状态进入 QUEUED
        Document document = documentService.submitProcess(documentId, request);

        // 2. 再投递流水线任务，确保 Worker 可以消费并执行 Workflow Graph
        DocumentQueueInfo queueInfo = taskDispatcher.enqueue(document.getDocumentId());
        return DocumentConverter.toProcessStatusVO(document, queueInfo);
    }

    /**
     * 重试失败文档并投递流水线任务。
     *
     * @param documentId 文档ID
     * @return 文档处理状态和实时队列信息
     */
    @Override
    public DocumentProcessStatusVO retryProcess(Long documentId) {
        // 1. 先重置失败信息并重新进入 QUEUED 状态
        Document document = documentService.retryProcess(documentId);

        // 2. 再投递流水线任务，避免只改状态但没有 Worker 可消费任务
        DocumentQueueInfo queueInfo = taskDispatcher.enqueue(document.getDocumentId());
        return DocumentConverter.toProcessStatusVO(document, queueInfo);
    }
}
