package com.nexarag.document.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.nexarag.document.model.entity.DocumentTaskOutboxDO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文档流水线Outbox服务，负责消息保存、发布抢占和结果状态维护。
 */
public interface DocumentPipelineOutboxService extends IService<DocumentTaskOutboxDO> {

    /**
     * 抢占当前可发布的消息。
     *
     * @param lockOwner 发布器实例标识
     * @param now       当前时间
     * @return 成功抢占的消息列表
     */
    List<DocumentTaskOutboxDO> claimPublishableMessages(String lockOwner, LocalDateTime now);

    /**
     * 标记消息发布成功。
     *
     * @param outboxId Outbox记录ID
     */
    void markPublished(Long outboxId);

    /**
     * 记录消息发布失败并计算后续状态。
     *
     * @param outboxId     Outbox记录ID
     * @param failureReason 失败原因
     */
    void markPublishFailed(Long outboxId, String failureReason);
}
