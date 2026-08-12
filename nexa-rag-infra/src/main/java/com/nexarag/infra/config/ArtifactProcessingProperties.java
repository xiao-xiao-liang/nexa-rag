package com.nexarag.infra.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 文档解析制品处理配置，控制临时工作区和任务并发边界。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "nexa.parser.artifact")
public class ArtifactProcessingProperties {

    /** 临时工作区根目录。 */
    private Path tempRoot = Paths.get(System.getProperty("java.io.tmpdir"), "nexa-rag-artifacts");

    /** 单个工作区允许写入的最大字节数。 */
    private long maxWorkspaceBytes = 524_288_000L;

    /** 最大并发解析任务数。 */
    private int maxConcurrent = 2;
}
