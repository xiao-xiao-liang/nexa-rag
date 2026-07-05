package com.nexarag.model.registry;

import com.nexarag.model.entity.ModelConfig;
import com.nexarag.model.entity.ModelRoute;
import com.nexarag.model.entity.ModelRouteConfig;
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
}
