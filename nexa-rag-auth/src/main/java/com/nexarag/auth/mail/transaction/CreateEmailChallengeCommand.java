package com.nexarag.auth.mail.transaction;

import com.nexarag.auth.enums.EmailVerificationPurpose;

import java.time.LocalDateTime;

/**
 * RocketMQ 本地事务阶段创建验证码挑战所需的进程内命令。
 *
 * @param challengeId 挑战 ID
 * @param userId 关联用户 ID；匿名场景为空
 * @param email 原始收件邮箱，仅在本地事务阶段使用
 * @param emailKey 规范化邮箱键
 * @param purpose 验证码用途
 * @param contextHash 验证码上下文哈希
 * @param verificationCode 原始验证码，仅用于事务消息加密载荷，不写入数据库或 Redis
 * @param verificationCodeHash 带服务端密钥的验证码哈希，仅写入 Redis
 * @param expiresTime 过期时间
 * @param createTime 创建时间
 */
public record CreateEmailChallengeCommand(Long challengeId, Long userId, String email, String emailKey,
                                          EmailVerificationPurpose purpose, String contextHash,
                                          String verificationCode, String verificationCodeHash, LocalDateTime expiresTime,
                                          LocalDateTime createTime) {
}
