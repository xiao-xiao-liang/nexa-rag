package com.nexarag.retrieval.retriever.vector;

import com.nexarag.retrieval.chat.model.ConversationRetrievalRequest;
import com.nexarag.retrieval.chat.model.RetrievalChunk;
import com.nexarag.retrieval.config.RetrievalProperties;
import com.nexarag.retrieval.dto.VectorIndexSearchRequest;
import com.nexarag.retrieval.index.vector.VectorIndexClient;
import com.nexarag.retrieval.model.VectorIndexSearchResult;
import com.nexarag.retrieval.retriever.ConversationRetriever;
import com.nexarag.model.enums.ModelBizType;
import com.nexarag.model.gateway.ModelGateway;
import com.nexarag.model.gateway.embedding.EmbeddingModelRequest;
import com.nexarag.model.gateway.embedding.EmbeddingModelResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * 基于 Milvus 的对话向量检索通道。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "nexa.retrieval.vector", name = "type", havingValue = "milvus")
public class MilvusConversationRetriever implements ConversationRetriever {

    private final ModelGateway modelGateway;
    private final VectorIndexClient vectorIndexClient;
    private final RetrievalProperties retrievalProperties;

    @Override
    public List<RetrievalChunk> retrieve(ConversationRetrievalRequest request) {
        // 1. 调用统一模型网关生成查询向量
        EmbeddingModelResponse response = modelGateway.embedding(EmbeddingModelRequest.builder()
                .traceId("chat-retrieval-" + UUID.randomUUID().toString().replace("-", ""))
                .bizType(ModelBizType.RETRIEVAL)
                .bizId("chat")
                .routeKey(retrievalProperties.getEmbedding().getRouteKey())
                .texts(List.of(request.question()))
                .build());
        if (response == null || response.embeddings() == null || response.embeddings().isEmpty()) {
            return List.of();
        }

        // 2. 查询 Milvus 并标准化通道内排名
        List<VectorIndexSearchResult> results = vectorIndexClient.search(new VectorIndexSearchRequest(
                null, response.embeddings().getFirst(), request.topK()));
        return java.util.stream.IntStream.range(0, results.size())
                .filter(index -> results.get(index).score() >= request.vectorThreshold())
                .mapToObj(index -> toRetrievalChunk(results.get(index), index + 1))
                .toList();
    }

    private RetrievalChunk toRetrievalChunk(VectorIndexSearchResult result, int rank) {
        return new RetrievalChunk(result.chunkId(), result.documentId(), result.chunkOrder(), result.parentChunkId(),
                null, null, result.text(), result.score(), "MILVUS", rank);
    }
}
