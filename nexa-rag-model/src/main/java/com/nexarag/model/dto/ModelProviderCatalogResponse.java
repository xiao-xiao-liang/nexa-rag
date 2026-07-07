package com.nexarag.model.dto;

import com.nexarag.model.enums.ModelProvider;
import com.nexarag.model.enums.ModelType;
import lombok.Builder;

import java.util.List;
import java.util.Map;

/**
 * 模型厂商推荐值响应。
 *
 * @param provider                     模型厂商
 * @param displayName                  展示名称
 * @param supportedTypes               支持的模型类型
 * @param defaultBaseUrl               默认服务地址
 * @param recommendedModels            推荐模型
 * @param apiKeyRequired               是否需要 API Key
 * @param openAiCompatible             是否兼容 OpenAI 协议
 * @param defaultEndpointPath          默认接口路径
 * @param defaultGovernanceDescription 默认治理说明
 */
@Builder
public record ModelProviderCatalogResponse(ModelProvider provider, String displayName,
                                           List<ModelType> supportedTypes, String defaultBaseUrl,
                                           Map<ModelType, List<String>> recommendedModels,
                                           boolean apiKeyRequired, Boolean openAiCompatible,
                                           String defaultEndpointPath,
                                           String defaultGovernanceDescription) {

    /**
     * 兼容前端更直观的字段命名。
     *
     * @return 支持的模型类型
     */
    public List<ModelType> supportedModelTypes() {
        return supportedTypes;
    }
}
