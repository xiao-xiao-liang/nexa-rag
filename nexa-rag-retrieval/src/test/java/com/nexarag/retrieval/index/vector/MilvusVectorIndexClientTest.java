package com.nexarag.retrieval.index.vector;

import com.nexarag.retrieval.config.RetrievalProperties;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.database.request.CreateDatabaseReq;
import io.milvus.v2.service.database.response.ListDatabasesResp;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Milvus 向量索引客户端测试，验证数据库初始化和连接配置行为。
 */
class MilvusVectorIndexClientTest {

    @Test
    void constructorShouldCreateConfiguredDatabaseBeforeConnectingTargetDatabase() {
        RetrievalProperties retrievalProperties = retrievalProperties("nexa_rag");
        List<ConnectConfig> connectConfigs = new ArrayList<>();

        try (MockedConstruction<MilvusClientV2> construction = mockConstruction(MilvusClientV2.class,
                (mock, context) -> {
                    connectConfigs.add(connectConfig(context));
                    when(mock.listDatabases()).thenReturn(ListDatabasesResp.builder()
                            .databaseNames(List.of("default"))
                            .build());
                })) {
            MilvusVectorIndexClient client = new MilvusVectorIndexClient(retrievalProperties);

            // 1. 校验先创建默认库客户端用于检查数据库，再创建目标库正式客户端
            assertThat(construction.constructed()).hasSize(2);
            assertThat(connectConfigs.getFirst().getDbName()).isNull();
            assertThat(connectConfigs.get(1).getDbName()).isEqualTo("nexa_rag");

            // 2. 校验目标数据库不存在时会自动创建，并关闭临时客户端
            MilvusClientV2 bootstrapClient = construction.constructed().getFirst();
            verify(bootstrapClient).createDatabase(argThat(request -> "nexa_rag".equals(request.getDatabaseName())));
            verify(bootstrapClient).close();
            client.destroy();
        }
    }

    @Test
    void constructorShouldSkipCreateDatabaseWhenConfiguredDatabaseExists() {
        RetrievalProperties retrievalProperties = retrievalProperties("nexa_rag");

        try (MockedConstruction<MilvusClientV2> construction = mockConstruction(MilvusClientV2.class,
                (mock, context) -> when(mock.listDatabases()).thenReturn(ListDatabasesResp.builder()
                        .databaseNames(List.of("default", "nexa_rag"))
                        .build()))) {
            MilvusVectorIndexClient client = new MilvusVectorIndexClient(retrievalProperties);

            // 1. 数据库已存在时仅完成存在性检查，不重复创建
            assertThat(construction.constructed()).hasSize(2);
            MilvusClientV2 bootstrapClient = construction.constructed().getFirst();
            verify(bootstrapClient, never()).createDatabase(argThat(this::anyCreateDatabaseReq));
            verify(bootstrapClient).close();
            client.destroy();
        }
    }

    @Test
    void constructorShouldRejectInvalidDatabaseNameBeforeConnectingMilvus() {
        RetrievalProperties retrievalProperties = retrievalProperties("nexa-rag");

        try (MockedConstruction<MilvusClientV2> construction = mockConstruction(MilvusClientV2.class)) {
            assertThatThrownBy(() -> new MilvusVectorIndexClient(retrievalProperties))
                    .hasMessageContaining("Milvus 数据库名称不合法");

            // 1. 配置不合法时直接失败，避免继续连接 Milvus 后暴露深层 SDK 异常
            assertThat(construction.constructed()).isEmpty();
        }
    }

    private RetrievalProperties retrievalProperties(String databaseName) {
        RetrievalProperties retrievalProperties = new RetrievalProperties();
        RetrievalProperties.Vector vector = retrievalProperties.getVector();
        vector.setHost("192.168.0.134");
        vector.setPort(19530);
        vector.setDatabaseName(databaseName);
        vector.setRpcDeadlineMs(60000);
        return retrievalProperties;
    }

    private ConnectConfig connectConfig(MockedConstruction.Context context) {
        return (ConnectConfig) context.arguments().getFirst();
    }

    private boolean anyCreateDatabaseReq(CreateDatabaseReq request) {
        return request != null;
    }
}
