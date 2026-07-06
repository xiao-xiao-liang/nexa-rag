package com.nexarag.model.route;

import com.nexarag.common.exception.ServiceException;

/**
 * 模型路由器。
 */
public interface ModelRouter {

    /**
     * 根据上下文选择模型。
     *
     * @param context 路由上下文
     * @return 路由决策
     */
    ModelRoutePlan plan(ModelRouteContext context);

    /**
     * 根据上下文选择第一个可用模型。
     *
     * @param context 路由上下文
     * @return 路由决策
     */
    default ModelRouteDecision route(ModelRouteContext context) {
        // 1. 通过路由计划保持旧调用方兼容
        ModelRoutePlan plan = plan(context);
        if (plan.candidates() == null || plan.candidates().isEmpty()) {
            throw new ServiceException("模型路由没有可用候选: " + context.routeKey());
        }
        return plan.candidates().getFirst();
    }
}
