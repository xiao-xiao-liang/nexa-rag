package com.nexarag.retrieval.embedding;

import com.nexarag.model.enums.ModelBizType;
import com.nexarag.model.gateway.ModelGateway;
import com.nexarag.model.gateway.embedding.EmbeddingModelRequest;
import com.nexarag.model.gateway.embedding.EmbeddingModelResponse;
import com.nexarag.retrieval.config.RetrievalProperties;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * Spring AI EmbeddingModel 与统一模型网关之间的适配器。
 *
 * <p>仅供 retrieval 模块的 VectorStore 使用，所有模型调用均委托给 ModelGateway，
 * 不在此处处理厂商适配、路由绕过或向量缓存。</p>
 */
@Component
@RequiredArgsConstructor
public class ModelGatewayEmbeddingModel implements EmbeddingModel {

    private static final String VECTOR_STORE_BIZ_ID = "spring-vector-store";

    private final ModelGateway modelGateway;
    private final RetrievalProperties retrievalProperties;

    /**
     * 调用模型网关生成向量。
     *
     * @param request Spring AI 向量化请求
     * @return 与输入顺序一致的 Spring AI 向量化响应
     */
    @NotNull
    @Override
    public EmbeddingResponse call(@NotNull EmbeddingRequest request) {
        // 1. 校验框架传入的文本，避免空请求进入模型治理链路
        List<String> texts = request.getInstructions();
        if (texts.isEmpty()) {
            throw new IllegalArgumentException("Spring AI向量化请求不能为空");
        }

        // 2. 委托统一模型网关执行路由、治理与厂商适配
        EmbeddingModelResponse response = modelGateway.embedding(EmbeddingModelRequest.builder()
                .traceId("spring-vector-store-" + UUID.randomUUID().toString().replace("-", ""))
                .bizType(ModelBizType.RETRIEVAL)
                .bizId(VECTOR_STORE_BIZ_ID)
                .routeKey(retrievalProperties.getEmbedding().getRouteKey())
                .texts(List.copyOf(texts))
                .build());

        // 3. 校验网关响应并转换为 Spring AI 响应
        return toEmbeddingResponse(texts.size(), response);
    }

    /**
     * 对单个 Spring AI 文档生成向量。
     *
     * @param document Spring AI 文档
     * @return 文档文本对应的向量
     */
    @NotNull
    @Override
    public float[] embed(@NotNull Document document) {
        assert document.getText() != null;
        return embed(document.getText());
    }

    /**
     * 返回已治理的向量维度，禁止 Spring AI 通过探测文本推断维度。
     *
     * @return 向量维度
     */
    @Override
    public int dimensions() {
        int dimension = retrievalProperties.getVector().getDimension();
        if (dimension <= 0) {
            throw new IllegalStateException("使用ModelGatewayEmbeddingModel前必须配置nexa.retrieval.vector.dimension");
        }
        return dimension;
    }

    private EmbeddingResponse toEmbeddingResponse(int expectedCount, EmbeddingModelResponse response) {
        if (response == null || response.embeddings() == null) {
            throw new IllegalStateException("模型网关未返回向量结果");
        }
        if (response.embeddings().size() != expectedCount) {
            throw new IllegalStateException("模型网关返回向量数量不匹配，expected=" + expectedCount
                    + "，actual=" + response.embeddings().size());
        }

        int dimension = dimensions();
        List<Embedding> embeddings = IntStream.range(0, response.embeddings().size())
                .mapToObj(index -> new Embedding(validateVector(response.embeddings().get(index), dimension), index))
                .toList();
        EmbeddingResponseMetadata metadata = new EmbeddingResponseMetadata(response.modelProfile(),
                new DefaultUsage(null, null, response.totalTokens()));
        return new EmbeddingResponse(embeddings, metadata);
    }

    private float[] validateVector(float[] vector, int expectedDimension) {
        if (vector == null || vector.length == 0) {
            throw new IllegalStateException("模型网关返回空向量");
        }
        if (vector.length != expectedDimension) {
            throw new IllegalStateException("模型网关返回向量维度不匹配，expected=" + expectedDimension
                    + "，actual=" + vector.length);
        }
        return vector;
    }
}
