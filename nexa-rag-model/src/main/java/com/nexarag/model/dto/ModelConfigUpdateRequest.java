package com.nexarag.model.dto;

import com.nexarag.model.enums.ModelProvider;
import com.nexarag.model.enums.ModelType;
import lombok.Builder;

/**
 * 模型配置更新请求。
 */
@Builder
public record ModelConfigUpdateRequest(String configKey, ModelType modelType, ModelProvider provider,
                                       String baseUrl, String endpointPath,
                                       String apiKey, String modelName,
                                       Boolean enabled, Integer timeoutMs, Integer maxRetries,
                                       String extraConfig, String remark) {
}
