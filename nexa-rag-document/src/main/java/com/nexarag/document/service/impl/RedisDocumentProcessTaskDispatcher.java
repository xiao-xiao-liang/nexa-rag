package com.nexarag.document.service.impl;

import com.nexarag.document.service.DocumentProcessTaskDispatcher;
import com.nexarag.document.entity.DocumentQueueInfo;
import com.nexarag.infra.queue.document.DocumentPipelineQueue;
import com.nexarag.infra.queue.document.DocumentPipelineQueueStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Redis 文档处理任务分发器，负责将文档入库流水线任务投递到 Redis 公平队列。
 */
@Service
@RequiredArgsConstructor
public class RedisDocumentProcessTaskDispatcher implements DocumentProcessTaskDispatcher {

    private final DocumentPipelineQueue documentPipelineQueue;

    /**
     * 投递文档处理任务并返回实时队列信息。
     *
     * @param documentId 文档ID
     * @return 文档队列信息
     */
    @Override
    public DocumentQueueInfo enqueue(Long documentId) {
        // 1. 调用基础队列能力完成入队
        DocumentPipelineQueueStatus status = documentPipelineQueue.enqueue(documentId);

        // 2. 将基础队列状态映射为 document 模块对外使用的队列信息
        return new DocumentQueueInfo(status.queuePosition(), status.waitingCount(), status.running(),
                status.workerId(), status.leaseTtlSeconds());
    }
}
