package com.nexarag.model.governance;

import com.nexarag.model.config.ModelGovernanceProperties;
import com.nexarag.model.entity.ModelGovernanceConfig;
import com.nexarag.model.enums.ModelGovernanceBindingMode;
import com.nexarag.model.registry.ModelRegistry;
import com.nexarag.model.route.ModelRouteDecision;
import org.springframework.stereotype.Component;

/**
 * 模型治理配置解析器，负责把路由决策转换为运行时治理参数。
 */
@Component
public class ModelGovernanceResolver {

    private final ModelRegistry modelRegistry;
    private final ModelGovernanceProperties properties;

    /**
     * 创建模型治理配置解析器。
     */
    public ModelGovernanceResolver() {
        this(new ModelRegistry(), new ModelGovernanceProperties());
    }

    /**
     * 创建模型治理配置解析器。
     *
     * @param modelRegistry 模型注册表
     * @param properties    模型治理配置
     */
    public ModelGovernanceResolver(ModelRegistry modelRegistry, ModelGovernanceProperties properties) {
        this.modelRegistry = modelRegistry;
        this.properties = properties;
    }

    /**
     * 解析模型治理配置。
     *
     * @param decision 模型路由决策
     * @return 模型治理执行参数
     */
    public ModelGovernanceSettings resolve(ModelRouteDecision decision) {
        return resolve(null, decision);
    }

    /**
     * 按业务路由解析模型治理配置。
     *
     * @param routeKey  业务路由 key
     * @param decision 模型路由决策
     * @return 模型治理执行参数
     */
    public ModelGovernanceSettings resolve(String routeKey, ModelRouteDecision decision) {
        // 1. 按当前全局绑定模式读取治理配置，同一时间只生效一种模式
        ModelGovernanceBindingMode bindingMode = properties.getGovernance().getBindingMode();
        Long configId = decision == null ? null : decision.configId();
        ModelGovernanceConfig config = modelRegistry.getGovernanceConfig(bindingMode, configId, routeKey);

        // 2. 未配置或未启用治理时，返回关闭全部治理能力的运行时参数
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            return ModelGovernanceSettings.disabled();
        }

        // 3. 将数据库治理配置转换为执行器可消费的运行时参数
        return ModelGovernanceSettings.builder()
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
}
