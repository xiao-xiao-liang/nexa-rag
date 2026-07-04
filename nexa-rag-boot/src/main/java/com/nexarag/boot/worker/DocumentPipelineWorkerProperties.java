package com.nexarag.boot.worker;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文档流水线 Worker 配置属性，控制本地 Worker 的启停、并发和租约参数。
 */
@Component
@ConfigurationProperties(prefix = "nexa.document.pipeline")
public class DocumentPipelineWorkerProperties {

    /**
     * 处理模式，初版支持 local，预留 mq。
     */
    private String mode = "local";

    /**
     * 队列模式，初版使用整条流水线排队。
     */
    private String queueMode = "pipeline";

    /**
     * 是否启用本地 Worker。
     */
    private boolean workerEnabled = false;

    /**
     * 最大并发 Worker 数。
     */
    private int maxConcurrency = 2;

    /**
     * 空队列轮询间隔毫秒数。
     */
    private long pollIntervalMs = 1000L;

    /**
     * 任务租约秒数。
     */
    private long leaseTtlSeconds = 300L;

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getQueueMode() {
        return queueMode;
    }

    public void setQueueMode(String queueMode) {
        this.queueMode = queueMode;
    }

    public boolean isWorkerEnabled() {
        return workerEnabled;
    }

    public void setWorkerEnabled(boolean workerEnabled) {
        this.workerEnabled = workerEnabled;
    }

    public int getMaxConcurrency() {
        return maxConcurrency;
    }

    public void setMaxConcurrency(int maxConcurrency) {
        this.maxConcurrency = maxConcurrency;
    }

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public long getLeaseTtlSeconds() {
        return leaseTtlSeconds;
    }

    public void setLeaseTtlSeconds(long leaseTtlSeconds) {
        this.leaseTtlSeconds = leaseTtlSeconds;
    }
}