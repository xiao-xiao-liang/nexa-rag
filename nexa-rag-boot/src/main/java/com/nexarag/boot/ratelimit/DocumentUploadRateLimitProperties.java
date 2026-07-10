package com.nexarag.boot.ratelimit;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文档上传限流配置属性，负责控制上传入口的跨实例并发许可。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "nexa.document.upload.rate-limit")
public class DocumentUploadRateLimitProperties {

    /** 是否启用文档上传限流。 */
    private boolean enabled = true;

    /** 文档上传分布式信号量名称。 */
    private String semaphoreName = "nexa:document:upload";

    /** 文档上传最大并发请求数。 */
    private int maxConcurrent = 10;

    /** 获取上传许可最大等待秒数。 */
    private int maxWaitSeconds = 5;

    /** 上传许可自动释放秒数。 */
    private int leaseSeconds = 300;
}
