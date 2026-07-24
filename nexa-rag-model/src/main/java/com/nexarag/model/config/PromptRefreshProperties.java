package com.nexarag.model.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Prompt 发布刷新和发布代次对账配置。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "nexa.prompt.refresh")
public class PromptRefreshProperties {

    /** Redis Pub/Sub 刷新消息主题。 */
    private String topic = "nexa.prompt.release.changed";

    /** 发布代次定时对账间隔，单位为毫秒。 */
    private long reconcileIntervalMs = 60_000L;
}
