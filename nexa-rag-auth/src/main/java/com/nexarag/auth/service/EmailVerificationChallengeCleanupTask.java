package com.nexarag.auth.service;

import com.nexarag.auth.mapper.EmailVerificationChallengeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 定期清理过期邮箱验证码挑战元数据，控制认证安全表增长。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailVerificationChallengeCleanupTask {

    private final EmailVerificationChallengeMapper challengeMapper;

    /**
     * 清理已过期挑战；固定延迟避免多次任务重叠执行。
     */
    @Scheduled(fixedDelayString = "${nexa.auth.cleanup.email-challenge-interval-ms:3600000}")
    public void cleanupExpiredChallenges() {
        int deleted = challengeMapper.deleteExpiredBefore(LocalDateTime.now());
        if (deleted > 0) {
            log.info("已清理过期邮箱验证码挑战，count={}", deleted);
        }
    }
}
