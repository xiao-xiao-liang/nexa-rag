package com.nexarag.retrieval.config;

import com.nexarag.retrieval.embedding.ModelGatewayEmbeddingModel;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.MetricType;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

/**
 * Spring AI MilvusVectorStore 装配配置。
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "nexa.retrieval.vector", name = "type", havingValue = "milvus")
public class MilvusConfiguration {

    private final RetrievalProperties retrievalProperties;

    /**
     * 创建供 Spring AI 使用的 Milvus 客户端。
     *
     * @return Milvus 服务客户端
     */
    @Bean
    public MilvusServiceClient springAiMilvusServiceClient() {
        return new MilvusServiceClient(connectParam(retrievalProperties));
    }

    /**
     * 创建只通过 ModelGatewayEmbeddingModel 生成向量的 Spring AI VectorStore。
     *
     * @param springAiMilvusServiceClient Milvus 服务客户端
     * @param embeddingModel              模型网关 Embedding 适配器
     * @return Spring AI 向量存储
     */
    @Bean
    public VectorStore vectorStore(MilvusServiceClient springAiMilvusServiceClient, ModelGatewayEmbeddingModel embeddingModel) {
        // 1. 固定使用已治理的模型维度和余弦距离
        RetrievalProperties.Vector vectorProperties = retrievalProperties.getVector();
        MilvusVectorStore.Builder builder = MilvusVectorStore.builder(springAiMilvusServiceClient, embeddingModel)
                .collectionName(vectorProperties.getCollectionName())
                .embeddingDimension(embeddingModel.dimensions())
                .metricType(MetricType.COSINE)
                .initializeSchema(true);
        if (StringUtils.hasText(vectorProperties.getDatabaseName())) {
            builder.databaseName(vectorProperties.getDatabaseName());
        }

        // 2. 由 Spring 容器在 Bean 初始化阶段创建已确认切换的新 schema
        return builder.build();
    }

    /**
     * 将检索模块连接配置转换为 Milvus SDK 连接参数。
     *
     * @param retrievalProperties 检索运行配置
     * @return Milvus 连接参数
     */
    public static ConnectParam connectParam(RetrievalProperties retrievalProperties) {
        RetrievalProperties.Vector vectorProperties = retrievalProperties.getVector();
        ConnectParam.Builder builder = ConnectParam.newBuilder()
                .withHost(vectorProperties.getHost())
                .withPort(vectorProperties.getPort())
                .withRpcDeadline(vectorProperties.getRpcDeadlineMs(), TimeUnit.MILLISECONDS);
        if (StringUtils.hasText(vectorProperties.getDatabaseName())) {
            builder.withDatabaseName(vectorProperties.getDatabaseName());
        }
        if (StringUtils.hasText(vectorProperties.getUsername())) {
            builder.withAuthorization(vectorProperties.getUsername(), vectorProperties.getPassword());
        }
        return builder.build();
    }
}
