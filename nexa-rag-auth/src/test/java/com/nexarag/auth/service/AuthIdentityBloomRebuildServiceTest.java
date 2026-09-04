package com.nexarag.auth.service;

import com.nexarag.auth.mapper.AuthUserMapper;
import com.nexarag.auth.mapper.ExternalIdentityMapper;
import com.nexarag.auth.model.dataobject.AuthUserDO;
import com.nexarag.auth.model.dataobject.ExternalIdentityDO;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 认证身份 BloomFilter 低峰重建测试。 */
class AuthIdentityBloomRebuildServiceTest {

    @Test
    void shouldPopulateAllFiltersBeforeSettingReadyMarker() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RBloomFilter<String> emailFilter = mockBloomFilter();
        RBloomFilter<String> userProviderFilter = mockBloomFilter();
        RBloomFilter<String> subjectFilter = mockBloomFilter();
        RBucket<String> activeReadyMarker = mockStringBucket();
        RBucket<String> targetReadyMarker = mockStringBucket();
        when(redissonClient.<String>getBloomFilter("nexa:auth:bloom:email:v2")).thenReturn(emailFilter);
        when(redissonClient.<String>getBloomFilter("nexa:auth:bloom:external-user-provider:v2"))
                .thenReturn(userProviderFilter);
        when(redissonClient.<String>getBloomFilter("nexa:auth:bloom:external-subject:v2")).thenReturn(subjectFilter);
        when(redissonClient.<String>getBucket("nexa:auth:bloom:ready:v1")).thenReturn(activeReadyMarker);
        when(redissonClient.<String>getBucket("nexa:auth:bloom:ready:v2")).thenReturn(targetReadyMarker);

        AuthUserDO user = new AuthUserDO();
        user.setUserId(100L);
        user.setEmail("user@example.com");
        AuthUserMapper userMapper = mock(AuthUserMapper.class);
        when(userMapper.selectEmailUsersAfterUserId(anyLong(), anyLong())).thenReturn(List.of(user), List.of());
        ExternalIdentityDO identity = new ExternalIdentityDO();
        identity.setExternalIdentityId(200L);
        identity.setUserId(100L);
        identity.setProviderCode("github");
        identity.setProviderSubject("subject-1");
        ExternalIdentityMapper identityMapper = mock(ExternalIdentityMapper.class);
        when(identityMapper.selectAfterExternalIdentityId(anyLong(), anyLong()))
                .thenReturn(List.of(identity), List.of());

        AuthIdentityBloomRebuildService service = new AuthIdentityBloomRebuildService(redissonClient, userMapper,
                identityMapper);

        AuthIdentityBloomRebuildService.RebuildResult result = service.rebuild(2);

        org.assertj.core.api.Assertions.assertThat(result).isEqualTo(
                new AuthIdentityBloomRebuildService.RebuildResult(1, 1));
        var order = inOrder(activeReadyMarker, targetReadyMarker, emailFilter, userProviderFilter, subjectFilter);
        order.verify(activeReadyMarker).delete();
        order.verify(targetReadyMarker).delete();
        order.verify(emailFilter).add(anyString());
        order.verify(userProviderFilter).add(anyString());
        order.verify(subjectFilter).add(anyString());
        order.verify(targetReadyMarker).set("READY");
    }

    @SuppressWarnings("unchecked")
    private RBloomFilter<String> mockBloomFilter() {
        return mock(RBloomFilter.class);
    }

    @SuppressWarnings("unchecked")
    private RBucket<String> mockStringBucket() {
        return mock(RBucket.class);
    }
}
