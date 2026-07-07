package com.nexarag.model.enums;

/**
 * 模型调用状态。
 */
public enum ModelCallStatus {

    /**
     * 调用中。
     */
    RUNNING,

    /**
     * 调用成功。
     */
    SUCCESS,

    /**
     * 调用失败。
     */
    FAILED,

    /**
     * 调用超时。
     */
    TIMEOUT,

    /**
     * 调用被取消。
     */
    CANCELED,

    /**
     * 主模型失败后备用模型调用成功。
     *
     * @deprecated 明细日志不再使用该状态，fallback 信息通过 attempt 和 fallback 字段表达。
     */
    @Deprecated
    FALLBACK_SUCCESS
}
