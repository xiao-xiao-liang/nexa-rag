package com.nexarag.model.enums;

/**
 * 模型治理配置绑定模式，用于决定治理配置按模型配置还是按业务路由生效。
 */
public enum ModelGovernanceBindingMode {

    /**
     * 按模型配置ID绑定治理策略。
     */
    CONFIG,

    /**
     * 按模型路由 routeKey 绑定治理策略。
     */
    ROUTE
}
