package com.nexarag.retrieval.index.vector;

import com.nexarag.retrieval.config.MilvusConfiguration;
import com.nexarag.retrieval.config.RetrievalProperties;
import io.milvus.param.ConnectParam;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SpringAiMilvusVectorStoreConfiguration 单元测试。
 */
class MilvusConfigurationTest {

    @Test
    void connectParamShouldUseConfiguredConnectionAndAuthorization() {
        RetrievalProperties properties = new RetrievalProperties();
        properties.getVector().setHost("milvus.example.internal");
        properties.getVector().setPort(19530);
        properties.getVector().setDatabaseName("nexa_rag");
        properties.getVector().setUsername("nexa");
        properties.getVector().setPassword("password");
        properties.getVector().setRpcDeadlineMs(30000L);

        ConnectParam connectParam = MilvusConfiguration.connectParam(properties);

        assertThat(connectParam.getHost()).isEqualTo("milvus.example.internal");
        assertThat(connectParam.getPort()).isEqualTo(19530);
        assertThat(connectParam.getDatabaseName()).isEqualTo("nexa_rag");
        assertThat(connectParam.getRpcDeadlineMs()).isEqualTo(30000L);
        assertThat(connectParam.getAuthorization()).isNotBlank();
    }
}
