package com.nexarag.model.route;

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
    ModelRouteDecision route(ModelRouteContext context);
}
