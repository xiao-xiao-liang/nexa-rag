package com.nexarag.model.registry;

import com.nexarag.model.entity.ModelConfig;
import com.nexarag.model.entity.ModelGovernanceConfig;
import com.nexarag.model.entity.ModelRoute;
import com.nexarag.model.entity.ModelRouteConfig;
import com.nexarag.model.enums.ModelGovernanceBindingMode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 模型注册表，负责持有当前 JVM 内的模型配置快照。
 */
@Component
public class ModelRegistry {

    private final AtomicReference<ModelRegistrySnapshot> snapshotRef =
            new AtomicReference<>(ModelRegistrySnapshot.empty());

    /**
     * 获取当前模型注册表快照。
     *
     * @return 当前快照
     */
    public ModelRegistrySnapshot current() {
        return snapshotRef.get();
    }

    /**
     * 原子替换当前模型注册表快照。
     *
     * @param snapshot 新快照
     */
    public void replace(ModelRegistrySnapshot snapshot) {
        snapshotRef.set(snapshot == null ? ModelRegistrySnapshot.empty() : snapshot);
    }

    /**
     * 根据模型配置ID查询配置。
     *
     * @param configId 模型配置ID
     * @return 模型配置，不存在返回 null
     */
    public ModelConfig getConfig(Long configId) {
        return current().configMap().get(configId);
    }

    /**
     * 根据路由ID查询路由。
     *
     * @param routeId 路由ID
     * @return 模型路由，不存在返回 null
     */
    public ModelRoute getRoute(Long routeId) {
        return current().routeMap().get(routeId);
    }

    /**
     * 根据路由 key 查询路由。
     *
     * @param routeKey 路由 key
     * @return 模型路由，不存在返回 null
     */
    public ModelRoute getRoute(String routeKey) {
        return current().routeMap().values().stream()
                .filter(route -> route.getRouteKey().equals(routeKey))
                .findFirst()
                .orElse(null);
    }

    /**
     * 查询路由下的模型配置关联列表。
     *
     * @param routeId 路由ID
     * @return 模型配置关联列表
     */
    public List<ModelRouteConfig> getRouteConfigs(Long routeId) {
        return current().routeConfigMap().getOrDefault(routeId, List.of());
    }

    /**
     * 根据治理绑定模式查询治理配置。
     *
     * @param bindingMode 治理绑定模式
     * @param configId    模型配置ID
     * @param routeKey    模型路由 key
     * @return 治理配置，不存在返回 null
     */
    public ModelGovernanceConfig getGovernanceConfig(ModelGovernanceBindingMode bindingMode, Long configId,
                                                     String routeKey) {
        // 1. 根据绑定模式生成与快照一致的索引键
        String key = switch (bindingMode == null ? ModelGovernanceBindingMode.CONFIG : bindingMode) {
            case ROUTE -> routeKey == null || routeKey.isBlank() ? null : "ROUTE:" + routeKey;
            case CONFIG -> configId == null ? null : "CONFIG:" + configId;
        };

        // 2. 缺失必要标识时直接返回空结果
        if (key == null) {
            return null;
        }
        return current().governanceConfigMap().get(key);
    }
}
