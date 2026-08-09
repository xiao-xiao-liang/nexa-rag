package com.nexarag.document.service;

import com.nexarag.document.model.entity.Document;

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

    /**
     * 创建待发布的对象存储清理任务。
     *
     * @param document 待删除文档，需包含原始和解析文件对象名
     */
    void createStorageCleanupTask(Document document);
}
