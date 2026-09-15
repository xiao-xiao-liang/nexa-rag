package com.nexarag.infra.observability.langfuse.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

import java.net.URI;

/**
 * Langfuse 可观测性连接与 Tokenizer 配置。
 *
 * <p>关闭时允许连接参数为空；启用时必须通过环境变量提供完整的 Langfuse 项目凭据。</p>
 */
@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "nexa.observability.langfuse")
public class LangfuseProperties {

    /** 是否启用 Langfuse 遥测。 */
    private boolean enabled;

    /** Langfuse 服务根地址。 */
    private URI host;

    /** Langfuse 项目 Public Key。 */
    private String publicKey;

    /** Langfuse 项目 Secret Key。 */
    private String secretKey;

    /** 当前部署环境标识。 */
    @NotBlank
    private String environment = "local";

    /** 是否允许上报输入与输出正文，默认关闭。 */
    private boolean captureContent;

    /** vLLM 分段 Tokenizer 配置。 */
    @Valid
    private TokenizerProperties tokenizer = new TokenizerProperties();

    /**
     * 校验启用 Langfuse 时所需的连接参数。
     *
     * @return true 表示配置完整或功能未启用
     */
    @AssertTrue(message = "启用 Langfuse 时必须配置有效的 Host、Public Key 与 Secret Key")
    public boolean isConnectionConfigured() {
        return !enabled || (host != null && StringUtils.hasText(publicKey) && StringUtils.hasText(secretKey));
    }

    /**
     * vLLM Tokenizer 的受控调用配置。
     */
    @Getter
    @Setter
    public static class TokenizerProperties {

        /** 是否启用语义分段 Token 统计。 */
        private boolean enabled = true;

        /** 单次 Tokenizer 请求超时，单位毫秒。 */
        @Min(1)
        private long timeoutMs = 800;

        /** 等待 Tokenizer 并发许可的最长时间，单位毫秒。 */
        @Min(1)
        private long acquireTimeoutMs = 1_000;

        /** 同一进程最多并发的 Tokenizer 请求数量。 */
        @Min(1)
        private int maxConcurrency = 2;

        /** 稳定文本 Token 结果缓存的最大条目数。 */
        @Min(1)
        private int cacheMaximumSize = 10_000;
    }
}
