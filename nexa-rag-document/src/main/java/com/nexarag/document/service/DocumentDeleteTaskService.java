package com.nexarag.document.service;

/**
 * 文档删除后的异步任务创建服务。
 */
public interface DocumentDeleteTaskService {

    /**
     * 创建待发布的外部索引清理任务。
     *
     * @param documentId 已逻辑删除的文档ID
     * @return 清理任务Outbox ID
     */
    Long createIndexCleanupTask(Long documentId);
}
