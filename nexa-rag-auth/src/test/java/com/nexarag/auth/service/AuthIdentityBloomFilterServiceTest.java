package com.nexarag.auth.service;

import org.junit.jupiter.api.Test;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 身份绑定 BloomFilter 的降级边界测试。 */
class AuthIdentityBloomFilterServiceTest {

    @Test
    void shouldReturnUnknownWhenBloomFilterIsUnavailable() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RBucket<String> marker = mockStringBucket();
        RBloomFilter<String> subjectBloomFilter = mockBloomFilter();
        when(marker.get()).thenReturn("READY");
        when(redissonClient.<String>getBucket(anyString())).thenReturn(marker);
        when(subjectBloomFilter.isExists()).thenThrow(new IllegalStateException("redis down"));
        AuthIdentityBloomFilterService service = service(redissonClient, mockBloomFilter(), mockBloomFilter(),
                subjectBloomFilter);

        assertThat(service.mightContainExternalSubject("github", "subject-1"))
                .isEqualTo(AuthIdentityBloomFilterService.BloomLookup.UNKNOWN);
    }

    @Test
    void shouldBypassBloomFilterWhenReadyMarkerIsMissing() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RBucket<String> marker = mockStringBucket();
        when(redissonClient.<String>getBucket(anyString())).thenReturn(marker);
        AuthIdentityBloomFilterService service = service(redissonClient, mockBloomFilter(), mockBloomFilter(),
                mockBloomFilter());

        assertThat(service.mightContainEmailOwner(100L, "user@example.com"))
                .isEqualTo(AuthIdentityBloomFilterService.BloomLookup.UNKNOWN);
    }

    @Test
    void shouldUseBloomFilterWhenReadyMarkerExists() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RBucket<String> marker = mockStringBucket();
        RBloomFilter<String> emailBloomFilter = mockBloomFilter();
        when(marker.get()).thenReturn("READY");
        when(redissonClient.<String>getBucket(anyString())).thenReturn(marker);
        when(emailBloomFilter.isExists()).thenReturn(true);
        when(emailBloomFilter.contains(anyString())).thenReturn(false);
        AuthIdentityBloomFilterService service = service(redissonClient, emailBloomFilter, mockBloomFilter(),
                mockBloomFilter());

        assertThat(service.mightContainEmailOwner(100L, "user@example.com"))
                .isEqualTo(AuthIdentityBloomFilterService.BloomLookup.DEFINITELY_ABSENT);
    }

    @Test
    void shouldReturnUnknownWhenReadyMarkerExistsButFilterIsMissing() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RBucket<String> marker = mockStringBucket();
        RBloomFilter<String> emailBloomFilter = mockBloomFilter();
        when(marker.get()).thenReturn("READY");
        when(redissonClient.<String>getBucket(anyString())).thenReturn(marker);
        when(emailBloomFilter.isExists()).thenReturn(false);
        AuthIdentityBloomFilterService service = service(redissonClient, emailBloomFilter, mockBloomFilter(),
                mockBloomFilter());

        assertThat(service.mightContainEmailOwner(100L, "user@example.com"))
                .isEqualTo(AuthIdentityBloomFilterService.BloomLookup.UNKNOWN);
    }

    private AuthIdentityBloomFilterService service(RedissonClient redissonClient,
                                                    RBloomFilter<String> emailBloomFilter,
                                                    RBloomFilter<String> userProviderBloomFilter,
                                                    RBloomFilter<String> subjectBloomFilter) {
        return new AuthIdentityBloomFilterService(redissonClient, emailBloomFilter, userProviderBloomFilter,
                subjectBloomFilter);
    }

    @SuppressWarnings("unchecked")
    private RBucket<String> mockStringBucket() {
        return mock(RBucket.class);
    }

    @SuppressWarnings("unchecked")
    private RBloomFilter<String> mockBloomFilter() {
        return mock(RBloomFilter.class);
    }
}
