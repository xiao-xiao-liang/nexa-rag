package com.nexarag.model.service.impl;

import com.nexarag.model.dto.ModelGovernanceConfigRequest;
import com.nexarag.model.entity.ModelGovernanceConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模型治理配置服务实现测试。
 */
class ModelGovernanceConfigServiceImplTest {

    @Test
    void saveByConfigIdShouldCreateGovernanceConfigWhenMissing() {
        TestableModelGovernanceConfigServiceImpl service = new TestableModelGovernanceConfigServiceImpl();

        ModelGovernanceConfig config = service.saveByConfigId(1L, request());

        assertThat(config.getGovernanceId()).isNotNull();
        assertThat(config.getConfigId()).isEqualTo(1L);
        assertThat(config.getEnabled()).isTrue();
        assertThat(config.getRetryEnabled()).isTrue();
        assertThat(config.getMaxAttempts()).isEqualTo(2);
        assertThat(service.savedConfig).isSameAs(config);
    }

    @Test
    void saveByConfigIdShouldUpdateGovernanceConfigWhenExists() {
        TestableModelGovernanceConfigServiceImpl service = new TestableModelGovernanceConfigServiceImpl();
        service.existingConfig = ModelGovernanceConfig.builder()
                .governanceId(100L)
                .configId(1L)
                .enabled(false)
                .retryEnabled(false)
                .maxAttempts(1)
                .build();

        ModelGovernanceConfig config = service.saveByConfigId(1L, request());

        assertThat(config.getGovernanceId()).isEqualTo(100L);
        assertThat(config.getConfigId()).isEqualTo(1L);
        assertThat(config.getEnabled()).isTrue();
        assertThat(config.getRetryEnabled()).isTrue();
        assertThat(config.getMaxAttempts()).isEqualTo(2);
        assertThat(service.updatedConfig).isSameAs(config);
    }

    private ModelGovernanceConfigRequest request() {
        return ModelGovernanceConfigRequest.builder()
                .enabled(true)
                .retryEnabled(true)
                .maxAttempts(2)
                .retryWaitMs(100)
                .circuitEnabled(true)
                .failureRateThreshold(50)
                .slowCallRateThreshold(50)
                .slowCallDurationMs(3000)
                .minimumNumberOfCalls(10)
                .slidingWindowSize(20)
                .waitDurationInOpenStateMs(30000)
                .rateLimitEnabled(true)
                .limitForPeriod(20)
                .limitRefreshPeriodMs(1000)
                .timeoutDurationMs(100)
                .bulkheadEnabled(true)
                .maxConcurrentCalls(10)
                .maxWaitDurationMs(100)
                .build();
    }

    private static class TestableModelGovernanceConfigServiceImpl extends ModelGovernanceConfigServiceImpl {

        private ModelGovernanceConfig existingConfig;
        private ModelGovernanceConfig savedConfig;
        private ModelGovernanceConfig updatedConfig;

        @Override
        protected ModelGovernanceConfig findByConfigId(Long configId) {
            return existingConfig;
        }

        @Override
        protected boolean saveGovernanceConfig(ModelGovernanceConfig config) {
            this.savedConfig = config;
            return true;
        }

        @Override
        protected boolean updateGovernanceConfig(ModelGovernanceConfig config) {
            this.updatedConfig = config;
            return true;
        }
    }
}
