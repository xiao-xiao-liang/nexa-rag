package com.nexarag.model.entity;

import com.nexarag.model.enums.ModelBizType;
import com.nexarag.model.enums.ModelProvider;
import com.nexarag.model.enums.ModelRequestType;
import com.nexarag.model.enums.ModelRouteRole;
import com.nexarag.model.enums.ModelRouteStrategy;
import com.nexarag.model.enums.ModelType;
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
}
