package com.nexarag.infra.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 文档结构恢复的保守阈值和诊断数量配置。 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "nexa.parser.artifact.structure")
public class DocumentStructureProperties {

    /** 是否启用 DOCX 格式启发式标题。 */
    private boolean heuristicHeadingEnabled = true;

    /** 启发式标题允许进入章节树的最低置信度。 */
    private double heuristicHeadingMinConfidence = 0.80D;

    /** 单份文档保留的最多结构诊断数。 */
    private int maxDiagnostics = 100;

    /** 低置信标题的 LLM 精修配置。 */
    private LlmFallback llmFallback = new LlmFallback();

    /** 低置信标题的 LLM 精修配置。 */
    @Getter
    @Setter
    public static class LlmFallback {

        /** 是否启用 LLM 标题精修，默认关闭。 */
        private boolean enabled;

        /** 用于精修调用的模型路由键。 */
        private String routeKey;

        /** 可交给 LLM 精修的最高原始置信度。 */
        private double candidateMaxConfidence = 0.80D;

        /** 接受 LLM 结果所需的最低置信度。 */
        private double acceptedMinConfidence = 0.85D;

        /** 单篇文档允许精修的最多标题数。 */
        private int maxCandidates = 20;
    }
}
