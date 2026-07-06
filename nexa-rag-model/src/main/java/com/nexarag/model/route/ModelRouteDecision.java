package com.nexarag.model.route;

import com.nexarag.model.config.ModelProfileProperties;

/**
 * 模型路由决策。
 *
 * @param profileName   模型Profile名称
 * @param profile       模型Profile配置
 * @param fallback      是否为备用模型
 * @param priority      候选优先级
 * @param weight        候选权重
 * @param routeConfigId 路由配置关联ID
 * @param configId      模型配置ID
 */
public record ModelRouteDecision(String profileName, ModelProfileProperties profile, boolean fallback,
                                 Integer priority, Integer weight, Long routeConfigId, Long configId) {

    /**
     * 构造兼容旧主备路由的决策对象。
     *
     * @param profileName 模型Profile名称
     * @param profile     模型Profile配置
     * @param fallback    是否为备用模型
     */
    public ModelRouteDecision(String profileName, ModelProfileProperties profile, boolean fallback) {
        this(profileName, profile, fallback, null, null, null, null);
    }
}
