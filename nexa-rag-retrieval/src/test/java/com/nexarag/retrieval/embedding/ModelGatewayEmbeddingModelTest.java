package com.nexarag.retrieval.embedding;

import com.nexarag.model.enums.ModelBizType;
import com.nexarag.model.gateway.ModelGateway;
import com.nexarag.model.gateway.embedding.EmbeddingModelRequest;
import com.nexarag.model.gateway.embedding.EmbeddingModelResponse;
import com.nexarag.retrieval.config.RetrievalProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * ModelGatewayEmbeddingModel 单元测试。
 */
class ModelGatewayEmbeddingModelTest {

    @Test
    void callShouldDelegateToGatewayAndPreserveInputOrder() {
        ModelGateway modelGateway = mock(ModelGateway.class);
        when(modelGateway.embedding(any(EmbeddingModelRequest.class))).thenReturn(new EmbeddingModelResponse(
                List.of(vector(1.0F, 2.0F), vector(3.0F, 4.0F)), "embedding-primary", 12));
        ModelGatewayEmbeddingModel embeddingModel = new ModelGatewayEmbeddingModel(modelGateway, properties());

        EmbeddingResponse response = embeddingModel.call(new EmbeddingRequest(List.of("first", "second"),
                EmbeddingOptions.builder().model("should-not-bypass-gateway").build()));

        ArgumentCaptor<EmbeddingModelRequest> requestCaptor = ArgumentCaptor.forClass(EmbeddingModelRequest.class);
        verify(modelGateway).embedding(requestCaptor.capture());
        EmbeddingModelRequest request = requestCaptor.getValue();
        assertThat(request.bizType()).isEqualTo(ModelBizType.RETRIEVAL);
        assertThat(request.routeKey()).isEqualTo("embedding");
        assertThat(request.texts()).containsExactly("first", "second");
        assertThat(response.getResults()).extracting(Embedding::getIndex).containsExactly(0, 1);
        assertThat(response.getResults()).extracting(result -> result.getOutput()[0]).containsExactly(1.0F, 3.0F);
        assertThat(response.getResults()).allSatisfy(result -> assertThat(result.getOutput()).hasSize(1024));
        assertThat(response.getMetadata().getModel()).isEqualTo("embedding-primary");
        assertThat(response.getMetadata().getUsage().getTotalTokens()).isEqualTo(12);
    }

    @Test
    void callShouldRejectGatewayResponseWithMismatchedVectorCount() {
        ModelGateway modelGateway = mock(ModelGateway.class);
        when(modelGateway.embedding(any(EmbeddingModelRequest.class))).thenReturn(new EmbeddingModelResponse(
                List.of(vector(1.0F, 2.0F)), "embedding-primary", 2));
        ModelGatewayEmbeddingModel embeddingModel = new ModelGatewayEmbeddingModel(modelGateway, properties());

        assertThatThrownBy(() -> embeddingModel.call(new EmbeddingRequest(List.of("first", "second"), null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("数量不匹配");
    }

    @Test
    void callShouldRejectVectorWithUnexpectedDimension() {
        ModelGateway modelGateway = mock(ModelGateway.class);
        when(modelGateway.embedding(any(EmbeddingModelRequest.class))).thenReturn(new EmbeddingModelResponse(
                List.of(new float[1023]), "embedding-primary", 1));
        ModelGatewayEmbeddingModel embeddingModel = new ModelGatewayEmbeddingModel(modelGateway, properties());

        assertThatThrownBy(() -> embeddingModel.call(new EmbeddingRequest(List.of("first"), null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("维度不匹配");
    }

    @Test
    void dimensionsShouldRequireExplicitConfigurationWithoutCallingGateway() {
        ModelGateway modelGateway = mock(ModelGateway.class);
        RetrievalProperties properties = properties();
        properties.getVector().setDimension(0);
        ModelGatewayEmbeddingModel embeddingModel = new ModelGatewayEmbeddingModel(modelGateway, properties);

        assertThatThrownBy(embeddingModel::dimensions)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nexa.retrieval.vector.dimension");
        verifyNoInteractions(modelGateway);
    }

    private RetrievalProperties properties() {
        RetrievalProperties properties = new RetrievalProperties();
        properties.getEmbedding().setRouteKey("embedding");
        properties.getVector().setDimension(1024);
        return properties;
    }

    private float[] vector(float first, float second) {
        float[] vector = new float[1024];
        vector[0] = first;
        vector[1] = second;
        return vector;
    }
}
