package com.nexarag.document.service;

/**
 * 文档处理任务分发器，负责把整条文档入库流水线任务交给后续队列或 Worker。
 */
public interface DocumentProcessTaskDispatcher {

    /**
     * 投递文档处理任务。
     *
     * @param documentId 文档ID
     * @return 文档队列信息
     */
    DocumentQueueInfo enqueue(Long documentId);
}
