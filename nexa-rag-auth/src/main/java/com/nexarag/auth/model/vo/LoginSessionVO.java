package com.nexarag.auth.model.vo;

import java.util.List;

/**
 * 登录成功后返回给调用方的会话展示对象。
 *
 * @param userId 稳定用户ID
 * @param tenantId 当前选择的租户ID
 * @param role 当前用户的全局角色编码
 * @param permissions 当前用户的有效权限编码
 */
public record LoginSessionVO(String userId, String tenantId, String role, List<String> permissions) {

    /**
     * 兼容仅包含用户和租户的历史会话构造方式。
     *
     * @param userId 稳定用户ID
     * @param tenantId 当前选择的租户ID
     */
    public LoginSessionVO(String userId, String tenantId) {
        this(userId, tenantId, null, List.of());
    }
}
