package com.nexarag.auth.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 创建租户成员邀请请求。 */
public record TenantInvitationCreateDTO(@NotNull(message = "租户ID不能为空") String tenantId,
                                        @NotBlank(message = "受邀账号或邮箱不能为空") String target) {
}
