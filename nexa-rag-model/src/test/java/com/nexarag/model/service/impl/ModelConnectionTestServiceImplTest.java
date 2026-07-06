package com.nexarag.model.service.impl;

import com.nexarag.model.dto.ModelConnectionTestResponse;
import com.nexarag.model.entity.ModelConfig;
import com.nexarag.model.entity.ModelRoute;
import com.nexarag.model.enums.ModelBizType;
import com.nexarag.model.enums.ModelProvider;
import com.nexarag.model.enums.ModelRouteStrategy;
import com.nexarag.model.enums.ModelType;
import com.nexarag.model.gateway.ModelGateway;
import com.nexarag.model.gateway.chat.ChatModelRequest;
import com.nexarag.model.gateway.chat.ChatModelResponse;
import com.nexarag.model.gateway.embedding.EmbeddingModelRequest;
import com.nexarag.model.gateway.embedding.EmbeddingModelResponse;
import com.nexarag.model.gateway.rerank.RerankModelRequest;
import com.nexarag.model.gateway.rerank.RerankModelResponse;
import com.nexarag.model.route.ModelRouteDecision;
import com.nexarag.model.security.ModelSecretEncryptor;
import com.nexarag.model.service.ModelConfigService;
import com.nexarag.model.service.ModelRouteService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 模型连接测试服务实现类测试。
 */
class ModelConnectionTestServiceImplTest {

    @Test
    void testConfigShouldCallEmbeddingGatewayDirectly() {
        ModelConfigService modelConfigService = mock(ModelConfigService.class);
        ModelRouteService modelRouteService = mock(ModelRouteService.class);
        ModelSecretEncryptor modelSecretEncryptor = mock(ModelSecretEncryptor.class);
        ModelGateway modelGateway = mock(ModelGateway.class);
        ModelConnectionTestServiceImpl service = new ModelConnectionTestServiceImpl(
                modelConfigService, modelRouteService, modelSecretEncryptor, modelGateway
        );
        ModelConfig config = modelConfig(ModelType.EMBEDDING);

        when(modelConfigService.getById(1L)).thenReturn(config);
        when(modelGateway.embedding(any(ModelRouteDecision.class), any(EmbeddingModelRequest.class)))
                .thenReturn(new EmbeddingModelResponse(List.of(new float[]{0.1f, 0.2f, 0.3f}),
                        "embedding-primary", 10));

        ModelConnectionTestResponse response = service.testConfig(1L, null);

        assertThat(response.success()).isTrue();
        assertThat(response.provider()).isEqualTo(ModelProvider.OPENAI);
        assertThat(response.modelType()).isEqualTo(ModelType.EMBEDDING);
        assertThat(response.vectorDimension()).isEqualTo(3);

        ArgumentCaptor<EmbeddingModelRequest> requestCaptor = ArgumentCaptor.forClass(EmbeddingModelRequest.class);
        verify(modelGateway).embedding(any(ModelRouteDecision.class), requestCaptor.capture());
        assertThat(requestCaptor.getValue().bizType()).isEqualTo(ModelBizType.MODEL_TEST);
        assertThat(requestCaptor.getValue().texts()).containsExactly("你好，NexaRAG");
    }

    @Test
    void testRouteShouldCallRerankGateway() {
        ModelConfigService modelConfigService = mock(ModelConfigService.class);
        ModelRouteService modelRouteService = mock(ModelRouteService.class);
        ModelSecretEncryptor modelSecretEncryptor = mock(ModelSecretEncryptor.class);
        ModelGateway modelGateway = mock(ModelGateway.class);
        ModelConnectionTestServiceImpl service = new ModelConnectionTestServiceImpl(
                modelConfigService, modelRouteService, modelSecretEncryptor, modelGateway
        );
        ModelRoute route = modelRoute(ModelType.RERANK);

        when(modelRouteService.getById(2L)).thenReturn(route);
        when(modelGateway.rerank(any(RerankModelRequest.class))).thenReturn(new RerankModelResponse(
                List.of(new RerankModelResponse.RerankScore("doc-1", 0.9)), "rerank-primary", 20
        ));

        ModelConnectionTestResponse response = service.testRoute(2L, null);

        assertThat(response.success()).isTrue();
        assertThat(response.modelType()).isEqualTo(ModelType.RERANK);
        assertThat(response.rerankCount()).isEqualTo(1);

        ArgumentCaptor<RerankModelRequest> requestCaptor = ArgumentCaptor.forClass(RerankModelRequest.class);
        verify(modelGateway).rerank(requestCaptor.capture());
        assertThat(requestCaptor.getValue().bizType()).isEqualTo(ModelBizType.MODEL_TEST);
        assertThat(requestCaptor.getValue().routeKey()).isEqualTo("rerank");
        assertThat(requestCaptor.getValue().query()).isEqualTo("什么是 RAG？");
        assertThat(requestCaptor.getValue().candidates()).hasSize(2);
    }

    @Test
    void testConfigChatShouldCallChatGatewayDirectly() {
        ModelConfigService modelConfigService = mock(ModelConfigService.class);
        ModelRouteService modelRouteService = mock(ModelRouteService.class);
        ModelSecretEncryptor modelSecretEncryptor = mock(ModelSecretEncryptor.class);
        ModelGateway modelGateway = mock(ModelGateway.class);
        ModelConnectionTestServiceImpl service = new ModelConnectionTestServiceImpl(
                modelConfigService, modelRouteService, modelSecretEncryptor, modelGateway
        );

        when(modelConfigService.getById(3L)).thenReturn(modelConfig(ModelType.CHAT));
        when(modelGateway.chat(any(ModelRouteDecision.class), any(ChatModelRequest.class)))
                .thenReturn(ChatModelResponse.builder()
                        .content("连接正常")
                        .modelProfile("chat-primary")
                        .promptTokens(1)
                        .completionTokens(2)
                        .totalTokens(3)
                        .build());

        ModelConnectionTestResponse response = service.testConfig(3L, null);

        assertThat(response.success()).isTrue();
        assertThat(response.modelType()).isEqualTo(ModelType.CHAT);

        ArgumentCaptor<ChatModelRequest> requestCaptor = ArgumentCaptor.forClass(ChatModelRequest.class);
        verify(modelGateway).chat(any(ModelRouteDecision.class), requestCaptor.capture());
        assertThat(requestCaptor.getValue().bizType()).isEqualTo(ModelBizType.MODEL_TEST);
        assertThat(requestCaptor.getValue().messages()).hasSize(1);
        assertThat(requestCaptor.getValue().messages().getFirst().content()).isEqualTo("你好");
    }

    @Test
    void testConfigChatShouldFailWhenGatewayReturnsBlankContent() {
        ModelConfigService modelConfigService = mock(ModelConfigService.class);
        ModelRouteService modelRouteService = mock(ModelRouteService.class);
        ModelSecretEncryptor modelSecretEncryptor = mock(ModelSecretEncryptor.class);
        ModelGateway modelGateway = mock(ModelGateway.class);
        ModelConnectionTestServiceImpl service = new ModelConnectionTestServiceImpl(
                modelConfigService, modelRouteService, modelSecretEncryptor, modelGateway
        );

        when(modelConfigService.getById(3L)).thenReturn(modelConfig(ModelType.CHAT));
        when(modelGateway.chat(any(ModelRouteDecision.class), any(ChatModelRequest.class)))
                .thenReturn(ChatModelResponse.builder()
                        .content("")
                        .modelProfile("chat-primary")
                        .promptTokens(1)
                        .completionTokens(0)
                        .totalTokens(1)
                        .build());

        ModelConnectionTestResponse response = service.testConfig(3L, null);

        assertThat(response.success()).isFalse();
        assertThat(response.modelType()).isEqualTo(ModelType.CHAT);
        assertThat(response.errorMessage()).contains("Chat 模型连接测试未返回有效内容");
    }

    @Test
    void testConfigEmbeddingShouldFailWhenGatewayReturnsEmptyVector() {
        ModelConfigService modelConfigService = mock(ModelConfigService.class);
        ModelRouteService modelRouteService = mock(ModelRouteService.class);
        ModelSecretEncryptor modelSecretEncryptor = mock(ModelSecretEncryptor.class);
        ModelGateway modelGateway = mock(ModelGateway.class);
        ModelConnectionTestServiceImpl service = new ModelConnectionTestServiceImpl(
                modelConfigService, modelRouteService, modelSecretEncryptor, modelGateway
        );

        when(modelConfigService.getById(1L)).thenReturn(modelConfig(ModelType.EMBEDDING));
        when(modelGateway.embedding(any(ModelRouteDecision.class), any(EmbeddingModelRequest.class)))
                .thenReturn(new EmbeddingModelResponse(List.of(), "embedding-primary", 0));

        ModelConnectionTestResponse response = service.testConfig(1L, null);

        assertThat(response.success()).isFalse();
        assertThat(response.modelType()).isEqualTo(ModelType.EMBEDDING);
        assertThat(response.errorMessage()).contains("Embedding 模型连接测试未返回有效向量");
    }

    @Test
    void testRouteRerankShouldFailWhenGatewayReturnsEmptyScores() {
        ModelConfigService modelConfigService = mock(ModelConfigService.class);
        ModelRouteService modelRouteService = mock(ModelRouteService.class);
        ModelSecretEncryptor modelSecretEncryptor = mock(ModelSecretEncryptor.class);
        ModelGateway modelGateway = mock(ModelGateway.class);
        ModelConnectionTestServiceImpl service = new ModelConnectionTestServiceImpl(
                modelConfigService, modelRouteService, modelSecretEncryptor, modelGateway
        );
        ModelRoute route = modelRoute(ModelType.RERANK);

        when(modelRouteService.getById(2L)).thenReturn(route);
        when(modelGateway.rerank(any(RerankModelRequest.class))).thenReturn(new RerankModelResponse(
                List.of(), "rerank-primary", 0
        ));

        ModelConnectionTestResponse response = service.testRoute(2L, null);

        assertThat(response.success()).isFalse();
        assertThat(response.modelType()).isEqualTo(ModelType.RERANK);
        assertThat(response.errorMessage()).contains("Rerank 模型连接测试未返回有效分数");
    }

    private ModelConfig modelConfig(ModelType modelType) {
        return ModelConfig.builder()
                .configId(1L)
                .configKey("test-config")
                .modelType(modelType)
                .provider(ModelProvider.OPENAI)
                .baseUrl("https://api.openai.com/v1")
                .modelName("text-embedding-3-small")
                .enabled(true)
                .timeoutMs(30000)
                .version(1L)
                .build();
    }

    private ModelRoute modelRoute(ModelType modelType) {
        return ModelRoute.builder()
                .routeId(2L)
                .routeKey("rerank")
                .modelType(modelType)
                .strategy(ModelRouteStrategy.PRIMARY_BACKUP)
                .enabled(true)
                .version(1)
                .build();
    }
}
