package com.nexarag.model.route;

import com.nexarag.model.enums.ModelRouteStrategy;

import java.util.List;

/**
 * 模型路由计划，描述一次模型调用可尝试的候选模型链。
 *
 * @param routeKey   路由Key
 * @param strategy   路由策略
 * @param candidates 候选模型列表
 */
public record ModelRoutePlan(String routeKey, ModelRouteStrategy strategy, List<ModelRouteDecision> candidates) {
}
