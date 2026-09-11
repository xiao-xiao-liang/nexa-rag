package com.nexarag.workflow.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 回答模型输入窗口的证据预算配置。
 *
 * <p>当路由模型没有声明完整上下文窗口或输出预留时，使用本配置作为兼容兜底；
 * 已在模型配置中声明的值始终优先。</p>
 */
@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "nexa.chat.model-input")
public class ModelInputEvidenceProperties {

    /** 未声明模型上下文窗口时使用的兜底窗口，单位 Token。 */
    @Min(1)
    private int fallbackContextWindowTokens = 8192;

    /** 未声明模型输出预留时使用的默认预留，单位 Token。 */
    @Min(0)
    private int fallbackReservedOutputTokens = 1024;

    /** 输入预算中额外预留的安全余量，单位 Token。 */
    @Min(0)
    private int inputSafetyMarginTokens = 512;
}
