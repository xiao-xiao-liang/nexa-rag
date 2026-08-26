package com.nexarag.auth.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 创建企业租户请求。 */
public record CreateTenantDTO(@NotBlank(message = "租户名称不能为空") @Size(max = 128, message = "租户名称长度不能超过128") String tenantName) {
}
