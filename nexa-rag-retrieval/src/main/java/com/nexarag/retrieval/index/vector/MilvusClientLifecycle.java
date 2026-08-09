package com.nexarag.retrieval.index.vector;

import io.milvus.client.MilvusServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Spring AI Milvus 客户端生命周期管理器，确保应用停止时释放 gRPC 连接。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "nexa.retrieval.vector", name = "type", havingValue = "milvus")
public class MilvusClientLifecycle implements DisposableBean {

    private final MilvusServiceClient milvusServiceClient;

    /**
     * 关闭 Milvus 客户端连接。
     */
    @Override
    public void destroy() {
        try {
            milvusServiceClient.close(0L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("关闭Spring AI Milvus客户端时被中断", exception);
        }
    }
}
