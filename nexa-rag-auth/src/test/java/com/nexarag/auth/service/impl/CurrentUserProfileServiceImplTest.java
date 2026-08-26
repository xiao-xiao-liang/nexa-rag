package com.nexarag.auth.service.impl;

import com.nexarag.auth.mapper.AuthUserMapper;
import com.nexarag.auth.model.vo.LoginSessionVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * 当前登录用户资料组装测试。
 */
@ExtendWith(MockitoExtension.class)
class CurrentUserProfileServiceImplTest {

    @Mock
    private AuthUserMapper authUserMapper;

    @InjectMocks
    private CurrentUserProfileServiceImpl currentUserProfileService;

    /**
     * 当前用户资料应包含服务端查询的角色与全部权限。
     */
    @Test
    void shouldBuildProfileFromAuthoritativeRoleAndPermissions() {
        given(authUserMapper.selectRoleCodesByUserId(2L)).willReturn(List.of("USER"));
        given(authUserMapper.selectPermissionCodesByUserId(2L))
                .willReturn(List.of("prompt:manage", "crm:view"));

        LoginSessionVO profile = currentUserProfileService.getProfile(2L, "tenant-001");

        assertThat(profile.userId()).isEqualTo("2");
        assertThat(profile.tenantId()).isEqualTo("tenant-001");
        assertThat(profile.role()).isEqualTo("USER");
        assertThat(profile.permissions()).containsExactly("prompt:manage", "crm:view");
    }
}
