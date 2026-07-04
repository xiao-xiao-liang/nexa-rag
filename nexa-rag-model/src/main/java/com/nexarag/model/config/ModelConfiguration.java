package com.nexarag.model.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 模型模块配置入口。
 */
@Configuration
@EnableConfigurationProperties(ModelGovernanceProperties.class)
public class ModelConfiguration {
}
