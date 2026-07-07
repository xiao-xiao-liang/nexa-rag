package com.nexarag.model.service.impl;

import com.nexarag.common.exception.ClientException;
import com.nexarag.model.dto.ModelConfigCreateRequest;
import com.nexarag.model.dto.ModelConfigUpdateRequest;
import com.nexarag.model.config.ModelGovernanceProperties;
import com.nexarag.model.entity.ModelConfig;
import com.nexarag.model.entity.ModelGovernanceConfig;
import com.nexarag.model.enums.ModelGovernanceBindingMode;
import com.nexarag.model.enums.ModelProvider;
import com.nexarag.model.enums.ModelType;
import com.nexarag.model.governance.DefaultModelGovernancePolicyFactory;
import com.nexarag.model.security.ModelSecretEncryptor;
import com.nexarag.model.service.ModelGovernanceConfigService;
import com.nexarag.model.service.ModelRouteConfigService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 模型配置服务实现测试。
 */
class ModelConfigServiceImplTest {

    @Test
    void createConfigShouldEncryptAndMaskApiKey() {
        TestableModelConfigServiceImpl service = new TestableModelConfigServiceImpl();

        ModelConfig config = service.createConfig(ModelConfigCreateRequest.builder()
                .configKey("embedding.openai")
                .modelType(ModelType.EMBEDDING)
                .provider(ModelProvider.OPENAI)
                .baseUrl("https://api.openai.com/v1")
                .endpointPath("/embeddings")
                .apiKey("sk-test-abcdef")
                .modelName("text-embedding-3-small")
                .timeoutMs(30000)
                .maxRetries(0)
                .build());

        assertThat(config.getConfigId()).isNotNull();
        assertThat(config.getApiKeyCipher()).isNotBlank();
        assertThat(service.encryptor.decrypt(config.getApiKeyCipher())).isEqualTo("sk-test-abcdef");
        assertThat(config.getApiKeyMask()).isEqualTo("sk-****cdef");
        assertThat(config.getEndpointPath()).isEqualTo("/embeddings");
        assertThat(service.savedConfig).isSameAs(config);
        assertThat(service.registryBumpCount).isEqualTo(1);
    }

    @Test
    void createConfigShouldAutoCreateDefaultGovernanceWhenEnabled() {
        DefaultModelGovernancePolicyFactory policyFactory = mock(DefaultModelGovernancePolicyFactory.class);
        ModelGovernanceConfigService governanceConfigService = mock(ModelGovernanceConfigService.class);
        ModelGovernanceProperties properties = new ModelGovernanceProperties();
        properties.getGovernance().setAutoCreateDefault(Boolean.TRUE);
        when(policyFactory.createForConfig(anyLong(), eq(ModelType.CHAT))).thenAnswer(invocation ->
                ModelGovernanceConfig.builder()
                        .bindingMode(ModelGovernanceBindingMode.CONFIG)
                        .configId(invocation.getArgument(0))
                        .enabled(Boolean.TRUE)
                        .build());

        TestableModelConfigServiceImpl service = new TestableModelConfigServiceImpl(policyFactory,
                governanceConfigService, properties);
        ModelConfig created = service.createConfig(ModelConfigCreateRequest.builder()
                .configKey("chat.openai")
                .modelType(ModelType.CHAT)
                .provider(ModelProvider.OPENAI)
                .baseUrl("https://api.openai.com/v1")
                .apiKey("sk-chat")
                .modelName("gpt-4o-mini")
                .build());

        verify(governanceConfigService).saveDefaultIfAbsent(argThat(config ->
                config.getBindingMode() == ModelGovernanceBindingMode.CONFIG
                        && created.getConfigId().equals(config.getConfigId())));
    }

    @Test
    void updateConfigShouldEncryptApiKeyWhenExistingSecretIsMissing() {
        TestableModelConfigServiceImpl service = new TestableModelConfigServiceImpl();
        service.existingConfig = ModelConfig.builder()
                .configId(1L)
                .configKey("chat.openai")
                .modelType(ModelType.CHAT)
                .provider(ModelProvider.OPENAI)
                .baseUrl("https://api.openai.com/v1")
                .apiKeyCipher(null)
                .apiKeyMask(null)
                .modelName("gpt-4o-mini")
                .timeoutMs(30000)
                .maxRetries(0)
                .version(1L)
                .build();

        ModelConfig config = service.updateConfig(1L, ModelConfigUpdateRequest.builder()
                .apiKey("sk-new-secret")
                .timeoutMs(50000)
                .maxRetries(3)
                .build());

        assertThat(config.getApiKeyCipher()).isNotBlank();
        assertThat(service.encryptor.decrypt(config.getApiKeyCipher())).isEqualTo("sk-new-secret");
        assertThat(config.getApiKeyMask()).isEqualTo("sk-****cret");
        assertThat(config.getTimeoutMs()).isEqualTo(50000);
        assertThat(config.getMaxRetries()).isEqualTo(3);
        assertThat(service.updatedConfig).isSameAs(config);
    }

    @Test
    void createConfigShouldNormalizeEndpointPathByModelType() {
        TestableModelConfigServiceImpl service = new TestableModelConfigServiceImpl();

        ModelConfig chatConfig = service.createConfig(ModelConfigCreateRequest.builder()
                .configKey("chat.openai")
                .modelType(ModelType.CHAT)
                .provider(ModelProvider.OPENAI)
                .baseUrl("https://api.openai.com/v1")
                .apiKey("sk-chat")
                .modelName("gpt-4o-mini")
                .build());

        assertThat(chatConfig.getEndpointPath()).isEqualTo("/chat/completions");

        ModelConfig embeddingConfig = service.createConfig(ModelConfigCreateRequest.builder()
                .configKey("embedding.openai")
                .modelType(ModelType.EMBEDDING)
                .provider(ModelProvider.OPENAI)
                .baseUrl("https://api.openai.com/v1")
                .apiKey("sk-embedding")
                .modelName("text-embedding-3-small")
                .build());

        assertThat(embeddingConfig.getEndpointPath()).isEqualTo("/embeddings");
    }

    @Test
    void updateConfigShouldPreserveSecretWhenApiKeyMissing() {
        TestableModelConfigServiceImpl service = new TestableModelConfigServiceImpl();
        service.existingConfig = ModelConfig.builder()
                .configId(1L)
                .configKey("embedding.openai")
                .modelType(ModelType.EMBEDDING)
                .provider(ModelProvider.OPENAI)
                .baseUrl("https://api.openai.com/v1")
                .endpointPath("/embeddings")
                .apiKeyCipher("cipher-old")
                .apiKeyMask("sk-****1111")
                .modelName("text-embedding-3-small")
                .timeoutMs(30000)
                .maxRetries(0)
                .version(1L)
                .build();

        ModelConfig config = service.updateConfig(1L, ModelConfigUpdateRequest.builder()
                .baseUrl("https://api.openai.com/v1")
                .endpointPath("/v1/embeddings")
                .modelName("text-embedding-3-large")
                .timeoutMs(60000)
                .maxRetries(1)
                .build());

        assertThat(config.getApiKeyCipher()).isEqualTo("cipher-old");
        assertThat(config.getApiKeyMask()).isEqualTo("sk-****1111");
        assertThat(config.getModelName()).isEqualTo("text-embedding-3-large");
        assertThat(config.getEndpointPath()).isEqualTo("/v1/embeddings");
        assertThat(config.getVersion()).isEqualTo(2L);
        assertThat(service.updatedConfig).isSameAs(config);
        assertThat(service.registryBumpCount).isEqualTo(1);
    }

    @Test
    void updateConfigShouldSwitchApiPathByModelType() {
        TestableModelConfigServiceImpl service = new TestableModelConfigServiceImpl();
        service.existingConfig = ModelConfig.builder()
                .configId(1L)
                .configKey("chat.openai")
                .modelType(ModelType.CHAT)
                .provider(ModelProvider.OPENAI)
                .baseUrl("https://api.openai.com/v1")
                .endpointPath(null)
                .modelName("gpt-4o-mini")
                .timeoutMs(30000)
                .maxRetries(0)
                .version(1L)
                .build();

        ModelConfig config = service.updateConfig(1L, ModelConfigUpdateRequest.builder()
                .modelType(ModelType.EMBEDDING)
                .modelName("text-embedding-3-small")
                .build());

        assertThat(config.getModelType()).isEqualTo(ModelType.EMBEDDING);
        assertThat(config.getEndpointPath()).isEqualTo("/embeddings");
    }

    @Test
    void deleteConfigShouldRemoveAndBumpRegistryVersion() {
        TestableModelConfigServiceImpl service = new TestableModelConfigServiceImpl();
        service.existingConfig = ModelConfig.builder()
                .configId(1L)
                .configKey("embedding.openai")
                .modelType(ModelType.EMBEDDING)
                .provider(ModelProvider.OPENAI)
                .baseUrl("https://api.openai.com/v1")
                .modelName("text-embedding-3-small")
                .version(1L)
                .build();

        service.deleteConfig(1L);

        assertThat(service.removedConfigId).isEqualTo(1L);
        assertThat(service.registryBumpCount).isEqualTo(1);
    }

    @Test
    void deleteConfigShouldFailWhenReferencedByRouteConfig() {
        ModelRouteConfigService routeConfigService = mock(ModelRouteConfigService.class);
        when(routeConfigService.existsByConfigId(1L)).thenReturn(true);
        TestableModelConfigServiceImpl service = new TestableModelConfigServiceImpl(routeConfigService);
        service.existingConfig = ModelConfig.builder()
                .configId(1L)
                .configKey("embedding.openai")
                .modelType(ModelType.EMBEDDING)
                .provider(ModelProvider.OPENAI)
                .baseUrl("https://api.openai.com/v1")
                .modelName("text-embedding-3-small")
                .version(1L)
                .build();

        assertThatThrownBy(() -> service.deleteConfig(1L))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("请先从路由中移除该模型配置");
    }

    private static class TestableModelConfigServiceImpl extends ModelConfigServiceImpl {

        private final ModelSecretEncryptor encryptor = new ModelSecretEncryptor("0123456789abcdef0123456789abcdef");
        private ModelConfig savedConfig;
        private ModelConfig updatedConfig;
        private ModelConfig existingConfig;
        private Long removedConfigId;
        private int registryBumpCount;

        private TestableModelConfigServiceImpl() {
            this(new DefaultModelGovernancePolicyFactory(), mock(ModelGovernanceConfigService.class),
                    new ModelGovernanceProperties(), mock(ModelRouteConfigService.class));
        }

        private TestableModelConfigServiceImpl(ModelRouteConfigService routeConfigService) {
            this(new DefaultModelGovernancePolicyFactory(), mock(ModelGovernanceConfigService.class),
                    new ModelGovernanceProperties(), routeConfigService);
        }

        private TestableModelConfigServiceImpl(DefaultModelGovernancePolicyFactory policyFactory,
                                               ModelGovernanceConfigService governanceConfigService,
                                               ModelGovernanceProperties properties) {
            this(policyFactory, governanceConfigService, properties, mock(ModelRouteConfigService.class));
        }

        private TestableModelConfigServiceImpl(DefaultModelGovernancePolicyFactory policyFactory,
                                               ModelGovernanceConfigService governanceConfigService,
                                               ModelGovernanceProperties properties,
                                               ModelRouteConfigService routeConfigService) {
            super(new ModelSecretEncryptor("0123456789abcdef0123456789abcdef"), null, null,
                    policyFactory, governanceConfigService, properties, routeConfigService);
        }

        @Override
        protected boolean existsByConfigKey(String configKey, Long excludedConfigId) {
            return false;
        }

        @Override
        protected boolean saveConfig(ModelConfig config) {
            this.savedConfig = config;
            return true;
        }

        @Override
        protected ModelConfig getRequiredConfig(Long configId) {
            return existingConfig;
        }

        @Override
        protected boolean updateConfigById(ModelConfig config) {
            this.updatedConfig = config;
            return true;
        }

        @Override
        protected boolean removeConfigById(Long configId) {
            this.removedConfigId = configId;
            return true;
        }

        @Override
        protected long bumpRegistryVersionAndPublish() {
            this.registryBumpCount++;
            return registryBumpCount;
        }
    }
}
