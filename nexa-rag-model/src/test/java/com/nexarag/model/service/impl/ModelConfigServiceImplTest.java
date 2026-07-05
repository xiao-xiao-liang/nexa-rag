package com.nexarag.model.service.impl;

import com.nexarag.model.dto.ModelConfigCreateRequest;
import com.nexarag.model.dto.ModelConfigUpdateRequest;
import com.nexarag.model.entity.ModelConfig;
import com.nexarag.model.enums.ModelProvider;
import com.nexarag.model.enums.ModelType;
import com.nexarag.model.security.ModelSecretEncryptor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
                .apiKey("sk-test-abcdef")
                .modelName("text-embedding-3-small")
                .timeoutMs(30000)
                .maxRetries(0)
                .build());

        assertThat(config.getConfigId()).isNotNull();
        assertThat(config.getApiKeyCipher()).isNotBlank();
        assertThat(service.encryptor.decrypt(config.getApiKeyCipher())).isEqualTo("sk-test-abcdef");
        assertThat(config.getApiKeyMask()).isEqualTo("sk-****cdef");
        assertThat(service.savedConfig).isSameAs(config);
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
                .apiKeyCipher("cipher-old")
                .apiKeyMask("sk-****1111")
                .modelName("text-embedding-3-small")
                .timeoutMs(30000)
                .maxRetries(0)
                .version(1L)
                .build();

        ModelConfig config = service.updateConfig(1L, ModelConfigUpdateRequest.builder()
                .baseUrl("https://api.openai.com/v1")
                .modelName("text-embedding-3-large")
                .timeoutMs(60000)
                .maxRetries(1)
                .build());

        assertThat(config.getApiKeyCipher()).isEqualTo("cipher-old");
        assertThat(config.getApiKeyMask()).isEqualTo("sk-****1111");
        assertThat(config.getModelName()).isEqualTo("text-embedding-3-large");
        assertThat(config.getVersion()).isEqualTo(2L);
        assertThat(service.updatedConfig).isSameAs(config);
    }

    private static class TestableModelConfigServiceImpl extends ModelConfigServiceImpl {

        private final ModelSecretEncryptor encryptor = new ModelSecretEncryptor("0123456789abcdef0123456789abcdef");
        private ModelConfig savedConfig;
        private ModelConfig updatedConfig;
        private ModelConfig existingConfig;

        private TestableModelConfigServiceImpl() {
            super(new ModelSecretEncryptor("0123456789abcdef0123456789abcdef"));
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
    }
}
