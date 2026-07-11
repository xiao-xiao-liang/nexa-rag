package com.nexarag.document.vo;

import com.nexarag.document.enums.DocumentStatus;
import com.nexarag.document.enums.DocumentPipelineMessageStatus;

/**
 * 文档处理状态响应。
 *
 * @param documentId    文档ID
 * @param processId     处理批次ID
 * @param status        文档状态
 * @param messageStatus 流水线消息状态
 * @param consumedTimes 消息消费次数
 * @param failureStage  失败阶段
 * @param failureReason 失败原因
 */
public record DocumentProcessStatusVO(Long documentId,
                                      String processId,
                                      DocumentStatus status,
                                      DocumentPipelineMessageStatus messageStatus,
                                      Integer consumedTimes,
                                      String failureStage,
                                      String failureReason) {
}
