package com.nexarag.model.dto;

import com.nexarag.model.enums.ModelRouteRole;
import lombok.Builder;

/**
 * 模型路由配置关联更新请求。
 */
@Builder
public record ModelRouteConfigUpdateRequest(ModelRouteRole role, Integer priority,
                                            Integer weight, Boolean enabled) {
}
