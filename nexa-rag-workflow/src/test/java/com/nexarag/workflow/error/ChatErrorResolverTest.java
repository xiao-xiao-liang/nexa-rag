package com.nexarag.workflow.error;

import com.nexarag.common.exception.ClientException;
import org.apache.http.ConnectionClosedException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class ChatErrorResolverTest {

    @Test
    void resolveShouldIdentifyElasticsearchErrors() {
        Throwable esError = new DataAccessResourceFailureException("Connection is closed",
                new RuntimeException(new ConnectionClosedException("Connection is closed")));
        ChatErrorDetail detail = ChatErrorResolver.resolve(esError);
        assertThat(detail.errorCode()).isEqualTo("ELASTICSEARCH_SERVICE_ERROR");
        assertThat(detail.errorMessage()).contains("Elasticsearch");
    }

    @Test
    void resolveShouldIdentifyMilvusErrors() {
        Throwable milvusError = new RuntimeException("io.milvus.client.MilvusClientException: timeout");
        ChatErrorDetail detail = ChatErrorResolver.resolve(milvusError);
        assertThat(detail.errorCode()).isEqualTo("MILVUS_SERVICE_ERROR");
        assertThat(detail.errorMessage()).contains("Milvus");
    }

    @Test
    void resolveShouldIdentifyTimeoutErrors() {
        Throwable timeoutError = new RuntimeException(new SocketTimeoutException("Read timed out"));
        ChatErrorDetail detail = ChatErrorResolver.resolve(timeoutError);
        assertThat(detail.errorCode()).isEqualTo("TIMEOUT_ERROR");
        assertThat(detail.errorMessage()).contains("超时");
    }

    @Test
    void resolveShouldPreserveBusinessExceptions() {
        ClientException clientException = new ClientException("消息内容不合规");
        ChatErrorDetail detail = ChatErrorResolver.resolve(clientException);
        assertThat(detail.errorMessage()).isEqualTo("消息内容不合规");
    }
}
