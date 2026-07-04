package com.nexarag.infra.config;

import com.nexarag.infra.enums.StorageType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文件存储通用配置属性，负责描述当前启用的存储类型和基础连接信息。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "nexa.storage")
public class StorageProperties {

    /**
     * 当前启用的文件存储类型。
     */
    private StorageType type = StorageType.MINIO;

    /**
     * 存储服务地址。
     */
    private String endpoint = "http://127.0.0.1:9000";

    /**
     * 存储访问密钥。
     */
    private String accessKey = "";

    /**
     * 存储访问密钥密码。
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
