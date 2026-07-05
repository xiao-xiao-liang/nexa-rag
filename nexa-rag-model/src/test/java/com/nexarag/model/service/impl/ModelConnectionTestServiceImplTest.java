package com.nexarag.model.service.impl;

import com.nexarag.model.dto.ModelConnectionTestRequest;
import com.nexarag.model.dto.ModelConnectionTestResponse;
import com.nexarag.model.entity.ModelConfig;
import com.nexarag.model.entity.ModelRoute;
import com.nexarag.model.enums.ModelBizType;
import com.nexarag.model.enums.ModelProvider;
import com.nexarag.model.enums.ModelRouteStrategy;
import com.nexarag.model.enums.ModelType;
import com.nexarag.model.gateway.ModelGateway;
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

        ModelConnectionTestResponse response = service.testConfig(1L, new ModelConnectionTestRequest("测试文本",
                null, null));

        assertThat(response.success()).isTrue();
        assertThat(response.provider()).isEqualTo(ModelProvider.OPENAI);
        assertThat(response.modelType()).isEqualTo(ModelType.EMBEDDING);
        assertThat(response.vectorDimension()).isEqualTo(3);

        ArgumentCaptor<EmbeddingModelRequest> requestCaptor = ArgumentCaptor.forClass(EmbeddingModelRequest.class);
        verify(modelGateway).embedding(any(ModelRouteDecision.class), requestCaptor.capture());
        assertThat(requestCaptor.getValue().bizType()).isEqualTo(ModelBizType.MODEL_TEST);
        assertThat(requestCaptor.getValue().texts()).containsExactly("测试文本");
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

        ModelConnectionTestResponse response = service.testRoute(2L, new ModelConnectionTestRequest(null,
                "什么是 RAG？", List.of("RAG 是检索增强生成。")));

        assertThat(response.success()).isTrue();
        assertThat(response.modelType()).isEqualTo(ModelType.RERANK);
        assertThat(response.rerankCount()).isEqualTo(1);

        ArgumentCaptor<RerankModelRequest> requestCaptor = ArgumentCaptor.forClass(RerankModelRequest.class);
        verify(modelGateway).rerank(requestCaptor.capture());
        assertThat(requestCaptor.getValue().bizType()).isEqualTo(ModelBizType.MODEL_TEST);
        assertThat(requestCaptor.getValue().routeKey()).isEqualTo("rerank");
        assertThat(requestCaptor.getValue().candidates()).hasSize(1);
    }

    @Test
    void testConfigChatShouldReturnUnsupportedResult() {
        ModelConfigService modelConfigService = mock(ModelConfigService.class);
        ModelRouteService modelRouteService = mock(ModelRouteService.class);
        ModelSecretEncryptor modelSecretEncryptor = mock(ModelSecretEncryptor.class);
        ModelGateway modelGateway = mock(ModelGateway.class);
        ModelConnectionTestServiceImpl service = new ModelConnectionTestServiceImpl(
                modelConfigService, modelRouteService, modelSecretEncryptor, modelGateway
        );

        when(modelConfigService.getById(3L)).thenReturn(modelConfig(ModelType.CHAT));

        ModelConnectionTestResponse response = service.testConfig(3L, null);

        assertThat(response.success()).isFalse();
        assertThat(response.modelType()).isEqualTo(ModelType.CHAT);
        assertThat(response.errorMessage()).contains("暂未支持");
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
