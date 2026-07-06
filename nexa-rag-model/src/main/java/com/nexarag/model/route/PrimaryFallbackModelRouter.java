package com.nexarag.model.route;

import com.nexarag.common.exception.ServiceException;
import com.nexarag.model.config.ModelGovernanceProperties;
import com.nexarag.model.config.ModelProfileProperties;
import com.nexarag.model.config.ModelRouteProperties;
import com.nexarag.model.enums.ModelRouteStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 主备模型路由器。
 */
@RequiredArgsConstructor
public class PrimaryFallbackModelRouter implements ModelRouter {

    private final ModelGovernanceProperties properties;

    @Override
    public ModelRoutePlan plan(ModelRouteContext context) {
        ModelRouteProperties route = properties.getRoutes().get(context.routeKey());
        if (route == null) {
            throw new ServiceException("模型路由不存在: " + context.routeKey());
        }
        ModelRouteStrategy strategy = routeStrategy(route);
        if (strategy != ModelRouteStrategy.PRIMARY_BACKUP) {
            throw new ServiceException("当前本地配置路由暂不支持权重或规则策略");
        }

        // 1. 根据主备配置生成候选模型链
        List<ModelRouteDecision> candidates = new ArrayList<>();
        if (context.useFallback()) {
            addCandidate(candidates, route.getFallback(), true);
        } else {
            addCandidate(candidates, route.getPrimary(), false);
            addCandidate(candidates, route.getFallback(), true);
        }
        if (candidates.isEmpty()) {
            throw new ServiceException("模型路由未配置可用Profile: " + context.routeKey());
        }
        return new ModelRoutePlan(context.routeKey(), strategy, candidates);
    }

    private void addCandidate(List<ModelRouteDecision> candidates, String profileName, boolean fallback) {
        if (!StringUtils.hasText(profileName)) {
            return;
        }
        ModelProfileProperties profile = properties.getProfiles().get(profileName);
        if (profile == null) {
            if (fallback) {
                return;
            }
            throw new ServiceException("模型Profile不存在: " + profileName);
        }
        candidates.add(new ModelRouteDecision(profileName, profile, fallback));
    }

    private ModelRouteStrategy routeStrategy(ModelRouteProperties route) {
        if (!StringUtils.hasText(route.getType())) {
            return ModelRouteStrategy.PRIMARY_BACKUP;
        }
        if ("PRIMARY_FALLBACK".equals(route.getType())) {
            return ModelRouteStrategy.PRIMARY_BACKUP;
        }
        return ModelRouteStrategy.valueOf(route.getType());
    }
}
