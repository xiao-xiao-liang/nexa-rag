package com.nexarag.model.route;

import com.nexarag.common.exception.ServiceException;
import com.nexarag.model.config.ModelGovernanceProperties;
import com.nexarag.model.config.ModelProfileProperties;
import com.nexarag.model.config.ModelRouteProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

/**
 * 主备模型路由器。
 */
@RequiredArgsConstructor
public class PrimaryFallbackModelRouter implements ModelRouter {

    private final ModelGovernanceProperties properties;

    @Override
    public ModelRouteDecision route(ModelRouteContext context) {
        ModelRouteProperties route = properties.getRoutes().get(context.routeKey());
        if (route == null) {
            throw new ServiceException("模型路由不存在: " + context.routeKey());
        }

        // 1. 根据上下文选择主模型或备用模型
        String profileName = context.useFallback() ? route.getFallback() : route.getPrimary();
        if (!StringUtils.hasText(profileName)) {
            throw new ServiceException("模型路由未配置可用Profile: " + context.routeKey());
        }

        // 2. 查询 Profile 配置
        ModelProfileProperties profile = properties.getProfiles().get(profileName);
        if (profile == null) {
            throw new ServiceException("模型Profile不存在: " + profileName);
        }

        return new ModelRouteDecision(profileName, profile, context.useFallback());
    }
}
