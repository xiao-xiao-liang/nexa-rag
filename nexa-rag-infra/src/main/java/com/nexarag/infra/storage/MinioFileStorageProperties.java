package com.nexarag.infra.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MinIO 文件存储配置属性，集中维护对象存储连接和桶策略。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "nexa.storage.minio")
public class MinioFileStorageProperties {

    /**
     * MinIO 服务地址。
     */
    private String endpoint = "http://127.0.0.1:9000";

    /**
     * MinIO 访问密钥。
     */
    private String accessKey = "";

    /**
     * MinIO 访问密钥密码。
     */
    private String secretKey = "";

    /**
     * 文档对象存储桶名称。
     */
    private String bucket = "nexa-rag";

    /**
     * 存储桶不存在时是否自动创建。
     */
    private boolean createBucket = true;
}
