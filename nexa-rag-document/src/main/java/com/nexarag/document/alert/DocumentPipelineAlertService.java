package com.nexarag.document.alert;

/**
 * 文档流水线告警服务，为日志和后续外部告警适配器提供统一接口。
 */
public interface DocumentPipelineAlertService {

    /**
     * 发出文档流水线最终失败告警。
     *
     * @param event 最终失败事件
     */
    void alert(DocumentPipelineFailureEvent event);
}
