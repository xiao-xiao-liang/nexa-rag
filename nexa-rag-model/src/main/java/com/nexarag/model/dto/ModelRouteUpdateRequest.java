package com.nexarag.model.dto;

import com.nexarag.model.enums.ModelRouteStrategy;
import com.nexarag.model.enums.ModelType;
import lombok.Builder;

/**
 * 模型路由更新请求。
 */
@Builder
public record ModelRouteUpdateRequest(String routeKey, ModelType modelType,
                                      ModelRouteStrategy strategy, Boolean enabled, String remark) {
}
