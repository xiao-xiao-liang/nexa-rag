package com.nexarag.model.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ClientException;
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

    @Override
    public ModelGovernanceConfig getByConfigId(Long configId) {
        validateConfigId(configId);

        // 1. 查询已有治理配置，缺失时返回默认关闭配置，方便前端回显
        ModelGovernanceConfig config = findByConfigId(configId);
        return config == null ? defaultConfig(configId) : config;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModelGovernanceConfig saveByConfigId(Long configId, ModelGovernanceConfigRequest request) {
        validateConfigId(configId);
        if (request == null) {
            throw new ClientException("模型治理配置请求不能为空", BaseErrorCode.PARAM_ERROR);
        }

        // 1. 查询已有配置，存在则更新，不存在则创建
        ModelGovernanceConfig config = findByConfigId(configId);
        boolean create = config == null;
        if (create) {
            config = defaultConfig(configId);
            config.setGovernanceId(IdWorker.getId());
        }

        // 2. 应用请求参数并持久化
        applyRequest(config, request);
        if (create) {
            saveGovernanceConfig(config);
        } else {
            updateGovernanceConfig(config);
        }
        bumpRegistryVersionAndPublish();
        return config;
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

        // 2. 保留原治理配置ID并覆盖治理参数
        defaults.setGovernanceId(existing.getGovernanceId());
        updateGovernanceConfig(defaults);

        // 3. 发布模型注册表刷新
        bumpRegistryVersionAndPublish();
    }

    @Override
    public ModelGovernanceConfigResponse toResponse(ModelGovernanceConfig config) {
        return ModelGovernanceConfigResponse.builder()
                .governanceId(config.getGovernanceId())
                .bindingMode(config.getBindingMode())
                .configId(config.getConfigId())
                .routeKey(config.getRouteKey())
                .enabled(config.getEnabled())
                .retryEnabled(config.getRetryEnabled())
                .maxAttempts(config.getMaxAttempts())
                .retryWaitMs(config.getRetryWaitMs())
                .circuitEnabled(config.getCircuitEnabled())
                .failureRateThreshold(config.getFailureRateThreshold())
                .slowCallRateThreshold(config.getSlowCallRateThreshold())
                .slowCallDurationMs(config.getSlowCallDurationMs())
                .minimumNumberOfCalls(config.getMinimumNumberOfCalls())
                .slidingWindowSize(config.getSlidingWindowSize())
                .waitDurationInOpenStateMs(config.getWaitDurationInOpenStateMs())
                .rateLimitEnabled(config.getRateLimitEnabled())
                .limitForPeriod(config.getLimitForPeriod())
                .limitRefreshPeriodMs(config.getLimitRefreshPeriodMs())
                .timeoutDurationMs(config.getTimeoutDurationMs())
                .bulkheadEnabled(config.getBulkheadEnabled())
                .timeLimiterEnabled(config.getTimeLimiterEnabled())
                .timeLimiterTimeoutMs(config.getTimeLimiterTimeoutMs())
                .streamFirstChunkTimeoutMs(config.getStreamFirstChunkTimeoutMs())
                .streamMaxDurationMs(config.getStreamMaxDurationMs())
                .maxConcurrentCalls(config.getMaxConcurrentCalls())
                .maxWaitDurationMs(config.getMaxWaitDurationMs())
                .build();
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
        return this.lambdaUpdate()
                .eq(ModelGovernanceConfig::getGovernanceId, config.getGovernanceId())
                .set(ModelGovernanceConfig::getEnabled, config.getEnabled())
                .set(ModelGovernanceConfig::getBindingMode, config.getBindingMode())
                .set(ModelGovernanceConfig::getConfigId, config.getConfigId())
                .set(ModelGovernanceConfig::getRouteKey, config.getRouteKey())
                .set(ModelGovernanceConfig::getRetryEnabled, config.getRetryEnabled())
                .set(ModelGovernanceConfig::getMaxAttempts, config.getMaxAttempts())
                .set(ModelGovernanceConfig::getRetryWaitMs, config.getRetryWaitMs())
                .set(ModelGovernanceConfig::getCircuitEnabled, config.getCircuitEnabled())
                .set(ModelGovernanceConfig::getFailureRateThreshold, config.getFailureRateThreshold())
                .set(ModelGovernanceConfig::getSlowCallRateThreshold, config.getSlowCallRateThreshold())
                .set(ModelGovernanceConfig::getSlowCallDurationMs, config.getSlowCallDurationMs())
                .set(ModelGovernanceConfig::getMinimumNumberOfCalls, config.getMinimumNumberOfCalls())
                .set(ModelGovernanceConfig::getSlidingWindowSize, config.getSlidingWindowSize())
                .set(ModelGovernanceConfig::getWaitDurationInOpenStateMs, config.getWaitDurationInOpenStateMs())
                .set(ModelGovernanceConfig::getRateLimitEnabled, config.getRateLimitEnabled())
                .set(ModelGovernanceConfig::getLimitForPeriod, config.getLimitForPeriod())
                .set(ModelGovernanceConfig::getLimitRefreshPeriodMs, config.getLimitRefreshPeriodMs())
                .set(ModelGovernanceConfig::getTimeoutDurationMs, config.getTimeoutDurationMs())
                .set(ModelGovernanceConfig::getBulkheadEnabled, config.getBulkheadEnabled())
                .set(ModelGovernanceConfig::getTimeLimiterEnabled, config.getTimeLimiterEnabled())
                .set(ModelGovernanceConfig::getTimeLimiterTimeoutMs, config.getTimeLimiterTimeoutMs())
                .set(ModelGovernanceConfig::getStreamFirstChunkTimeoutMs, config.getStreamFirstChunkTimeoutMs())
                .set(ModelGovernanceConfig::getStreamMaxDurationMs, config.getStreamMaxDurationMs())
                .set(ModelGovernanceConfig::getMaxConcurrentCalls, config.getMaxConcurrentCalls())
                .set(ModelGovernanceConfig::getMaxWaitDurationMs, config.getMaxWaitDurationMs())
                .update();
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

    private ModelGovernanceConfig defaultConfig(Long configId) {
        return ModelGovernanceConfig.builder()
                .bindingMode(ModelGovernanceBindingMode.CONFIG)
                .configId(configId)
                .enabled(false)
                .retryEnabled(false)
                .maxAttempts(1)
                .retryWaitMs(0)
                .circuitEnabled(false)
                .failureRateThreshold(50)
                .slowCallRateThreshold(100)
                .slowCallDurationMs(3000)
                .minimumNumberOfCalls(10)
                .slidingWindowSize(20)
                .waitDurationInOpenStateMs(30000)
                .rateLimitEnabled(false)
                .limitForPeriod(100)
                .limitRefreshPeriodMs(1000)
                .timeoutDurationMs(0)
                .bulkheadEnabled(false)
                .timeLimiterEnabled(false)
                .timeLimiterTimeoutMs(60000)
                .streamFirstChunkTimeoutMs(30000)
                .streamMaxDurationMs(300000)
                .maxConcurrentCalls(20)
                .maxWaitDurationMs(0)
                .build();
    }

    private void applyRequest(ModelGovernanceConfig config, ModelGovernanceConfigRequest request) {
        if (request.enabled() != null) config.setEnabled(request.enabled());
        if (request.retryEnabled() != null) config.setRetryEnabled(request.retryEnabled());
        if (request.maxAttempts() != null) config.setMaxAttempts(request.maxAttempts());
        if (request.retryWaitMs() != null) config.setRetryWaitMs(request.retryWaitMs());
        if (request.circuitEnabled() != null) config.setCircuitEnabled(request.circuitEnabled());
        if (request.failureRateThreshold() != null) config.setFailureRateThreshold(request.failureRateThreshold());
        config.setSlowCallRateThreshold(request.slowCallRateThreshold() != null ? request.slowCallRateThreshold() : (config.getSlowCallRateThreshold() != null ? config.getSlowCallRateThreshold() : 100));
        if (request.slowCallDurationMs() != null) config.setSlowCallDurationMs(request.slowCallDurationMs());
        config.setMinimumNumberOfCalls(request.minimumNumberOfCalls() != null ? request.minimumNumberOfCalls() : (config.getMinimumNumberOfCalls() != null ? config.getMinimumNumberOfCalls() : 10));
        config.setSlidingWindowSize(request.slidingWindowSize() != null ? request.slidingWindowSize() : (config.getSlidingWindowSize() != null ? config.getSlidingWindowSize() : 20));
        config.setWaitDurationInOpenStateMs(request.waitDurationInOpenStateMs() != null ? request.waitDurationInOpenStateMs() : (config.getWaitDurationInOpenStateMs() != null ? config.getWaitDurationInOpenStateMs() : 30000));
        if (request.rateLimitEnabled() != null) config.setRateLimitEnabled(request.rateLimitEnabled());
        if (request.limitForPeriod() != null) config.setLimitForPeriod(request.limitForPeriod());
        config.setLimitRefreshPeriodMs(request.limitRefreshPeriodMs() != null ? request.limitRefreshPeriodMs() : (config.getLimitRefreshPeriodMs() != null ? config.getLimitRefreshPeriodMs() : 1000));
        config.setTimeoutDurationMs(request.timeoutDurationMs() != null ? request.timeoutDurationMs() : (config.getTimeoutDurationMs() != null ? config.getTimeoutDurationMs() : 0));
        if (request.bulkheadEnabled() != null) config.setBulkheadEnabled(request.bulkheadEnabled());
        if (request.timeLimiterEnabled() != null) config.setTimeLimiterEnabled(request.timeLimiterEnabled());
        config.setTimeLimiterTimeoutMs(request.timeLimiterTimeoutMs() != null ? request.timeLimiterTimeoutMs() : (config.getTimeLimiterTimeoutMs() != null ? config.getTimeLimiterTimeoutMs() : 60000));
        if (request.streamFirstChunkTimeoutMs() != null) config.setStreamFirstChunkTimeoutMs(request.streamFirstChunkTimeoutMs());
        if (request.streamMaxDurationMs() != null) config.setStreamMaxDurationMs(request.streamMaxDurationMs());
        if (request.maxConcurrentCalls() != null) config.setMaxConcurrentCalls(request.maxConcurrentCalls());
        config.setMaxWaitDurationMs(request.maxWaitDurationMs() != null ? request.maxWaitDurationMs() : (config.getMaxWaitDurationMs() != null ? config.getMaxWaitDurationMs() : 0));
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
