package com.nexarag.infra.queue.document;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 文档流水线 Redis Key 生成器，集中维护队列相关 key 命名。
 */
@Component
@RequiredArgsConstructor
public class DocumentPipelineQueueKeys {

    private static final String WAITING = "waiting";
    private static final String RUNNING = "running";
    private static final String LEASE = "lease";
    private static final String META = "meta";
    private static final String RETRY = "retry";
    private static final String SEQUENCE = "sequence";

    private final DocumentPipelineQueueProperties properties;

    /**
     * 生成等待队列 key。
     *
     * @return 等待队列 key
     */
    public String waitingKey() {
        return join(WAITING);
    }

    /**
     * 生成运行态 key。
     *
     * @return 运行态 key
     */
    public String runningKey() {
        return join(RUNNING);
    }

    /**
     * 生成租约 key。
     *
     * @param documentId 文档ID
     * @return 租约 key
     */
    public String leaseKey(Long documentId) {
        return join(LEASE, documentId);
    }

    /**
     * 生成租约 key 前缀。
     *
     * @return 租约 key 前缀
     */
    public String leaseKeyPrefix() {
        return join(LEASE) + ":";
    }

    /**
     * 生成元数据 key。
     *
     * @param documentId 文档ID
     * @return 元数据 key
     */
    public String metaKey(Long documentId) {
        return join(META, documentId);
    }

    /**
     * 生成重试副本 key。
     *
     * @param documentId 文档ID
     * @return 重试副本 key
     */
    public String retryKey(Long documentId) {
        return join(RETRY, documentId);
    }

    /**
     * 生成公平队列序号 key。
     *
     * @return 队列序号 key
     */
    public String sequenceKey() {
        return join(SEQUENCE);
    }

    private String join(Object... parts) {
        StringBuilder builder = new StringBuilder(normalizePrefix());
        for (Object part : parts) {
            builder.append(':').append(part);
        }
        return builder.toString();
    }

    private String normalizePrefix() {
        String keyPrefix = properties.getKeyPrefix();
        while (keyPrefix.endsWith(":")) {
            keyPrefix = keyPrefix.substring(0, keyPrefix.length() - 1);
        }
        return keyPrefix;
    }
}
