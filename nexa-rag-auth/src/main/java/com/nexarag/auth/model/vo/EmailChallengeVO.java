package com.nexarag.auth.model.vo;

import java.time.LocalDateTime;

/**
 * 邮箱验证码挑战展示对象。
 *
 * @param challengeId 提交验证码时使用的挑战ID
 * @param expiresTime 验证码过期时间
 */
public record EmailChallengeVO(Long challengeId, LocalDateTime expiresTime) {
}
