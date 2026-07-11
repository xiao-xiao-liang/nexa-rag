package com.nexarag.retrieval.constants;

/**
 * Elasticsearch索引常量，集中维护响应处理等客户端约束。
 */
public final class ElasticsearchIndexConstants {

    /**
     * 异常信息中允许保留的最大响应正文长度。
     */
    public static final int MAX_RESPONSE_BODY_LENGTH = 1024;

    private ElasticsearchIndexConstants() {
    }
}
