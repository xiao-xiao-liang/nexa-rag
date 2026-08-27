package com.nexarag.auth.service;

import com.nexarag.auth.enums.AuthErrorCode;
import com.nexarag.auth.enums.OAuthProvider;
import com.nexarag.auth.mapper.AuthUserMapper;
import com.nexarag.auth.model.dataobject.AuthUserDO;
import com.nexarag.common.exception.ClientException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OAuth 首次注册账号名生成测试。
 */
class OAuthAccountNameGeneratorTest {

    /**
     * 验证未占用且符合账号规则的第三方名称可直接作为本地账号名。
     */
    @Test
    void shouldPreferAvailableValidDisplayName() {
        AccountNamePolicy policy = mock(AccountNamePolicy.class);
        AuthUserMapper userMapper = mock(AuthUserMapper.class);
        when(policy.normalizeAndValidate("octocat")).thenReturn("octocat");
        when(userMapper.selectByAccountNameKey("octocat")).thenReturn(null);

        String accountName = new OAuthAccountNameGenerator(policy, userMapper)
                .generate(OAuthProvider.GITHUB, "github-subject", "octocat", null);

        assertThat(accountName).isEqualTo("octocat");
    }

    /**
     * 验证不符合账号规则的昵称不会导致 OAuth 首次登录失败，而会得到安全的确定性账号名。
     */
    @Test
    void shouldGenerateSafeAccountNameWhenDisplayNameIsInvalid() {
        AccountNamePolicy policy = mock(AccountNamePolicy.class);
        AuthUserMapper userMapper = mock(AuthUserMapper.class);
        when(policy.normalizeAndValidate("飞书 用户"))
                .thenThrow(new ClientException(AuthErrorCode.ACCOUNT_NAME_INVALID));
        when(userMapper.selectByAccountNameKey(anyString())).thenReturn(null);

        String accountName = new OAuthAccountNameGenerator(policy, userMapper)
                .generate(OAuthProvider.FEISHU, "open-id-123", "飞书 用户", null);

        assertThat(accountName).matches("feishu-[0-9a-f]{16}");
        assertThat(accountName).doesNotContain("open-id-123");
    }

    /**
     * 验证调用方显式给出的已校验账号名始终优先，保持既有接口兼容。
     */
    @Test
    void shouldKeepExplicitAccountName() {
        AccountNamePolicy policy = mock(AccountNamePolicy.class);
        AuthUserMapper userMapper = mock(AuthUserMapper.class);

        String accountName = new OAuthAccountNameGenerator(policy, userMapper)
                .generate(OAuthProvider.GOOGLE, "google-subject", "Google User", "chosen-name");

        assertThat(accountName).isEqualTo("chosen-name");
        verify(policy, never()).normalizeAndValidate(anyString());
        verify(userMapper, never()).selectByAccountNameKey(anyString());
    }

    /**
     * 验证哈希候选名已占用时，生成器会追加确定性序号避免冲突。
     */
    @Test
    void shouldAppendSuffixWhenGeneratedAccountNameIsOccupied() {
        AccountNamePolicy policy = mock(AccountNamePolicy.class);
        AuthUserMapper userMapper = mock(AuthUserMapper.class);
        when(userMapper.selectByAccountNameKey(anyString()))
                .thenReturn(new AuthUserDO(), (AuthUserDO) null);

        String accountName = new OAuthAccountNameGenerator(policy, userMapper)
                .generate(OAuthProvider.QQ, "qq-subject", null, null);

        assertThat(accountName).matches("qq-[0-9a-f]{16}-2");
    }
}
