package com.nexarag.model.route;

/**
 * 模型路由上下文。
 *
 * @param routeKey    路由Key
 * @param useFallback 是否使用备用模型
 */
public record ModelRouteContext(String routeKey, boolean useFallback) {
}
