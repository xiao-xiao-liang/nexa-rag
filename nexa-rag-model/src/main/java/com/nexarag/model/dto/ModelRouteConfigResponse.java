package com.nexarag.model.dto;

import com.nexarag.model.enums.ModelRouteRole;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 模型路由配置关联响应。
 */
@Builder
public record ModelRouteConfigResponse(Long routeConfigId, Long routeId, Long configId,
                                       ModelRouteRole role, Integer priority, Integer weight,
                                       Boolean enabled, LocalDateTime createTime, LocalDateTime updateTime) {
}
