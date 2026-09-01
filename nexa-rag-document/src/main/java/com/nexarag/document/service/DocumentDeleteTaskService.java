package com.nexarag.document.service;

import com.nexarag.document.model.entity.DocumentVersionDO;

/**
 * 文档删除后的异步任务创建服务。
 */
public interface DocumentDeleteTaskService {

    /**
     * 创建指定历史版本的外部索引清理任务。
     *
     * @param documentId        文档ID
     * @param documentVersionId 待永久删除的历史版本ID
     * @return 清理任务Outbox ID
     */
    Long createVersionIndexCleanupTask(Long documentId, Long documentVersionId);

    /**
     * 创建指定历史版本的对象存储清理任务。
     *
     * @param documentVersion 待永久删除的历史版本快照
     */
    void createVersionStorageCleanupTask(DocumentVersionDO documentVersion);
}
