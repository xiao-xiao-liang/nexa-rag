package com.nexarag.auth.service;

import com.nexarag.auth.config.BloomFilterConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static com.nexarag.auth.config.BloomFilterConfiguration.ACTIVE_VERSION;

/**
 * 认证邮箱和第三方身份关系的 BloomFilter 辅助索引。
 *
 * <p>所有返回值都只能用于跳过一次精确读取，数据库唯一索引和精确查询仍是最终裁决。</p>
 */
@Slf4j
@Service
public class AuthIdentityBloomFilterService {

    private static final String READY_MARKER_VALUE = "READY";

    private final RedissonClient redissonClient;
    private final RBloomFilter<String> emailIdentityBloomFilter;
    private final RBloomFilter<String> externalUserProviderBloomFilter;
    private final RBloomFilter<String> externalSubjectBloomFilter;

    public AuthIdentityBloomFilterService(RedissonClient redissonClient,
                                          @Qualifier("emailIdentityBloomFilter") RBloomFilter<String> emailIdentityBloomFilter,
                                          @Qualifier("externalUserProviderBloomFilter") RBloomFilter<String> externalUserProviderBloomFilter,
                                          @Qualifier("externalSubjectBloomFilter") RBloomFilter<String> externalSubjectBloomFilter) {
        this.redissonClient = redissonClient;
        this.emailIdentityBloomFilter = emailIdentityBloomFilter;
        this.externalUserProviderBloomFilter = externalUserProviderBloomFilter;
        this.externalSubjectBloomFilter = externalSubjectBloomFilter;
    }

    /**
     * Bloom 查询三态；UNKNOWN 必须回退数据库。
     */
    public enum BloomLookup {
        /**
         * 过滤器确认该元素不存在。
         */
        DEFINITELY_ABSENT,
        /**
         * 过滤器认为元素可能存在，仍需数据库确认。
         */
        MAY_EXIST,
        /**
         * 过滤器未就绪、重建或异常，必须回退数据库。
         */
        UNKNOWN
    }

    /**
     * 查询某用户是否可能拥有指定规范化邮箱。
     */
    public BloomLookup mightContainEmailOwner(Long userId, String normalizedEmail) {
        return mightContain(emailIdentityBloomFilter, BloomFilterConfiguration.emailFilterName(ACTIVE_VERSION), hash(userId + "|" + normalizedEmail));
    }

    /**
     * 查询某用户是否可能已绑定指定 OAuth 平台。
     */
    public BloomLookup mightContainExternalUserProvider(Long userId, String providerCode) {
        return mightContain(externalUserProviderBloomFilter,
                BloomFilterConfiguration.externalUserProviderFilterName(ACTIVE_VERSION),
                hash(userId + "|" + providerCode));
    }

    /**
     * 查询 OAuth 稳定主体是否可能已被绑定。
     */
    public BloomLookup mightContainExternalSubject(String providerCode, String providerSubject) {
        return mightContain(externalSubjectBloomFilter,
                BloomFilterConfiguration.externalSubjectFilterName(ACTIVE_VERSION),
                hash(providerCode + "|" + providerSubject));
    }

    /**
     * 在数据库写入前加入用户邮箱归属元素；失败仅记录并让数据库继续裁决。
     */
    public void recordEmailOwner(Long userId, String normalizedEmail) {
        add(emailIdentityBloomFilter,
                BloomFilterConfiguration.emailFilterName(ACTIVE_VERSION),
                hash(userId + "|" + normalizedEmail));
    }

    /**
     * 在数据库写入前加入用户平台绑定元素；失败仅记录并让数据库继续裁决。
     */
    public void recordExternalUserProvider(Long userId, String providerCode) {
        add(externalUserProviderBloomFilter,
                BloomFilterConfiguration.externalUserProviderFilterName(ACTIVE_VERSION),
                hash(userId + "|" + providerCode));
    }

    /**
     * 在数据库写入前加入 OAuth 稳定主体元素；失败仅记录并让数据库继续裁决。
     */
    public void recordExternalSubject(String providerCode, String providerSubject) {
        add(externalSubjectBloomFilter,
                BloomFilterConfiguration.externalSubjectFilterName(ACTIVE_VERSION),
                hash(providerCode + "|" + providerSubject));
    }

    private BloomLookup mightContain(RBloomFilter<String> bloomFilter, String filterName, String element) {
        try {
            if (hasReadyMarker()) {
                return BloomLookup.UNKNOWN;
            }
            assertExists(bloomFilter);
            return bloomFilter.contains(element) ? BloomLookup.MAY_EXIST : BloomLookup.DEFINITELY_ABSENT;
        } catch (RuntimeException exception) {
            log.warn("认证 BloomFilter 查询失败，name={}，reason={}", filterName, exception.toString());
            return BloomLookup.UNKNOWN;
        }
    }

    private void add(RBloomFilter<String> bloomFilter, String filterName, String element) {
        try {
            if (hasReadyMarker()) {
                return;
            }
            assertExists(bloomFilter);
            bloomFilter.add(element);
        } catch (RuntimeException exception) {
            log.warn("认证 BloomFilter 写入失败，name={}，reason={}", filterName, exception.toString());
        }
    }

    private void assertExists(RBloomFilter<String> bloomFilter) {
        if (!bloomFilter.isExists()) {
            throw new IllegalStateException("认证 BloomFilter 实体不存在");
        }
    }

    /**
     * Redis 中的版本就绪标记随过滤器数据一同丢失时，认证路径自动回退数据库；
     * 重建任务会先删除标记，回填完成后才恢复标记。
     */
    private boolean hasReadyMarker() {
        RBucket<String> marker = redissonClient.getBucket(BloomFilterConfiguration.readyMarkerName(ACTIVE_VERSION));
        return !READY_MARKER_VALUE.equals(marker.get());
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 未提供 SHA-256 算法", exception);
        }
    }
}
