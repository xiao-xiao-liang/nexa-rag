package com.nexarag.auth.redis;

/**
 * 已成功预占的邮箱验证码挑战，用于在数据库事务结束后完成消费或释放。
 *
 * @param challengeId      挑战 ID
 * @param reservationToken 仅本次事务持有的随机预占令牌
 */
public record EmailChallengeReservation(Long challengeId, String reservationToken) {
}
