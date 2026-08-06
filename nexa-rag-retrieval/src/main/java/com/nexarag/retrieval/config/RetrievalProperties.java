package com.nexarag.retrieval.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 检索模块运行配置，用于控制 Embedding、向量索引和关键词索引的真实适配器开关。
 */
@Getter
@Setter
@Component
@Validated
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
     * 对话检索候选集配置。
     */
    @Valid
    private Candidate candidate = new Candidate();

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
         * 章节导航向量集合名称，保留给独立导航通道使用。
         */
        private String navigationCollectionName = "nexa_document_section_navigation";

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
         * 章节导航关键词索引名称，与正文片段索引物理隔离。
         */
        private String navigationIndexName = "nexa_document_section_navigation";

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

    /**
     * 候选召回、融合、重排序和证据组装的运行参数。
     */
    @Getter
    @Setter
    public static class Candidate {

        /**
         * 向量检索通道的候选数量。
         */
        @Min(1)
        private int vectorCandidateLimit = 20;

        /**
         * 关键词检索通道的候选数量。
         */
        @Min(1)
        private int keywordCandidateLimit = 20;

        /**
         * 初筛保留分数下限。
         */
        @DecimalMin("0.0")
        private double coarseScoreFloor = 0D;

        /**
         * RRF 融合后保留的候选数量。
         */
        @Min(1)
        private int rrfCandidateLimit = 20;

        /**
         * 重排序后保留的候选数量。
         */
        @Min(1)
        private int rerankCandidateLimit = 12;

        /**
         * 触发扩召时使用的候选数量。
         */
        @Min(1)
        private int expansionCandidateLimit = 8;

        /**
         * 章节扩展时最多补充的正文证据数量。
         */
        @Min(1)
        private int expansionEvidenceLimit = 3;

        /**
         * 证据正文的 Token 预算。
         */
        @Min(1)
        private int evidenceTokenBudget = 1800;

        /**
         * 初始正文证据不足此 Token 数时触发章节扩展。
         */
        @Min(1)
        private int expansionMinimumBodyTokens = 64;

        /**
         * 初始正文证据最高相关度低于此值时触发章节扩展。
         */
        @DecimalMin("0.0")
        private double expansionConfidenceThreshold = 0.1D;

        /**
         * 重排序结果可接受的最低分数。
         */
        @DecimalMin("0.0")
        private double acceptedRerankScore = 0D;
    }
}
