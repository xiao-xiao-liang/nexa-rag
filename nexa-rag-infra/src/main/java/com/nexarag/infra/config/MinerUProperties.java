package com.nexarag.infra.config;

import com.nexarag.infra.enums.MinerUClientMode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * MinerU 解析器配置属性，支持本地部署和官方服务两种调用模式。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "nexa.parser.mineru")
public class MinerUProperties {

    /** 是否启用 MinerU 解析器。 */
    private boolean enabled = true;

    /** MinerU 客户端模式。 */
    private MinerUClientMode mode = MinerUClientMode.LOCAL;

    /** 本地 MinerU 服务地址。 */
    private String localEndpoint = "http://127.0.0.1:8000";

    /** 本地 MinerU 文件解析路径。 */
    private String localParsePath = "/file_parse";

    /** 官方 MinerU 服务地址。 */
    private String officialEndpoint = "";

    /** 官方 MinerU API Key，只允许通过环境变量或外部配置注入。 */
    private String apiKey = "";

    /** 连接超时时间。 */
    private Duration connectTimeout = Duration.ofSeconds(3);

    /** 读取超时时间。 */
    private Duration readTimeout = Duration.ofSeconds(120);

    /** 官方异步任务轮询间隔。 */
    private Duration pollInterval = Duration.ofSeconds(2);

    /** 官方异步任务最大轮询次数。 */
    private int maxPollCount = 60;

    /** MinerU 全局最大并发解析任务数。 */
    private int concurrencyLimit = 5;

    /** MinerU 解析分布式信号量名称。 */
    private String semaphoreName = "nexa:mineru:parse";

    /** 获取 MinerU 解析许可最大等待秒数。 */
    private int maxWaitSeconds = 30;

    /** MinerU 解析许可自动释放秒数。 */
    private int leaseSeconds = 900;
}
