package com.nexarag.model.dto;

import com.nexarag.model.enums.ModelRouteStrategy;
import com.nexarag.model.enums.ModelType;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 模型路由响应。
 */
@Builder
public record ModelRouteResponse(Long routeId, String routeKey, ModelType modelType,
                                 ModelRouteStrategy strategy, Boolean enabled, String remark,
                                 LocalDateTime createTime, LocalDateTime updateTime) {
}
