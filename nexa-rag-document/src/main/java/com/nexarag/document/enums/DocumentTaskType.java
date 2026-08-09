package com.nexarag.document.enums;

/**
 * 文档异步任务类型。
 */
public enum DocumentTaskType {

    /** 文档处理流水线任务。 */
    PROCESS_DOCUMENT,
    /** 文档外部索引清理任务。 */
    CLEAN_DOCUMENT_INDEX,
    /** 文档对象存储清理任务。 */
    CLEAN_DOCUMENT_STORAGE,
    /** 飞书最终失败告警任务。 */
    SEND_FEISHU_FAILURE_ALERT,
    /** 邮件最终失败告警任务。 */
    SEND_EMAIL_FAILURE_ALERT;

    /**
     * 判断是否为通知渠道任务。
     *
     * @return 是通知任务时返回true
     */
    public boolean isAlertTask() {
        return this == SEND_FEISHU_FAILURE_ALERT || this == SEND_EMAIL_FAILURE_ALERT;
    }
}
