package com.nexarag.infra.messaging.document.model;

import java.time.LocalDateTime;

/**
 * 文档流水线失败消息，记录消费失败上下文以供后续补偿和排查。
 *
 * @param outboxId          对应处理任务Outbox ID；历史消息允许为空
 * @param documentId        文档ID
 * @param documentVersionId 文档版本ID
 * @param processId         处理批次ID
 * @param failureStage      失败阶段
 * @param failureReason     失败原因
 * @param failureDetail     失败详情
 * @param consumedTimes     已消费次数
 * @param messageId         原始消息ID
 * @param failureTime       失败时间
 */
public record DocumentPipelineFailureMessage(
        Long outboxId,
        Long documentId,
        Long documentVersionId,
        String processId,
        String failureStage,
        String failureReason,
        String failureDetail,
        Integer consumedTimes,
        String messageId,
        LocalDateTime failureTime) {

    /**
     * 兼容V1失败消息：历史文档在迁移后固定映射为与文档ID相同的初始版本ID。
     *
     * @param outboxId      对应处理任务Outbox ID
     * @param documentId    文档ID
     * @param processId     处理批次ID
     * @param failureStage  失败阶段
     * @param failureReason 失败原因
     * @param failureDetail 失败详情
     * @param consumedTimes 已消费次数
     * @param messageId     原始消息ID
     * @param failureTime   失败时间
     */
    public DocumentPipelineFailureMessage(Long outboxId, Long documentId, String processId,
                                          String failureStage, String failureReason, String failureDetail,
                                          Integer consumedTimes, String messageId, LocalDateTime failureTime) {
        this(outboxId, documentId, documentId, processId, failureStage, failureReason, failureDetail,
                consumedTimes, messageId, failureTime);
    }
}
