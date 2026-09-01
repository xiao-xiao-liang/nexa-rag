package com.nexarag.auth.service.impl;

import com.nexarag.auth.context.UserContext;
import com.nexarag.auth.mapper.AuthUserMapper;
import com.nexarag.auth.model.dataobject.AuthUserDO;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * 当前账号名提供者测试。
 */
class CurrentUserAccountNameProviderImplTest {

    @Test
    void shouldReturnAccountNameForCurrentUser() {
        AuthUserMapper authUserMapper = mock(AuthUserMapper.class);
        when(authUserMapper.selectById(7L)).thenReturn(new AuthUserDO(7L, "alice", "alice", 1L,
                0, "tenant-1", null, null));
        try (MockedStatic<UserContext> userContext = mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn("7");

            assertThat(new CurrentUserAccountNameProviderImpl(authUserMapper).getCurrentAccountName())
                    .isEqualTo("alice");
        }
    }
}
