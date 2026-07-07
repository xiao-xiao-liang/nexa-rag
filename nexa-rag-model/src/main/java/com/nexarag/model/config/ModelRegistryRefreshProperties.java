package com.nexarag.model.config;

import com.nexarag.model.enums.ModelRefreshChannel;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 模型注册表刷新配置属性。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "nexa.model.registry")
public class ModelRegistryRefreshProperties {

    /**
     * 注册表刷新通道。
     */
    private ModelRefreshChannel refreshChannel = ModelRefreshChannel.LOCAL;

    /**
     * 注册表刷新消息主题。
     */
    private String refreshTopic = "nexa.model.registry.changed";
}
