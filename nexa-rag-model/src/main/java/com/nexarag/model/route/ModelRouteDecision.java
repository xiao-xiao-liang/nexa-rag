package com.nexarag.model.route;

import com.nexarag.model.config.ModelProfileProperties;

/**
 * 模型路由决策。
 *
 * @param profileName 模型Profile名称
 * @param profile     模型Profile配置
 * @param fallback    是否为备用模型
 */
public record ModelRouteDecision(String profileName, ModelProfileProperties profile, boolean fallback) {
}
