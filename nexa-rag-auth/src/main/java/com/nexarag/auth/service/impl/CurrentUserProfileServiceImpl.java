package com.nexarag.auth.service.impl;

import com.nexarag.auth.mapper.AuthUserMapper;
import com.nexarag.auth.model.vo.LoginSessionVO;
import com.nexarag.auth.service.CurrentUserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 当前登录用户资料实现，始终从认证数据源读取角色和权限，避免前端推断身份。
 */
@Service
@RequiredArgsConstructor
public class CurrentUserProfileServiceImpl implements CurrentUserProfileService {

    private final AuthUserMapper authUserMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public LoginSessionVO getProfile(Long userId, String tenantId) {
        // 1. 读取全局角色；当前数据模型每个用户仅绑定一个全局角色
        List<String> roleCodes = authUserMapper.selectRoleCodesByUserId(userId);
        String role = roleCodes.isEmpty() ? null : roleCodes.getFirst();

        // 2. 读取有效角色授予的全部权限，返回不可变快照
        List<String> permissions = List.copyOf(authUserMapper.selectPermissionCodesByUserId(userId));

        // 3. 组装仅包含展示所需字段的会话资料
        return new LoginSessionVO(String.valueOf(userId), tenantId, role, permissions);
    }
}
