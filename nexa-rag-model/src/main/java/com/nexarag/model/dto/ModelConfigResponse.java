package com.nexarag.model.dto;

import com.nexarag.model.enums.ModelProvider;
import com.nexarag.model.enums.ModelType;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 模型配置响应。
 */
@Builder
public record ModelConfigResponse(Long configId, String configKey, ModelType modelType,
                                  ModelProvider provider, String baseUrl, String apiKeyMask,
                                  String modelName, String endpointPath,
                                  Boolean enabled, Integer timeoutMs, Integer maxRetries, Long version,
                                  String extraConfig, String remark, LocalDateTime createTime,
                                  LocalDateTime updateTime) {
}
