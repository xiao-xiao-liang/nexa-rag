package com.nexarag.infra.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 飞书 Docx 应用身份读取配置。 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "nexa.source.feishu")
public class FeishuSourceProperties {
    private String appId;
    private String appSecret;
    private String baseUrl = "https://open.feishu.cn";
}
