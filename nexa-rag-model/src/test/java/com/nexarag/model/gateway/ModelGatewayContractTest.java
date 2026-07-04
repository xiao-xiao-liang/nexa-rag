package com.nexarag.model.gateway;

import com.nexarag.model.enums.ModelBizType;
import com.nexarag.model.gateway.chat.ChatModelRequest;
import com.nexarag.model.gateway.chat.ChatModelResponse;
import com.nexarag.model.gateway.embedding.EmbeddingModelRequest;
import com.nexarag.model.gateway.embedding.EmbeddingModelResponse;
import com.nexarag.model.gateway.rerank.RerankCandidate;
import com.nexarag.model.gateway.rerank.RerankModelRequest;
import com.nexarag.model.gateway.rerank.RerankModelResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模型网关契约测试。
 */
class ModelGatewayContractTest {

    @Test
    void chatContractShouldCarryMessagesAndRouteKey() {
        ChatModelRequest request = new ChatModelRequest(
                "trace-1",
                ModelBizType.CHAT,
                "conversation-1",
                "chat",
                List.of(new ChatModelRequest.ChatMessage("USER", "你好")),
                Map.of("temperature", 0.7)
        );
        ChatModelResponse response = new ChatModelResponse("你好", "chat-primary", 1, 2, 3);

        assertThat(request.routeKey()).isEqualTo("chat");
        assertThat(request.messages()).hasSize(1);
        assertThat(response.modelProfile()).isEqualTo("chat-primary");
    }

    @Test
    void embeddingContractShouldCarryTextsAndEmbeddings() {
        EmbeddingModelRequest request = new EmbeddingModelRequest(
                "trace-1", ModelBizType.RETRIEVAL, "document-1", "embedding", List.of("片段")
        );
        EmbeddingModelResponse response = new EmbeddingModelResponse(List.of(new float[]{0.1f, 0.2f}),
                "embedding-primary", 10);

        assertThat(request.texts()).containsExactly("片段");
        assertThat(response.embeddings()).hasSize(1);
    }

    @Test
    void rerankContractShouldCarryQueryAndCandidates() {
        RerankCandidate candidate = new RerankCandidate("chunk-1", "片段内容", Map.of("documentId", 1L));
        RerankModelRequest request = new RerankModelRequest(
                "trace-1", ModelBizType.RERANK, "conversation-1", "rerank", "问题", List.of(candidate)
        );
        RerankModelResponse response = new RerankModelResponse(
                List.of(new RerankModelResponse.RerankScore("chunk-1", 0.9)), "rerank-primary", 20
        );

        assertThat(request.candidates()).containsExactly(candidate);
        assertThat(response.scores().getFirst().score()).isEqualTo(0.9);
    }
}
