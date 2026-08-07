package com.nexarag.infra.alert.model;

/**
 * 告警严重级别。
 */
public enum AlertSeverity {

    /** 需要关注但不影响核心可用性的告警。 */
    WARNING,
    /** 需要尽快处理的任务失败告警。 */
    ERROR
}
