package com.nexarag.retrieval.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 检索模块运行配置，用于控制 Embedding、向量索引和关键词索引的真实适配器开关。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "nexa.retrieval")
public class RetrievalProperties {

    /**
     * Embedding 生成配置。
     */
    private Embedding embedding = new Embedding();

    /**
     * 向量索引配置。
     */
    private Vector vector = new Vector();

    /**
     * 关键词索引配置。
     */
    private Keyword keyword = new Keyword();

    /**
     * Embedding 生成配置。
     */
    @Getter
    @Setter
    public static class Embedding {

        /**
         * Embedding 类型，可选值：model。
         */
        private String type = "model";

        /**
         * 默认模型路由Key。
         */
        private String routeKey = "embedding";

        /**
         * 单次向量化最大片段数量，避免超过云端 Embedding 接口批量限制。
         */
        private int maxBatchSize = 10;
    }

    /**
     * 向量索引配置，具体向量库由 type 指定，其余连接参数保持通用语义。
     */
    @Getter
    @Setter
    public static class Vector {

        /**
         * 向量索引类型，可选值：milvus。
         */
        private String type = "milvus";

        /**
         * 向量索引服务地址。
         */
        private String host = "192.168.0.134";

        /**
         * 向量索引服务端口。
         */
        private int port = 19530;

        /**
         * 向量库数据库名称，为空时使用默认数据库。
         */
        private String databaseName;

        /**
         * 文档片段向量集合名称。
         */
        private String collectionName = "nexa_document_chunk";

        /**
         * 向量维度，0 表示按首次写入向量维度创建集合。
         */
        private int dimension = 0;

        /**
         * 向量索引服务用户名，未启用鉴权时为空。
         */
        private String username;

        /**
         * 向量索引服务密码，未启用鉴权时为空。
         */
        private String password;

        /**
         * RPC 超时时间，单位毫秒。
         */
        private long rpcDeadlineMs = 60000;
    }

    /**
     * 关键词索引配置。
     */
    @Getter
    @Setter
    public static class Keyword {

        /**
         * 关键词索引类型，可选值：none/elasticsearch。
         */
        private String type = "none";

        /**
         * 关键词索引服务协议。
         */
        private String scheme = "http";

        /**
         * 关键词索引服务地址。
         */
        private String host = "192.168.0.134";

        /**
         * 关键词索引服务端口。
         */
        private int port = 9200;

        /**
         * 关键词索引名称。
         */
        private String indexName = "nexa_document_chunk";

        /**
         * 关键词索引服务用户名。
         */
        private String username = "elastic";

        /**
         * 关键词索引服务密码。
         */
        private String password = "";

        /**
         * 请求超时时间，单位毫秒。
         */
        private long requestTimeoutMs = 30000;
    }
}