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
     * 主模型失败后备用模型调用成功。
     */
    FALLBACK_SUCCESS
}
