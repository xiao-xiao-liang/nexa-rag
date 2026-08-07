package com.nexarag.document.enums;

/**
 * 文档任务消费者的最终执行状态。
 */
public enum DocumentTaskStatus {

    /** 历史记录未记录消费者执行结果。 */
    NOT_TRACKED,
    /** 已入队，等待消费者处理。 */
    PENDING,
    /** 消费者正在处理。 */
    PROCESSING,
    /** 消费者已成功完成。 */
    SUCCEEDED,
    /** 消费者重试耗尽后的最终失败。 */
    FAILED
}
