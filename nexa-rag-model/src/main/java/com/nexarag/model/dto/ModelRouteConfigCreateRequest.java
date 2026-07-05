package com.nexarag.model.dto;

import com.nexarag.model.enums.ModelRouteRole;
import lombok.Builder;

/**
 * 模型路由配置关联创建请求。
 */
@Builder
public record ModelRouteConfigCreateRequest(Long configId, ModelRouteRole role,
                                            Integer priority, Integer weight) {
}
