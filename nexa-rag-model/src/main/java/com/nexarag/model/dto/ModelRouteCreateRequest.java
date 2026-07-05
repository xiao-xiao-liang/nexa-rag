package com.nexarag.model.dto;

import com.nexarag.model.enums.ModelRouteStrategy;
import com.nexarag.model.enums.ModelType;
import lombok.Builder;

/**
 * 模型路由创建请求。
 */
@Builder
public record ModelRouteCreateRequest(String routeKey, ModelType modelType,
                                      ModelRouteStrategy strategy, String remark) {
}
