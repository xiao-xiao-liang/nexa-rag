package com.nexarag.document.alert;

import java.time.LocalDateTime;

/**
 * 文档流水线最终失败事件，承载结构化告警所需上下文。
 *
 * @param documentId   文档ID
 * @param processId    处理批次ID
 * @param failureStage 失败阶段
 * @param failureReason 失败原因
 * @param failureDetail 失败详情
 * @param consumedTimes 消费次数
 * @param messageId    消息ID
 * @param failureTime  失败时间
 */
public record DocumentPipelineFailureEvent(Long documentId,
                                           String processId,
                                           String failureStage,
                                           String failureReason,
                                           String failureDetail,
                                           Integer consumedTimes,
                                           String messageId,
                                           LocalDateTime failureTime) {
}
