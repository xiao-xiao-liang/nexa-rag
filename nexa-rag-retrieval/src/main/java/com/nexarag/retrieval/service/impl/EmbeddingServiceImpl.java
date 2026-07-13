package com.nexarag.retrieval.service.impl;

import com.nexarag.common.exception.ServiceException;
import com.nexarag.model.enums.ModelBizType;
import com.nexarag.model.gateway.ModelGateway;
import com.nexarag.model.gateway.embedding.EmbeddingModelRequest;
import com.nexarag.model.gateway.embedding.EmbeddingModelResponse;
import com.nexarag.retrieval.model.IndexConfigSnapshot;
import com.nexarag.retrieval.config.RetrievalProperties;
import com.nexarag.retrieval.model.ChunkEmbedding;
import com.nexarag.retrieval.model.IndexableChunk;
import com.nexarag.retrieval.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 基于模型网关的片段向量化服务，负责调用 ModelGateway 生成真实 Embedding 向量。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "nexa.retrieval.embedding", name = "type", havingValue = "model")
public class EmbeddingServiceImpl implements EmbeddingService {

    private static final int DEFAULT_MAX_BATCH_SIZE = 10;

    private final ModelGateway modelGateway;
    private final RetrievalProperties retrievalProperties;

    /**
     * 调用模型网关批量生成片段向量。
     *
     * @param chunks 待向量化片段
     * @param config 索引运行配置
     * @return 片段向量列表
     */
    @Override
    public List<ChunkEmbedding> embed(List<IndexableChunk> chunks, IndexConfigSnapshot config) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }

        // 1. 按配置分批调用模型网关，避免超过云端 Embedding 接口批量限制
        Long documentId = chunks.getFirst().documentId();
        int batchSize = resolveMaxBatchSize();
        List<ChunkEmbedding> chunkEmbeddings = new ArrayList<>();
        for (int start = 0; start < chunks.size(); start += batchSize) {
            // 2. 截取当前批次并保留原始顺序
            int end = Math.min(start + batchSize, chunks.size());
            List<IndexableChunk> batchChunks = chunks.subList(start, end);

            // 3. 调用统一模型网关生成当前批次向量
            EmbeddingModelResponse response = modelGateway.embedding(buildRequest(documentId, batchChunks, config));
            if (response == null || response.embeddings() == null || response.embeddings().size() != batchChunks.size()) {
                throw new ServiceException("模型向量化结果数量不匹配，documentId=" + documentId);
            }

            // 4. 按批次原始顺序绑定向量结果
            chunkEmbeddings.addAll(toChunkEmbeddings(batchChunks, response));
        }
        return chunkEmbeddings;
    }

    private EmbeddingModelRequest buildRequest(Long documentId, List<IndexableChunk> batchChunks,
                                               IndexConfigSnapshot config) {
        return EmbeddingModelRequest.builder()
                .traceId("rag-index-" + UUID.randomUUID().toString().replace("-", ""))
                .bizType(ModelBizType.RETRIEVAL)
                .bizId(String.valueOf(documentId))
                .routeKey(resolveRouteKey(config))
                .texts(batchChunks.stream().map(IndexableChunk::text).toList())
                .build();
    }

    private String resolveRouteKey(IndexConfigSnapshot config) {
        if (config != null && StringUtils.hasText(config.embeddingRouteKey())) {
            return config.embeddingRouteKey();
        }
        return retrievalProperties.getEmbedding().getRouteKey();
    }

    private int resolveMaxBatchSize() {
        int configuredBatchSize = retrievalProperties.getEmbedding().getMaxBatchSize();
        return configuredBatchSize > 0 ? configuredBatchSize : DEFAULT_MAX_BATCH_SIZE;
    }

    private List<ChunkEmbedding> toChunkEmbeddings(List<IndexableChunk> chunks, EmbeddingModelResponse response) {
        return java.util.stream.IntStream.range(0, chunks.size())
                .mapToObj(index -> new ChunkEmbedding(chunks.get(index).chunkId(),
                        response.embeddings().get(index), response.modelProfile(), chunks.get(index).tokenCount()))
                .toList();
    }
}
