package com.nexarag.infra.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Pandoc 文档转换配置。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "nexa.parser.pandoc")
public class PandocProperties {

    /**
     * 是否启用 DOCX 转换器。
     */
    private boolean enabled = true;

    /**
     * Pandoc 可执行文件。
     */
    private String executable = "pandoc";

    /**
     * 单次转换最长执行时间。
     */
    private Duration timeout = Duration.ofSeconds(120);

    /**
     * 标准错误最大保留字节数。
     */
    private int maxStderrBytes = 8192;
}
