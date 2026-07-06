package com.nexarag.model.dto;

import com.nexarag.model.enums.ModelProvider;
import com.nexarag.model.enums.ModelType;
import lombok.Builder;

/**
 * 模型配置创建请求。
 */
@Builder
public record ModelConfigCreateRequest(String configKey, ModelType modelType, ModelProvider provider,
                                       String baseUrl, String endpointPath,
                                       String apiKey, String modelName,
                                       Integer timeoutMs, Integer maxRetries, String extraConfig, String remark) {
}
