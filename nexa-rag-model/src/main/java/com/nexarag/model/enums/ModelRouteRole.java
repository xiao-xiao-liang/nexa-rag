package com.nexarag.model.enums;

/**
 * 路由下模型配置角色枚举。
 */
public enum ModelRouteRole {

    /**
     * 主模型配置。
     */
    PRIMARY,

    /**
     * 备用模型配置。
     */
    BACKUP,

    /**
     * 候选模型配置，后续用于权重或规则路由。
     */
    CANDIDATE
}
