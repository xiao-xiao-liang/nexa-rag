package com.nexarag.auth.config;

import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 认证身份 BloomFilter Bean 配置。
 *
 * <p>过滤器仅由低峰重建任务初始化；这里不调用 {@code tryInit}，以免 Redis 数据不完整时创建空过滤器。</p>
 */
@Configuration
public class BloomFilterConfiguration {

    /** 当前活动过滤器版本。切换版本时随应用代码发布。 */
    public static final int ACTIVE_VERSION = 1;

    /** 单个过滤器预期容纳的最大元素数量。 */
    public static final long EXPECTED_INSERTIONS = 1_000_000L;

    /** 单个过滤器允许的假阳性率。 */
    public static final double FALSE_POSITIVE_RATE = 0.001D;

    private static final String EMAIL_FILTER_PREFIX = "nexa:auth:bloom:email:v";
    private static final String EXTERNAL_USER_PROVIDER_FILTER_PREFIX = "nexa:auth:bloom:external-user-provider:v";
    private static final String EXTERNAL_SUBJECT_FILTER_PREFIX = "nexa:auth:bloom:external-subject:v";
    private static final String READY_MARKER_PREFIX = "nexa:auth:bloom:ready:v";

    /** 注册用户邮箱归属 BloomFilter。 */
    @Bean
    public RBloomFilter<String> emailIdentityBloomFilter(RedissonClient redissonClient) {
        return redissonClient.getBloomFilter(emailFilterName(ACTIVE_VERSION));
    }

    /** 注册用户与 OAuth 平台绑定关系 BloomFilter。 */
    @Bean
    public RBloomFilter<String> externalUserProviderBloomFilter(RedissonClient redissonClient) {
        return redissonClient.getBloomFilter(externalUserProviderFilterName(ACTIVE_VERSION));
    }

    /** 注册 OAuth 稳定主体归属 BloomFilter。 */
    @Bean
    public RBloomFilter<String> externalSubjectBloomFilter(RedissonClient redissonClient) {
        return redissonClient.getBloomFilter(externalSubjectFilterName(ACTIVE_VERSION));
    }

    /** 获取指定版本的邮箱归属过滤器名称。 */
    public static String emailFilterName(int version) {
        return EMAIL_FILTER_PREFIX + version;
    }

    /** 获取指定版本的用户与 OAuth 平台绑定过滤器名称。 */
    public static String externalUserProviderFilterName(int version) {
        return EXTERNAL_USER_PROVIDER_FILTER_PREFIX + version;
    }

    /** 获取指定版本的 OAuth 稳定主体过滤器名称。 */
    public static String externalSubjectFilterName(int version) {
        return EXTERNAL_SUBJECT_FILTER_PREFIX + version;
    }

    /** 获取指定版本的 Redis 就绪标记名称。 */
    public static String readyMarkerName(int version) {
        return READY_MARKER_PREFIX + version;
    }
}
