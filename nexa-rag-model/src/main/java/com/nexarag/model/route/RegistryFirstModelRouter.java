package com.nexarag.model.route;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.model.config.ModelProfileProperties;
import com.nexarag.model.entity.ModelConfig;
import com.nexarag.model.entity.ModelRoute;
import com.nexarag.model.entity.ModelRouteConfig;
import com.nexarag.model.enums.ModelRouteRole;
import com.nexarag.model.enums.ModelRouteStrategy;
import com.nexarag.model.registry.ModelRegistry;
import com.nexarag.model.toolkits.ModelSecretEncryptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

import java.util.Comparator;
import java.util.List;

/**
 * 数据库优先模型路由器，优先使用模型注册表快照，未命中时回退本地配置路由。
 */
@RequiredArgsConstructor
@Slf4j
public class RegistryFirstModelRouter implements ModelRouter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
        JsonNode extraConfig = parseExtraConfig(config.getExtraConfig(), config.getConfigKey());
        return ModelProfileProperties.builder()
                .provider(config.getProvider().name())
                .baseUrl(config.getBaseUrl())
                .endpointPath(config.getEndpointPath())
                .apiKey(decryptApiKey(config.getApiKeyCipher()))
                .modelName(config.getModelName())
                .timeoutMs(config.getTimeoutMs() == null ? 60000L : config.getTimeoutMs().longValue())
                .contextWindowTokens(positiveInt(extraConfig, "contextWindowTokens"))
                .reservedOutputTokens(positiveInt(extraConfig, "reservedOutputTokens"))
                .build();
    }

    /**
     * 解析模型扩展配置。配置无效时保持兼容，交由调用侧的保守默认值兜底。
     *
     * @param extraConfig 数据库中的扩展配置 JSON
     * @param configKey 模型配置标识
     * @return JSON 根节点；无效时返回空节点
     */
    private JsonNode parseExtraConfig(String extraConfig, String configKey) {
        if (extraConfig == null || extraConfig.isBlank()) {
            return OBJECT_MAPPER.createObjectNode();
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(extraConfig);
            return node != null && node.isObject() ? node : OBJECT_MAPPER.createObjectNode();
        } catch (Exception exception) {
            log.warn("模型扩展配置不是有效 JSON，将使用保守的上下文窗口默认值，configKey={}", configKey);
            return OBJECT_MAPPER.createObjectNode();
        }
    }

    private int positiveInt(JsonNode extraConfig, String fieldName) {
        JsonNode value = extraConfig.get(fieldName);
        return value != null && value.canConvertToInt() && value.asInt() > 0 ? value.asInt() : 0;
    }

    private String decryptApiKey(String apiKeyCipher) {
        if (secretEncryptor == null) {
            return null;
        }
        return secretEncryptor.decrypt(apiKeyCipher);
    }
}
