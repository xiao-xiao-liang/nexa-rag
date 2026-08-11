package com.nexarag.infra.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 语雀来源读取配置。 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "nexa.source.yuque")
public class YuqueSourceProperties {

    /** 语雀个人访问令牌。 */
    private String token;
}
