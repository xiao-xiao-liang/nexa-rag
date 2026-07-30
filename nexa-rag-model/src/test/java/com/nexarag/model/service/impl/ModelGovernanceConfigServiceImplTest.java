package com.nexarag.model.service.impl;

import com.nexarag.model.dto.ModelGovernanceConfigRequest;
import com.nexarag.model.converter.ModelGovernanceConfigConverter;
import com.nexarag.model.entity.ModelGovernanceConfig;
import com.nexarag.model.enums.ModelGovernanceBindingMode;
import com.nexarag.model.refresh.ModelRegistryChangePublisher;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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

    @Test
    void saveDefaultIfAbsentShouldNotOverwriteExistingConfigBinding() {
        TestableModelGovernanceConfigServiceImpl service = new TestableModelGovernanceConfigServiceImpl();
        service.existingConfig = ModelGovernanceConfig.builder()
                .governanceId(100L)
                .bindingMode(ModelGovernanceBindingMode.CONFIG)
                .configId(1L)
                .enabled(Boolean.TRUE)
                .build();

        service.saveDefaultIfAbsent(ModelGovernanceConfig.builder()
                .bindingMode(ModelGovernanceBindingMode.CONFIG)
                .configId(1L)
                .enabled(Boolean.TRUE)
                .build());

        assertThat(service.savedConfig).isNull();
    }

    @Test
    void resetDefaultShouldOverwriteExplicitlyAndPublishRefresh() {
        ModelRegistryChangePublisher publisher = mock(ModelRegistryChangePublisher.class);
        TestableModelGovernanceConfigServiceImpl service = new TestableModelGovernanceConfigServiceImpl(publisher);
        service.existingConfig = ModelGovernanceConfig.builder()
                .governanceId(100L)
                .bindingMode(ModelGovernanceBindingMode.CONFIG)
                .configId(1L)
                .enabled(Boolean.TRUE)
                .maxConcurrentCalls(2)
                .build();
        service.defaultConfig = ModelGovernanceConfig.builder()
                .bindingMode(ModelGovernanceBindingMode.CONFIG)
                .configId(1L)
                .enabled(Boolean.TRUE)
                .maxConcurrentCalls(10)
                .build();

        service.resetDefault(100L);

        assertThat(service.savedConfig.getGovernanceId()).isEqualTo(100L);
        assertThat(service.savedConfig.getMaxConcurrentCalls()).isEqualTo(10);
        verify(publisher).publish(1L);
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
        private ModelGovernanceConfig defaultConfig;
        private ModelGovernanceConfig savedConfig;
        private ModelGovernanceConfig updatedConfig;
        private int registryBumpCount;
        private final ModelRegistryChangePublisher publisher;

        private TestableModelGovernanceConfigServiceImpl() {
            this(null);
        }

        private TestableModelGovernanceConfigServiceImpl(ModelRegistryChangePublisher publisher) {
            super(null, publisher, null, Mappers.getMapper(ModelGovernanceConfigConverter.class));
            this.publisher = publisher;
        }

        @Override
        protected ModelGovernanceConfig findByConfigId(Long configId) {
            return existingConfig;
        }

        @Override
        protected ModelGovernanceConfig findByGovernanceId(Long governanceId) {
            return existingConfig;
        }

        @Override
        public boolean existsConfigBinding(Long configId) {
            return existingConfig != null
                    && existingConfig.getBindingMode() == ModelGovernanceBindingMode.CONFIG
                    && configId.equals(existingConfig.getConfigId());
        }

        @Override
        public boolean existsRouteBinding(String routeKey) {
            return existingConfig != null
                    && existingConfig.getBindingMode() == ModelGovernanceBindingMode.ROUTE
                    && routeKey.equals(existingConfig.getRouteKey());
        }

        @Override
        protected ModelGovernanceConfig createDefault(ModelGovernanceConfig config) {
            return defaultConfig == null ? super.createDefault(config) : defaultConfig;
        }

        @Override
        protected boolean saveGovernanceConfig(ModelGovernanceConfig config) {
            this.savedConfig = config;
            this.existingConfig = config;
            return true;
        }

        @Override
        protected boolean updateGovernanceConfig(ModelGovernanceConfig config) {
            this.updatedConfig = config;
            return true;
        }

        @Override
        protected void deleteGovernanceConfigPhysically(Long governanceId) {
            // 1. 单元测试不执行数据库物理删除
        }

        @Override
        protected long bumpRegistryVersionAndPublish() {
            this.registryBumpCount++;
            if (publisher != null) {
                publisher.publish(registryBumpCount);
            }
            return registryBumpCount;
        }
    }
}
