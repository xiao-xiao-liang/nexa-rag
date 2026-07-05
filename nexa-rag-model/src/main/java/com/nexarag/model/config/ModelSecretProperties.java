package com.nexarag.model.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 模型密钥配置属性。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "nexa.model.secret")
public class ModelSecretProperties {

    /**
     * API Key 加密主密钥。
     */
    private String masterKey;
}
