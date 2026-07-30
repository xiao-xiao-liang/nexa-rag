package com.nexarag.model.registry;

import com.nexarag.model.client.ChatClientFactory;
import com.nexarag.model.client.EmbeddingClientFactory;
import com.nexarag.model.client.RerankClientFactory;
import com.nexarag.model.entity.ModelConfig;
import com.nexarag.model.entity.ModelGovernanceConfig;
import com.nexarag.model.entity.ModelRegistryVersion;
import com.nexarag.model.entity.ModelRoute;
import com.nexarag.model.entity.ModelRouteConfig;
import com.nexarag.model.enums.ModelGovernanceBindingMode;
import com.nexarag.model.mapper.ModelRegistryVersionMapper;
import com.nexarag.model.service.ModelConfigService;
import com.nexarag.model.service.ModelGovernanceConfigService;
import com.nexarag.model.service.ModelRouteConfigService;
import com.nexarag.model.service.ModelRouteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 模型注册表刷新器，负责从数据库加载模型配置并替换 JVM 内存快照。
 */
@Service
@RequiredArgsConstructor
public class ModelRegistryRefresher {

    private static final long DEFAULT_VERSION_ID = 1L;

    private final ModelRegistry modelRegistry;
    private final ModelConfigService modelConfigService;
    private final ModelGovernanceConfigService modelGovernanceConfigService;
    private final ModelRouteService modelRouteService;
    private final ModelRouteConfigService modelRouteConfigService;
    private final ModelRegistryVersionMapper modelRegistryVersionMapper;
    private final ChatClientFactory chatClientFactory;
    private final EmbeddingClientFactory embeddingClientFactory;
    private final RerankClientFactory rerankClientFactory;

    /**
     * 远端版本更新时刷新模型注册表。
     *
     * @param remoteVersion 远端版本号
     * @return true 表示执行了刷新
     */
    public boolean refreshIfNewer(long remoteVersion) {
        if (remoteVersion <= modelRegistry.current().versionNo()) {
            return false;
        }

        // 1. 从数据库加载启用的模型配置、路由和关联关系
        List<ModelConfig> configs = modelConfigService.list().stream()
                .filter(config -> Boolean.TRUE.equals(config.getEnabled()))
                .toList();
        List<ModelRoute> routes = modelRouteService.list().stream()
                .filter(route -> Boolean.TRUE.equals(route.getEnabled()))
                .toList();
        List<ModelRouteConfig> routeConfigs = modelRouteConfigService.list().stream()
                .filter(routeConfig -> Boolean.TRUE.equals(routeConfig.getEnabled()))
                .toList();
        List<ModelGovernanceConfig> governanceConfigs = modelGovernanceConfigService.list();

        // 2. 构建不可变快照
        ModelRegistrySnapshot snapshot = new ModelRegistrySnapshot(
                remoteVersion,
                configs.stream().collect(Collectors.toMap(ModelConfig::getConfigId, config -> config)),
                routes.stream().collect(Collectors.toMap(ModelRoute::getRouteId, route -> route)),
                routeConfigs.stream().collect(Collectors.groupingBy(ModelRouteConfig::getRouteId)),
                governanceConfigs.stream()
                        .collect(Collectors.toMap(this::governanceKey, governanceConfig -> governanceConfig,
                                (first, second) -> second))
        );

        // 3. 原子替换快照并清理客户端缓存
        modelRegistry.replace(snapshot);
        chatClientFactory.clear();
        embeddingClientFactory.clear();
        rerankClientFactory.clear();
        return true;
    }

    /**
     * 按数据库当前版本强制刷新模型注册表。
     *
     * @return true 表示执行了刷新
     */
    public boolean refreshCurrentVersion() {
        ModelRegistryVersion version = modelRegistryVersionMapper.selectById(DEFAULT_VERSION_ID);
        long versionNo = version == null ? 0L : version.getVersionNo();
        return refreshIfNewer(versionNo);
    }

    private String governanceKey(ModelGovernanceConfig governanceConfig) {
        // 1. ROUTE 模式按业务路由 key 建立索引
        if (ModelGovernanceBindingMode.ROUTE.equals(governanceConfig.getBindingMode())) {
            return "ROUTE:" + governanceConfig.getRouteKey();
        }

        // 2. 默认按模型配置ID建立索引
        return "CONFIG:" + governanceConfig.getConfigId();
    }
}
