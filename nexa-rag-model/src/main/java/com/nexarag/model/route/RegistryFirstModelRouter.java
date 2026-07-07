package com.nexarag.model.route;

import com.nexarag.common.exception.ServiceException;
import com.nexarag.model.config.ModelProfileProperties;
import com.nexarag.model.entity.ModelConfig;
import com.nexarag.model.entity.ModelRoute;
import com.nexarag.model.entity.ModelRouteConfig;
import com.nexarag.model.enums.ModelRouteRole;
import com.nexarag.model.enums.ModelRouteStrategy;
import com.nexarag.model.registry.ModelRegistry;
import com.nexarag.model.security.ModelSecretEncryptor;
import lombok.RequiredArgsConstructor;
import org.springframework.util.CollectionUtils;

import java.util.Comparator;
import java.util.List;

/**
 * 数据库优先模型路由器，优先使用模型注册表快照，未命中时回退本地配置路由。
 */
@RequiredArgsConstructor
public class RegistryFirstModelRouter implements ModelRouter {

    private final ModelRegistry modelRegistry;
    private final ModelRouter fallbackRouter;
    private final ModelSecretEncryptor secretEncryptor;
    private final WeightedModelRouteSelector weightedModelRouteSelector;

    public RegistryFirstModelRouter(ModelRegistry modelRegistry, ModelRouter fallbackRouter) {
        this(modelRegistry, fallbackRouter, null, new WeightedModelRouteSelector());
    }

    @Override
    public ModelRoutePlan plan(ModelRouteContext context) {
        // 1. 优先从数据库注册表快照匹配路由
        ModelRoute route = modelRegistry.getRoute(context.routeKey());
        if (route == null) {
            return fallbackRouter.plan(context);
        }

        // 2. 根据路由配置构建可执行候选链
        List<ModelRouteDecision> candidates = registryCandidates(route, context);
        if (candidates.isEmpty()) {
            throw new ServiceException("数据库模型路由没有可用候选: " + context.routeKey());
        }

        // 3. 根据路由策略返回最终候选顺序
        ModelRouteStrategy strategy = route.getStrategy() == null ? ModelRouteStrategy.PRIMARY_BACKUP : route.getStrategy();
        if (strategy == ModelRouteStrategy.WEIGHT) {
            return new ModelRoutePlan(route.getRouteKey(), strategy, weightedModelRouteSelector.orderCandidates(candidates));
        }
        if (strategy == ModelRouteStrategy.RULE) {
            throw new ServiceException("数据库模型路由暂不支持规则路由: " + context.routeKey());
        }
        return new ModelRoutePlan(route.getRouteKey(), strategy, candidates);
    }

    private List<ModelRouteDecision> registryCandidates(ModelRoute route, ModelRouteContext context) {
        List<ModelRouteConfig> routeConfigs = modelRegistry.getRouteConfigs(route.getRouteId());
        if (CollectionUtils.isEmpty(routeConfigs)) {
            return List.of();
        }

        return routeConfigs.stream()
                .filter(routeConfig -> Boolean.TRUE.equals(routeConfig.getEnabled()))
                .filter(routeConfig -> !context.useFallback() || routeConfig.getRole() == ModelRouteRole.BACKUP)
                .sorted(candidateComparator())
                .map(this::toDecision)
                .toList();
    }

    private Comparator<ModelRouteConfig> candidateComparator() {
        return Comparator
                .comparingInt((ModelRouteConfig routeConfig) -> roleOrder(routeConfig.getRole()))
                .thenComparing(ModelRouteConfig::getPriority, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private int roleOrder(ModelRouteRole role) {
        if (role == ModelRouteRole.PRIMARY) {
            return 0;
        }
        if (role == ModelRouteRole.BACKUP) {
            return 1;
        }
        return 2;
    }

    private ModelRouteDecision toDecision(ModelRouteConfig routeConfig) {
        ModelConfig config = modelRegistry.getConfig(routeConfig.getConfigId());
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            throw new ServiceException("数据库模型路由关联的模型配置不可用: " + routeConfig.getConfigId());
        }
        ModelProfileProperties profile = toProfile(config);
        boolean fallback = routeConfig.getRole() == ModelRouteRole.BACKUP;
        return new ModelRouteDecision(config.getConfigKey(), profile, fallback,
                routeConfig.getPriority(), routeConfig.getWeight(), routeConfig.getRouteConfigId(),
                config.getConfigId(), modelRegistry.current().versionNo());
    }

    private ModelProfileProperties toProfile(ModelConfig config) {
        return ModelProfileProperties.builder()
                .provider(config.getProvider().name())
                .baseUrl(config.getBaseUrl())
                .endpointPath(config.getEndpointPath())
                .apiKey(decryptApiKey(config.getApiKeyCipher()))
                .modelName(config.getModelName())
                .timeoutMs(config.getTimeoutMs() == null ? 60000L : config.getTimeoutMs().longValue())
                .build();
    }

    private String decryptApiKey(String apiKeyCipher) {
        if (secretEncryptor == null) {
            return null;
        }
        return secretEncryptor.decrypt(apiKeyCipher);
    }
}
