package com.nexarag.document.service;

import com.nexarag.document.model.vo.DocumentTaskVO;

/**
 * 文档异步任务管理服务，提供安全查询和人工重试能力。
 */
public interface DocumentTaskAdminService {

    /**
     * 查询单个文档任务。
     *
     * @param outboxId 任务Outbox ID
     * @return 脱敏后的任务信息
     */
    DocumentTaskVO getTask(Long outboxId);

    /**
     * 为最终失败任务创建新的待发布任务。
     *
     * @param outboxId 失败任务Outbox ID
     * @return 新任务信息
     */
    DocumentTaskVO retryFailedTask(Long outboxId);
}
