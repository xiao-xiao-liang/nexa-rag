package com.nexarag.auth.service;

import com.nexarag.auth.model.vo.LoginSessionVO;

/**
 * 当前登录用户资料服务，用于向前端提供服务端权威的角色和权限快照。
 */
public interface CurrentUserProfileService {

    /**
     * 根据稳定用户和当前租户组装用户资料。
     *
     * @param userId 稳定用户 ID
     * @param tenantId 当前租户 ID
     * @return 当前用户资料
     */
    LoginSessionVO getProfile(Long userId, String tenantId);
}
