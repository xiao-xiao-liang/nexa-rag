package com.nexarag.model.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ClientException;
import com.nexarag.model.converter.ModelGovernanceConfigConverter;
import com.nexarag.model.dto.ModelGovernanceConfigRequest;
import com.nexarag.model.dto.ModelGovernanceConfigResponse;
import com.nexarag.model.entity.ModelGovernanceConfig;
import com.nexarag.model.entity.ModelRegistryVersion;
import com.nexarag.model.enums.ModelGovernanceBindingMode;
import com.nexarag.model.enums.ModelType;
import com.nexarag.model.governance.DefaultModelGovernancePolicyFactory;
import com.nexarag.model.mapper.ModelGovernanceConfigMapper;
import com.nexarag.model.mapper.ModelRegistryVersionMapper;
import com.nexarag.model.refresh.ModelRegistryChangePublisher;
import com.nexarag.model.service.ModelGovernanceConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 模型治理配置服务实现类，负责模型治理配置的创建、更新和响应转换。
 */
@Service
@RequiredArgsConstructor
public class ModelGovernanceConfigServiceImpl
        extends ServiceImpl<ModelGovernanceConfigMapper, ModelGovernanceConfig>
        implements ModelGovernanceConfigService {

    private static final long INITIAL_REGISTRY_VERSION = 1L;
    private static final long DEFAULT_REGISTRY_VERSION_ID = 1L;

    private final ModelRegistryVersionMapper modelRegistryVersionMapper;
    private final ModelRegistryChangePublisher modelRegistryChangePublisher;
    private final DefaultModelGovernancePolicyFactory defaultModelGovernancePolicyFactory;
    private final ModelGovernanceConfigConverter modelGovernanceConfigConverter;

    @Override
    public ModelGovernanceConfig getByConfigId(Long configId) {
        validateConfigId(configId);

        // 1. 查询已保存治理配置，不创建内存默认值
        return findByConfigId(configId);
    }

    @Override
    public ModelGovernanceConfig getByRouteKey(String routeKey) {
        validateRouteKey(routeKey);

        // 1. 查询已保存路由级治理配置
        return findByRouteKey(routeKey);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModelGovernanceConfig saveByConfigId(Long configId, ModelGovernanceConfigRequest request) {
        validateConfigId(configId);
        if (request == null) {
            throw new ClientException("模型治理配置请求不能为空", BaseErrorCode.PARAM_ERROR);
        }

        // 1. 查询已有配置，存在则更新，不存在则创建最小绑定实体
        ModelGovernanceConfig config = findByConfigId(configId);
        boolean create = config == null;
        if (create) {
            config = ModelGovernanceConfig.builder()
                    .governanceId(IdWorker.getId())
                    .bindingMode(ModelGovernanceBindingMode.CONFIG)
                    .configId(configId)
                    .build();
        }

        // 2. 应用非空策略字段并持久化
        modelGovernanceConfigConverter.patch(request, config);
        if (create) {
            saveGovernanceConfig(config);
        } else {
            updateGovernanceConfig(config);
        }
        bumpRegistryVersionAndPublish();
        return create ? findByConfigId(configId) : config;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModelGovernanceConfig saveByRouteKey(String routeKey, ModelGovernanceConfigRequest request) {
        validateRouteKey(routeKey);
        if (request == null) {
            throw new ClientException("模型治理配置请求不能为空", BaseErrorCode.PARAM_ERROR);
        }

        // 1. 查询已有路由级配置，缺失时创建最小绑定实体
        ModelGovernanceConfig config = findByRouteKey(routeKey);
        boolean create = config == null;
        if (create) {
            config = ModelGovernanceConfig.builder()
                    .governanceId(IdWorker.getId())
                    .bindingMode(ModelGovernanceBindingMode.ROUTE)
                    .routeKey(routeKey)
                    .build();
        }

        // 2. 应用非空策略字段并持久化
        modelGovernanceConfigConverter.patch(request, config);
        if (create) {
            saveGovernanceConfig(config);
        } else {
            updateGovernanceConfig(config);
        }
        bumpRegistryVersionAndPublish();
        return create ? findByRouteKey(routeKey) : config;
    }

    @Override
    public void renameRouteBinding(String oldRouteKey, String newRouteKey) {
        if (oldRouteKey == null || oldRouteKey.equals(newRouteKey)) {
            return;
        }

        // 1. 仅迁移对应旧路由标识的路由级治理配置
        this.lambdaUpdate()
                .eq(ModelGovernanceConfig::getBindingMode, ModelGovernanceBindingMode.ROUTE)
                .eq(ModelGovernanceConfig::getRouteKey, oldRouteKey)
                .set(ModelGovernanceConfig::getRouteKey, newRouteKey)
                .update();
    }

    @Override
    public boolean existsConfigBinding(Long configId) {
        if (configId == null) {
            return false;
        }

        // 1. 按 CONFIG 绑定模式和模型配置ID判断是否已存在
        return this.lambdaQuery()
                .eq(ModelGovernanceConfig::getBindingMode, ModelGovernanceBindingMode.CONFIG)
                .eq(ModelGovernanceConfig::getConfigId, configId)
                .exists();
    }

    @Override
    public boolean existsRouteBinding(String routeKey) {
        if (routeKey == null || routeKey.isBlank()) {
            return false;
        }

        // 1. 按 ROUTE 绑定模式和路由 key 判断是否已存在
        return this.lambdaQuery()
                .eq(ModelGovernanceConfig::getBindingMode, ModelGovernanceBindingMode.ROUTE)
                .eq(ModelGovernanceConfig::getRouteKey, routeKey)
                .exists();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveDefaultIfAbsent(ModelGovernanceConfig config) {
        if (config == null) {
            return;
        }

        // 1. 已存在对应绑定时不覆盖用户配置
        if (isExistingBinding(config)) {
            return;
        }

        // 2. 保存默认治理配置并发布刷新
        saveGovernanceConfig(config);
        bumpRegistryVersionAndPublish();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetDefault(Long governanceId) {
        if (governanceId == null) {
            throw new ClientException("模型治理配置ID不能为空", BaseErrorCode.PARAM_ERROR);
        }
        ModelGovernanceConfig existing = findByGovernanceId(governanceId);
        if (existing == null) {
            throw new ClientException("模型治理配置不存在，governanceId=" + governanceId, BaseErrorCode.PARAM_ERROR);
        }

        // 1. 基于现有绑定信息生成默认治理配置
        ModelGovernanceConfig defaults = createDefault(existing);

        // 2. 保留原治理配置ID，以数据库默认值重建治理配置
        defaults.setGovernanceId(existing.getGovernanceId());
        deleteGovernanceConfigPhysically(existing.getGovernanceId());
        saveGovernanceConfig(defaults);

        // 3. 发布模型注册表刷新
        bumpRegistryVersionAndPublish();
    }

    @Override
    public ModelGovernanceConfigResponse toResponse(ModelGovernanceConfig config) {
        return modelGovernanceConfigConverter.toResponse(config);
    }

    /**
     * 按模型配置ID查询治理配置。
     *
     * @param configId 模型配置ID
     * @return 治理配置
     */
    protected ModelGovernanceConfig findByConfigId(Long configId) {
        return this.lambdaQuery()
                .eq(ModelGovernanceConfig::getBindingMode, ModelGovernanceBindingMode.CONFIG)
                .eq(ModelGovernanceConfig::getConfigId, configId)
                .one();
    }

    /**
     * 按路由标识查询治理配置。
     *
     * @param routeKey 路由标识
     * @return 治理配置
     */
    protected ModelGovernanceConfig findByRouteKey(String routeKey) {
        return this.lambdaQuery()
                .eq(ModelGovernanceConfig::getBindingMode, ModelGovernanceBindingMode.ROUTE)
                .eq(ModelGovernanceConfig::getRouteKey, routeKey)
                .one();
    }

    /**
     * 按治理配置ID查询治理配置。
     *
     * @param governanceId 治理配置ID
     * @return 治理配置
     */
    protected ModelGovernanceConfig findByGovernanceId(Long governanceId) {
        return this.getById(governanceId);
    }

    /**
     * 保存治理配置。
     *
     * @param config 治理配置
     * @return true 表示保存成功
     */
    protected boolean saveGovernanceConfig(ModelGovernanceConfig config) {
        return this.save(config);
    }

    /**
     * 更新治理配置。
     *
     * @param config 治理配置
     * @return true 表示更新成功
     */
    protected boolean updateGovernanceConfig(ModelGovernanceConfig config) {
        return this.updateById(config);
    }

    /**
     * 物理删除治理配置，以便重建时由数据库填充默认值。
     *
     * @param governanceId 治理配置ID
     */
    protected void deleteGovernanceConfigPhysically(Long governanceId) {
        baseMapper.deletePhysicallyByGovernanceId(governanceId);
    }

    /**
     * 按现有绑定信息创建默认治理配置。
     *
     * @param config 现有治理配置
     * @return 默认治理配置
     */
    protected ModelGovernanceConfig createDefault(ModelGovernanceConfig config) {
        if (ModelGovernanceBindingMode.ROUTE.equals(config.getBindingMode())) {
            return defaultModelGovernancePolicyFactory.createForRoute(config.getRouteKey(), ModelType.CHAT);
        }
        return defaultModelGovernancePolicyFactory.createForConfig(config.getConfigId(), ModelType.CHAT);
    }

    /**
     * 递增模型注册表版本并发布刷新消息。
     *
     * @return 最新模型注册表版本号
     */
    protected long bumpRegistryVersionAndPublish() {
        if (modelRegistryVersionMapper == null || modelRegistryChangePublisher == null) {
            return INITIAL_REGISTRY_VERSION;
        }

        // 1. 写入最新模型注册表版本
        ModelRegistryVersion version = modelRegistryVersionMapper.selectById(DEFAULT_REGISTRY_VERSION_ID);
        long nextVersionNo = version == null ? INITIAL_REGISTRY_VERSION : version.getVersionNo() + 1;
        ModelRegistryVersion nextVersion = new ModelRegistryVersion();
        nextVersion.setVersionId(DEFAULT_REGISTRY_VERSION_ID);
        nextVersion.setVersionNo(nextVersionNo);
        if (version == null) {
            modelRegistryVersionMapper.insert(nextVersion);
        } else {
            modelRegistryVersionMapper.updateById(nextVersion);
        }

        // 2. 发布模型注册表刷新消息
        modelRegistryChangePublisher.publish(nextVersionNo);
        return nextVersionNo;
    }

    private void validateConfigId(Long configId) {
        if (configId == null) {
            throw new ClientException("模型配置ID不能为空", BaseErrorCode.PARAM_ERROR);
        }
    }

    private void validateRouteKey(String routeKey) {
        if (routeKey == null || routeKey.isBlank()) {
            throw new ClientException("模型路由标识不能为空", BaseErrorCode.PARAM_ERROR);
        }
    }

    private boolean isExistingBinding(ModelGovernanceConfig config) {
        // 1. ROUTE 模式按路由 key 判断
        if (ModelGovernanceBindingMode.ROUTE.equals(config.getBindingMode())) {
            return existsRouteBinding(config.getRouteKey());
        }

        // 2. 默认按模型配置ID判断
        return existsConfigBinding(config.getConfigId());
    }
}
