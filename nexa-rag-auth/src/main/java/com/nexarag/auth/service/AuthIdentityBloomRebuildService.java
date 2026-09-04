package com.nexarag.auth.service;

import com.nexarag.auth.config.BloomFilterConfiguration;
import com.nexarag.auth.mapper.AuthUserMapper;
import com.nexarag.auth.mapper.ExternalIdentityMapper;
import com.nexarag.auth.model.dataobject.AuthUserDO;
import com.nexarag.auth.model.dataobject.ExternalIdentityDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * 在业务低峰期从数据库事实来源重建认证身份 BloomFilter。
 *
 * <p>重建开始时会删除当前活动版本与目标版本的 Redis 就绪标记，避免请求使用不完整过滤器。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthIdentityBloomRebuildService {

    private static final String READY_MARKER_VALUE = "READY";
    private static final long PAGE_SIZE = 500L;

    private final RedissonClient redissonClient;
    private final AuthUserMapper authUserMapper;
    private final ExternalIdentityMapper externalIdentityMapper;

    /**
     * 重建指定版本的全部认证身份过滤器，并在完整回填后写入就绪标记。
     *
     * @param version 待重建的正整数版本
     * @return 本次回填的邮箱与第三方身份数量
     */
    public RebuildResult rebuild(int version) {
        if (version <= 0) {
            throw new IllegalArgumentException("BloomFilter 重建版本必须为正整数");
        }

        // 1. 先撤销当前活动版本与目标版本的就绪标记，任何中途异常都会保持数据库兜底。
        RBucket<String> readyMarker = invalidateReadyMarkers(version);
        RBloomFilter<String> emailFilter = recreateFilter(BloomFilterConfiguration.emailFilterName(version));
        RBloomFilter<String> userProviderFilter = recreateFilter(
                BloomFilterConfiguration.externalUserProviderFilterName(version));
        RBloomFilter<String> subjectFilter = recreateFilter(
                BloomFilterConfiguration.externalSubjectFilterName(version));

        // 2. 以主键游标扫描数据库事实来源，避免大偏移分页。
        long emailCount = refillEmailOwners(emailFilter);
        long identityCount = refillExternalIdentities(userProviderFilter, subjectFilter);

        // 3. 仅在所有过滤器回填成功后发布就绪标记。
        readyMarker.set(READY_MARKER_VALUE);
        log.info("认证 BloomFilter 重建完成，version={}，emailCount={}，identityCount={}", version, emailCount, identityCount);
        return new RebuildResult(emailCount, identityCount);
    }

    private RBucket<String> invalidateReadyMarkers(int targetVersion) {
        int activeVersion = BloomFilterConfiguration.ACTIVE_VERSION;
        RBucket<String> activeReadyMarker = redissonClient.getBucket(
                BloomFilterConfiguration.readyMarkerName(activeVersion));
        activeReadyMarker.delete();
        if (activeVersion == targetVersion) {
            return activeReadyMarker;
        }
        RBucket<String> targetReadyMarker = redissonClient.getBucket(
                BloomFilterConfiguration.readyMarkerName(targetVersion));
        targetReadyMarker.delete();
        return targetReadyMarker;
    }

    private RBloomFilter<String> recreateFilter(String filterName) {
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter(filterName);
        bloomFilter.delete();
        bloomFilter.tryInit(BloomFilterConfiguration.EXPECTED_INSERTIONS,
                BloomFilterConfiguration.FALSE_POSITIVE_RATE);
        return bloomFilter;
    }

    private long refillEmailOwners(RBloomFilter<String> emailFilter) {
        long cursor = 0L;
        long count = 0L;
        while (true) {
            List<AuthUserDO> users = authUserMapper.selectEmailUsersAfterUserId(cursor, PAGE_SIZE);
            if (users.isEmpty()) {
                return count;
            }
            for (AuthUserDO user : users) {
                emailFilter.add(hash(user.getUserId() + "|" + user.getEmail()));
                cursor = user.getUserId();
                count++;
            }
        }
    }

    private long refillExternalIdentities(RBloomFilter<String> userProviderFilter, RBloomFilter<String> subjectFilter) {
        long cursor = 0L;
        long count = 0L;
        while (true) {
            List<ExternalIdentityDO> identities = externalIdentityMapper.selectAfterExternalIdentityId(cursor, PAGE_SIZE);
            if (identities.isEmpty()) {
                return count;
            }
            for (ExternalIdentityDO identity : identities) {
                userProviderFilter.add(hash(identity.getUserId() + "|" + identity.getProviderCode()));
                subjectFilter.add(hash(identity.getProviderCode() + "|" + identity.getProviderSubject()));
                cursor = identity.getExternalIdentityId();
                count++;
            }
        }
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 未提供 SHA-256 算法", exception);
        }
    }

    /**
     * 重建结果摘要。
     *
     * @param emailCount    回填的用户邮箱数量
     * @param identityCount 回填的第三方身份数量
     */
    public record RebuildResult(long emailCount, long identityCount) {
    }
}
