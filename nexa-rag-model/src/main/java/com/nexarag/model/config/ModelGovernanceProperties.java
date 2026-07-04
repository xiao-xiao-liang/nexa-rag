package com.nexarag.model.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * 模型治理配置。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "nexa.model")
public class ModelGovernanceProperties {

    /**
     * 模型 Profile 集合。
     */
    private Map<String, ModelProfileProperties> profiles = new HashMap<>();

    /**
     * 业务路由配置集合。
     */
    private Map<String, ModelRouteProperties> routes = new HashMap<>();
}
