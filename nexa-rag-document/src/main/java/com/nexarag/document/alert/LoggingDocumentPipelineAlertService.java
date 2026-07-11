package com.nexarag.document.alert;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 文档流水线日志告警实现，以结构化错误日志记录最终失败事件。
 */
@Slf4j
@Service
public class LoggingDocumentPipelineAlertService implements DocumentPipelineAlertService {

    @Override
    public void alert(DocumentPipelineFailureEvent event) {
        // 1. 输出稳定字段，便于日志平台检索和后续告警规则接入
        log.error("文档流水线最终失败告警，documentId={}，processId={}，failureStage={}，failureReason={}，"
                        + "consumedTimes={}，messageId={}，failureTime={}",
                event.documentId(), event.processId(), event.failureStage(), event.failureReason(),
                event.consumedTimes(), event.messageId(), event.failureTime());
    }
}
