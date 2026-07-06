package com.nexarag.model.governance;

import com.nexarag.model.route.ModelRouteDecision;
import org.springframework.stereotype.Component;

/**
 * 模型治理配置解析器，负责把路由决策转换为运行时治理参数。
 */
@Component
public class ModelGovernanceResolver {

    /**
     * 解析模型治理配置。
     *
     * @param decision 模型路由决策
     * @return 模型治理执行参数
     */
    public ModelGovernanceSettings resolve(ModelRouteDecision decision) {
        // 1. 初版先返回默认关闭配置，后续接入 model_governance_config 表
        return ModelGovernanceSettings.builder()
                .retryEnabled(false)
                .circuitEnabled(false)
                .rateLimitEnabled(false)
                .bulkheadEnabled(false)
                .build();
    }
}
