package com.nexarag.infra.alert;

import com.nexarag.infra.alert.model.AlertMessage;

/**
 * 告警任务的业务状态回调，由拥有Outbox表的业务模块实现。
 */
public interface AlertDeliveryLifecycle {

    /**
     * 将告警任务标记为处理中。
     *
     * @param message 告警消息
     * @param consumeRetryCount 当前消费次数
     * @return false表示任务已进入终态，无需再次投递
     */
    boolean markProcessing(AlertMessage message, int consumeRetryCount);

    /**
     * 将告警任务标记为投递成功。
     *
     * @param message 告警消息
     */
    void markSucceeded(AlertMessage message);

    /**
     * 将告警任务标记为最终失败。
     *
     * @param message 告警消息
     * @param consumeRetryCount 当前消费次数
     * @param failureReason 脱敏失败原因
     */
    void markFailed(AlertMessage message, int consumeRetryCount, String failureReason);
}
