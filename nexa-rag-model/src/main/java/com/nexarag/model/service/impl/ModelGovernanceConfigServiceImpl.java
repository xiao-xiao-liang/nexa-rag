package com.nexarag.model.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ClientException;
import com.nexarag.model.dto.ModelGovernanceConfigRequest;
import com.nexarag.model.dto.ModelGovernanceConfigResponse;
import com.nexarag.model.entity.ModelGovernanceConfig;
import com.nexarag.model.mapper.ModelGovernanceConfigMapper;
import com.nexarag.model.service.ModelGovernanceConfigService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 模型治理配置服务实现类，负责模型治理配置的创建、更新和响应转换。
 */
@Service
public class ModelGovernanceConfigServiceImpl
        extends ServiceImpl<ModelGovernanceConfigMapper, ModelGovernanceConfig>
        implements ModelGovernanceConfigService {

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
        return config;
    }

    @Override
    public ModelGovernanceConfigResponse toResponse(ModelGovernanceConfig config) {
        return ModelGovernanceConfigResponse.builder()
                .governanceId(config.getGovernanceId())
                .configId(config.getConfigId())
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
                .eq(ModelGovernanceConfig::getConfigId, configId)
                .one();
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
                .set(ModelGovernanceConfig::getMaxConcurrentCalls, config.getMaxConcurrentCalls())
                .set(ModelGovernanceConfig::getMaxWaitDurationMs, config.getMaxWaitDurationMs())
                .update();
    }

    private void validateConfigId(Long configId) {
        if (configId == null) {
            throw new ClientException("模型配置ID不能为空", BaseErrorCode.PARAM_ERROR);
        }
    }

    private ModelGovernanceConfig defaultConfig(Long configId) {
        return ModelGovernanceConfig.builder()
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
                .maxConcurrentCalls(20)
                .maxWaitDurationMs(0)
                .build();
    }

    private void applyRequest(ModelGovernanceConfig config, ModelGovernanceConfigRequest request) {
        config.setEnabled(request.enabled());
        config.setRetryEnabled(request.retryEnabled());
        config.setMaxAttempts(request.maxAttempts());
        config.setRetryWaitMs(request.retryWaitMs());
        config.setCircuitEnabled(request.circuitEnabled());
        config.setFailureRateThreshold(request.failureRateThreshold());
        config.setSlowCallRateThreshold(request.slowCallRateThreshold());
        config.setSlowCallDurationMs(request.slowCallDurationMs());
        config.setMinimumNumberOfCalls(request.minimumNumberOfCalls());
        config.setSlidingWindowSize(request.slidingWindowSize());
        config.setWaitDurationInOpenStateMs(request.waitDurationInOpenStateMs());
        config.setRateLimitEnabled(request.rateLimitEnabled());
        config.setLimitForPeriod(request.limitForPeriod());
        config.setLimitRefreshPeriodMs(request.limitRefreshPeriodMs());
        config.setTimeoutDurationMs(request.timeoutDurationMs());
        config.setBulkheadEnabled(request.bulkheadEnabled());
        config.setMaxConcurrentCalls(request.maxConcurrentCalls());
        config.setMaxWaitDurationMs(request.maxWaitDurationMs());
    }
}
