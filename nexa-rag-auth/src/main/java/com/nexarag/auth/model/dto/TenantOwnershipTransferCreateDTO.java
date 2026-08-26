package com.nexarag.auth.model.dto;

import jakarta.validation.constraints.NotNull;

/** 发起企业租户所有者转交请求。 */
public record TenantOwnershipTransferCreateDTO(@NotNull(message = "租户ID不能为空") String tenantId,
                                               @NotNull(message = "目标成员ID不能为空") Long targetUserId) {
}
