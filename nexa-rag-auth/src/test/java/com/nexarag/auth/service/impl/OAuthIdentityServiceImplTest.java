package com.nexarag.auth.service.impl;

import com.nexarag.auth.enums.OAuthProvider;
import com.nexarag.auth.mapper.AuthUserMapper;
import com.nexarag.auth.mapper.EmailCredentialMapper;
import com.nexarag.auth.mapper.ExternalIdentityMapper;
import com.nexarag.auth.mapper.PasswordCredentialMapper;
import com.nexarag.auth.mapper.TenantMemberMapper;
import com.nexarag.auth.model.dataobject.AuthUserDO;
import com.nexarag.auth.model.dataobject.ExternalIdentityDO;
import com.nexarag.auth.service.AuthUserProvisioningService;
import com.nexarag.auth.service.CurrentUserProfileService;
import com.nexarag.auth.service.OAuthAccountNameGenerator;
import com.nexarag.auth.service.SecurityAuditService;
import com.nexarag.auth.service.SessionService;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OAuth 身份首次注册测试。
 */
class OAuthIdentityServiceImplTest {

    /**
     * 验证未绑定的第三方主体会自动生成账号、保存展示名称并写入身份绑定。
     */
    @Test
    void shouldAutomaticallyProvisionUserForUnboundOAuthIdentity() {
        ExternalIdentityMapper identityMapper = mock(ExternalIdentityMapper.class);
        AuthUserProvisioningService provisioningService = mock(AuthUserProvisioningService.class);
        OAuthAccountNameGenerator accountNameGenerator = mock(OAuthAccountNameGenerator.class);
        AuthUserDO user = new AuthUserDO();
        user.setUserId(100L);
        user.setDefaultTenantId("default");
        when(identityMapper.selectByProviderAndSubjectForUpdate("github", "subject-1")).thenReturn(null);
        when(accountNameGenerator.generate(OAuthProvider.GITHUB, "subject-1", "octocat", null))
                .thenReturn("octocat");
        when(provisioningService.createDefaultTenantUser("octocat", "octocat")).thenReturn(user);

        OAuthIdentityServiceImpl service = new OAuthIdentityServiceImpl(identityMapper, mock(AuthUserMapper.class),
                mock(TenantMemberMapper.class), mock(EmailCredentialMapper.class), mock(PasswordCredentialMapper.class),
                provisioningService, accountNameGenerator, mock(SessionService.class), mock(SecurityAuditService.class),
                mock(CurrentUserProfileService.class));

        service.loginOrRegister(OAuthProvider.GITHUB, "subject-1", "octocat", null);

        verify(provisioningService).createDefaultTenantUser("octocat", "octocat");
        verify(identityMapper).insert(any(ExternalIdentityDO.class));
    }
}
