package com.nexarag.model.config;

import lombok.Getter;
import lombok.Setter;

/**
 * 模型 Profile 配置，描述一组可调用的模型端点。
 */
@Getter
@Setter
public class ModelProfileProperties {

    /**
     * 模型厂商，例如 OPENAI、OPENAI_COMPATIBLE、OLLAMA。
     */
    private String provider = "OPENAI_COMPATIBLE";

    /**
     * 模型服务地址。
     */
    private String baseUrl;

    /**
     * API Key，禁止写入日志和数据库。
     */
    private String apiKey;

    /**
     * 模型名称。
     */
    private String modelName;

    /**
     * 请求超时时间，单位毫秒。
     */
    private long timeoutMs = 60000;

    /**
     * 温度参数。
     */
    private Double temperature;

    /**
     * 最大输出 Token。
     */
    private Integer maxTokens;
}
