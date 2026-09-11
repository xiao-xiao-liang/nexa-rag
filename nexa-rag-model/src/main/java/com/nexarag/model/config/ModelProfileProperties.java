package com.nexarag.model.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 模型 Profile 配置，描述一组可调用的模型端点。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelProfileProperties {

    /**
     * 模型厂商，例如 OPENAI、OLLAMA、DASHSCOPE。
     */
    @Builder.Default
    private String provider = "OPENAI";

    /**
     * 模型服务地址。
     */
    private String baseUrl;

    /**
     * 模型接口路径，和 baseUrl 拼接后得到实际调用地址。
     */
    private String endpointPath;

    /**
     * API Key，禁止写入日志和明文持久化。
     */
    private String apiKey;

    /**
     * 模型名称。
     */
    private String modelName;

    /**
     * 请求超时时间，单位毫秒。
     */
    @Builder.Default
    private long timeoutMs = 60000;

    /**
     * 温度参数。
     */
    private Double temperature;

    /**
     * 最大输出 Token。
     */
    private Integer maxTokens;

    /**
     * 模型完整上下文窗口大小，单位 Token；0 表示未配置。
     */
    @Builder.Default
    private int contextWindowTokens = 0;

    /**
     * 为模型输出预留的 Token 数；0 表示由回答工作流使用保守默认值。
     */
    @Builder.Default
    private int reservedOutputTokens = 0;
}
