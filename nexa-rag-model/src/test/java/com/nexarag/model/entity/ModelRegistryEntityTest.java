package com.nexarag.model.entity;

import com.nexarag.model.enums.ModelBizType;
import com.nexarag.model.enums.ModelCallStatus;
import com.nexarag.model.enums.ModelGovernanceBindingMode;
import com.nexarag.model.enums.ModelProvider;
import com.nexarag.model.enums.ModelRequestType;
import com.nexarag.model.enums.ModelRouteRole;
import com.nexarag.model.enums.ModelRouteStrategy;
import com.nexarag.model.enums.ModelType;
import com.nexarag.model.enums.TokenUsageSource;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模型注册表实体与枚举测试。
 */
class ModelRegistryEntityTest {

    @Test
    void enumsShouldContainInitialModelRegistryValues() {
        assertThat(ModelType.values()).extracting(Enum::name)
                .contains("CHAT", "EMBEDDING", "RERANK");
        assertThat(ModelProvider.values()).extracting(Enum::name)
                .contains("OPENAI", "OLLAMA", "DASHSCOPE", "DEEPSEEK",
                        "SILICONFLOW", "ZHIPU", "MOONSHOT", "CUSTOM_OPENAI");
        assertThat(ModelRouteStrategy.values()).extracting(Enum::name)
                .contains("PRIMARY_BACKUP");
        assertThat(ModelRouteRole.values()).extracting(Enum::name)
                .contains("PRIMARY", "BACKUP", "CANDIDATE");
        assertThat(ModelBizType.values()).extracting(Enum::name)
                .contains("MODEL_TEST");
        assertThat(ModelRequestType.values()).extracting(Enum::name)
                .contains("EMBEDDING_TEST", "RERANK_TEST", "CHAT_TEST");
    }

    @Test
    void modelConfigShouldRepresentCallableModel() {
        ModelConfig config = ModelConfig.builder()
                .configId(1L)
                .configKey("embedding.ollama")
                .modelType(ModelType.EMBEDDING)
                .provider(ModelProvider.OLLAMA)
                .baseUrl("http://localhost:11434/v1")
                .modelName("nomic-embed-text")
                .enabled(Boolean.TRUE)
                .version(1L)
                .createTime(LocalDateTime.now())
                .build();

        assertThat(config.getConfigId()).isEqualTo(1L);
        assertThat(config.getProvider()).isEqualTo(ModelProvider.OLLAMA);
        assertThat(config.getModelType()).isEqualTo(ModelType.EMBEDDING);
    }

    @Test
    void governanceConfigShouldSupportConfigAndRouteBinding() {
        ModelGovernanceConfig configBinding = ModelGovernanceConfig.builder()
                .bindingMode(ModelGovernanceBindingMode.CONFIG)
                .configId(1001L)
                .enabled(Boolean.TRUE)
                .build();

        ModelGovernanceConfig routeBinding = ModelGovernanceConfig.builder()
                .bindingMode(ModelGovernanceBindingMode.ROUTE)
                .routeKey("chat")
                .enabled(Boolean.TRUE)
                .build();

        assertThat(configBinding.getBindingMode()).isEqualTo(ModelGovernanceBindingMode.CONFIG);
        assertThat(configBinding.getConfigId()).isEqualTo(1001L);
        assertThat(routeBinding.getBindingMode()).isEqualTo(ModelGovernanceBindingMode.ROUTE);
        assertThat(routeBinding.getRouteKey()).isEqualTo("chat");
    }

    @Test
    void modelCallLogShouldSupportTimeoutCancelAndTokenUsageSource() {
        ModelCallLog timeoutLog = ModelCallLog.builder()
                .status(ModelCallStatus.TIMEOUT)
                .tokenUsageSource(TokenUsageSource.PROVIDER_USAGE)
                .firstTokenLatencyMs(1200L)
                .chunkCount(3)
                .outputCharCount(128)
                .estimatedOutputTokens(32)
                .build();

        assertThat(timeoutLog.getStatus()).isEqualTo(ModelCallStatus.TIMEOUT);
        assertThat(ModelCallStatus.valueOf("CANCELED")).isEqualTo(ModelCallStatus.CANCELED);
        assertThat(timeoutLog.getTokenUsageSource()).isEqualTo(TokenUsageSource.PROVIDER_USAGE);
        assertThat(timeoutLog.getFirstTokenLatencyMs()).isEqualTo(1200L);
        assertThat(timeoutLog.getChunkCount()).isEqualTo(3);
    }
}
