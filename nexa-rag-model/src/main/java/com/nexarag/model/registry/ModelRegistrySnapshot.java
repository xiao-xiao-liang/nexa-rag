package com.nexarag.model.registry;

import com.nexarag.model.entity.ModelConfig;
import com.nexarag.model.entity.ModelRoute;
import com.nexarag.model.entity.ModelRouteConfig;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 模型注册表不可变快照，保存某一版本下可用的模型配置、路由和关联关系。
 */
public record ModelRegistrySnapshot(long versionNo, Map<Long, ModelConfig> configMap,
                                    Map<Long, ModelRoute> routeMap,
                                    Map<Long, List<ModelRouteConfig>> routeConfigMap) {

    /**
     * 创建不可变模型注册表快照。
     *
     * @param versionNo      全局版本号
     * @param configMap      模型配置映射
     * @param routeMap       模型路由映射
     * @param routeConfigMap 路由配置关联映射
     */
    public ModelRegistrySnapshot {
        configMap = Map.copyOf(configMap == null ? Map.of() : configMap);
        routeMap = Map.copyOf(routeMap == null ? Map.of() : routeMap);
        routeConfigMap = copyRouteConfigMap(routeConfigMap);
    }

    /**
     * 创建空快照。
     *
     * @return 空模型注册表快照
     */
    public static ModelRegistrySnapshot empty() {
        return new ModelRegistrySnapshot(0L, Map.of(), Map.of(), Map.of());
    }

    private static Map<Long, List<ModelRouteConfig>> copyRouteConfigMap(
            Map<Long, List<ModelRouteConfig>> routeConfigMap) {
        if (routeConfigMap == null || routeConfigMap.isEmpty()) {
            return Map.of();
        }
        return routeConfigMap.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
    }
}
