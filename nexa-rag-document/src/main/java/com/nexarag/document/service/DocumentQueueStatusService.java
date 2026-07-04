package com.nexarag.document.service;

import com.nexarag.document.vo.DocumentProcessStatusVO;

/**
 * 文档队列状态服务，负责合并文档稳定处理状态和 Redis 实时队列状态。
 */
public interface DocumentQueueStatusService {

    /**
     * 查询文档处理状态。
     *
     * @param documentId 文档ID
     * @return 文档处理状态响应
     */
    DocumentProcessStatusVO getProcessStatus(Long documentId);
}
