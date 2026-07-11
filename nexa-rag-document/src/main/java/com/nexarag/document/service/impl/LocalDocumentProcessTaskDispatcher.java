package com.nexarag.document.service.impl;

import com.nexarag.document.service.DocumentProcessTaskDispatcher;
import com.nexarag.document.entity.DocumentQueueInfo;
import lombok.extern.slf4j.Slf4j;

/**
 * 本地占位文档任务分发器，当前批次只返回入队语义，真实 Redis 队列由下一批实现。
 */
@Slf4j
public class LocalDocumentProcessTaskDispatcher implements DocumentProcessTaskDispatcher {

    /**
     * 投递文档处理任务。
     *
     * @param documentId 文档ID
     * @return 文档队列信息
     */
    @Override
    public DocumentQueueInfo enqueue(Long documentId) {
        // 1. 当前批次只保留自动流水线入口，下一批接入 Redis 后替换实时队列位置
        log.info("文档处理任务已提交到本地占位队列，documentId={}", documentId);
        return new DocumentQueueInfo(1, 1);
    }
}
