package com.nexarag.model.config;

import lombok.Getter;
import lombok.Setter;

/**
 * 模型路由配置。
 */
@Getter
@Setter
public class ModelRouteProperties {

    /**
     * 主模型 Profile。
     */
    private String primary;

    /**
     * 备用模型 Profile。
     */
    private String fallback;

    /**
     * 路由类型，初版支持 PRIMARY_FALLBACK。
     */
    private String type = "PRIMARY_FALLBACK";
}
