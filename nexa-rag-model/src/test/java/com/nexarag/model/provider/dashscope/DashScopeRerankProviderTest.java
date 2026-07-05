package com.nexarag.model.provider.dashscope;

import com.alibaba.cloud.ai.dashscope.rerank.DashScopeRerankModel;
import com.alibaba.cloud.ai.document.DocumentWithScore;
import com.alibaba.cloud.ai.model.RerankRequest;
import com.alibaba.cloud.ai.model.RerankResponse;
import com.nexarag.model.client.ModelClientFactory;
import com.nexarag.model.config.ModelProfileProperties;
import com.nexarag.model.enums.ModelBizType;
import com.nexarag.model.enums.ModelProvider;
import com.nexarag.model.enums.ModelType;
import com.nexarag.model.gateway.rerank.RerankCandidate;
import com.nexarag.model.gateway.rerank.RerankModelRequest;
import com.nexarag.model.gateway.rerank.RerankModelResponse;
import com.nexarag.model.route.ModelRouteDecision;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * DashScope Rerank Provider 测试。
 */
class DashScopeRerankProviderTest {

    @Test
    void rerankShouldMapDashScopeResponse() {
        ModelClientFactory modelClientFactory = mock(ModelClientFactory.class);
        DashScopeRerankModel rerankModel = mock(DashScopeRerankModel.class);
        DashScopeRerankProvider provider = new DashScopeRerankProvider(modelClientFactory);
        ModelRouteDecision decision = routeDecision();
        RerankCandidate candidate = new RerankCandidate("chunk-1", "片段内容", Map.of("documentId", 1L));
        RerankModelRequest request = RerankModelRequest.builder()
                .traceId("trace-1")
                .bizType(ModelBizType.RERANK)
                .bizId("conversation-1")
                .routeKey("rerank")
                .query("问题")
                .candidates(List.of(candidate))
                .build();

        when(modelClientFactory.getDashScopeRerankClient(decision)).thenReturn(rerankModel);
        when(rerankModel.call(any(RerankRequest.class))).thenReturn(new RerankResponse(List.of(
                DocumentWithScore.builder()
                        .withDocument(new Document("片段内容", Map.of("nexa_candidate_id", "chunk-1")))
                        .withScore(0.91)
                        .build()
        )));

        RerankModelResponse response = provider.rerank(decision, request);

        assertThat(response.modelProfile()).isEqualTo("rerank-primary");
        assertThat(response.totalTokens()).isZero();
        assertThat(response.scores()).hasSize(1);
        assertThat(response.scores().getFirst().id()).isEqualTo("chunk-1");
        assertThat(response.scores().getFirst().score()).isEqualTo(0.91);
    }

    @Test
    void shouldOnlySupportDashScopeRerank() {
        DashScopeRerankProvider provider = new DashScopeRerankProvider(mock(ModelClientFactory.class));

        assertThat(provider.supports(ModelProvider.DASHSCOPE, ModelType.RERANK)).isTrue();
        assertThat(provider.supports(ModelProvider.DASHSCOPE, ModelType.EMBEDDING)).isFalse();
        assertThat(provider.supports(ModelProvider.OPENAI, ModelType.RERANK)).isFalse();
    }

    private ModelRouteDecision routeDecision() {
        ModelProfileProperties profile = new ModelProfileProperties();
        profile.setProvider("DASHSCOPE");
        profile.setBaseUrl("https://dashscope.aliyuncs.com");
        profile.setModelName("qwen3-rerank");
        return new ModelRouteDecision("rerank-primary", profile, false);
    }
}
