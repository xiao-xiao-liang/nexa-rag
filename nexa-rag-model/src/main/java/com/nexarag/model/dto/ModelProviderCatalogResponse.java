package com.nexarag.model.dto;

import com.nexarag.model.enums.ModelProvider;
import com.nexarag.model.enums.ModelType;
import lombok.Builder;

import java.util.List;
import java.util.Map;

/**
 * 模型厂商推荐值响应。
 */
@Builder
public record ModelProviderCatalogResponse(ModelProvider provider, String displayName,
                                           List<ModelType> supportedTypes, String defaultBaseUrl,
                                           Map<ModelType, List<String>> recommendedModels,
                                           boolean apiKeyRequired) {
}
