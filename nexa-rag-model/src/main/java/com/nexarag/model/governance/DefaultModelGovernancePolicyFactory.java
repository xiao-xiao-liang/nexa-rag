package com.nexarag.model.governance;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.nexarag.model.entity.ModelGovernanceConfig;
import com.nexarag.model.enums.ModelGovernanceBindingMode;
import com.nexarag.model.enums.ModelType;
import org.springframework.stereotype.Component;

/**
 * 默认模型治理策略工厂，负责按模型类型创建开箱即用的治理配置。
 */
@Component
public class DefaultModelGovernancePolicyFactory {

    /**
     * 为模型配置创建默认治理配置。
     *
     * @param configId  模型配置ID
     * @param modelType 模型类型
     * @return 默认治理配置
     */
    public ModelGovernanceConfig createForConfig(Long configId, ModelType modelType) {
        // 1. 创建基础治理配置
        ModelGovernanceConfig config = createBase(modelType);

        // 2. 绑定到指定模型配置
        config.setBindingMode(ModelGovernanceBindingMode.CONFIG);
        config.setConfigId(configId);
        return config;
    }

    /**
     * 为业务路由创建默认治理配置。
     *
     * @param routeKey  业务路由 key
     * @param modelType 模型类型
     * @return 默认治理配置
     */
    public ModelGovernanceConfig createForRoute(String routeKey, ModelType modelType) {
        // 1. 创建基础治理配置
        ModelGovernanceConfig config = createBase(modelType);

        // 2. 绑定到指定业务路由
        config.setBindingMode(ModelGovernanceBindingMode.ROUTE);
        config.setRouteKey(routeKey);
        return config;
    }

    private ModelGovernanceConfig createBase(ModelType modelType) {
        // 1. 仅创建数据库默认值之外的基础标识
        ModelGovernanceConfig config = ModelGovernanceConfig.builder()
                .governanceId(IdWorker.getId())
                .build();

        // 2. 按不同模型类型调整吞吐和超时参数
        if (ModelType.EMBEDDING.equals(modelType)) {
            applyEmbeddingDefaults(config);
        } else if (ModelType.RERANK.equals(modelType)) {
            applyRerankDefaults(config);
        }
        return config;
    }

    private void applyEmbeddingDefaults(ModelGovernanceConfig config) {
        config.setRetryEnabled(Boolean.TRUE);
        config.setMaxAttempts(2);
        config.setLimitForPeriod(200);
        config.setMaxConcurrentCalls(30);
        config.setTimeLimiterTimeoutMs(60000);
        config.setStreamFirstChunkTimeoutMs(0);
        config.setStreamMaxDurationMs(0);
    }

    private void applyRerankDefaults(ModelGovernanceConfig config) {
        config.setRetryEnabled(Boolean.TRUE);
        config.setMaxAttempts(2);
        config.setLimitForPeriod(120);
        config.setMaxConcurrentCalls(20);
        config.setTimeLimiterTimeoutMs(30000);
        config.setStreamFirstChunkTimeoutMs(0);
        config.setStreamMaxDurationMs(0);
    }
}
