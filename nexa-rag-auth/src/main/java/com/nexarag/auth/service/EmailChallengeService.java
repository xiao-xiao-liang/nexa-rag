package com.nexarag.auth.service;

import com.nexarag.auth.enums.EmailVerificationPurpose;
import com.nexarag.auth.model.vo.EmailChallengeVO;

/**
 * 邮箱验证码挑战服务，负责发送、限流、校验和单次消费。
 */
public interface EmailChallengeService {

    /**
     * 向指定邮箱发送用途受限的验证码。
     *
     * @param email 邮箱地址
     * @param purpose 验证码用途
     * @param userId 关联用户ID；匿名场景为 null
     * @return 新建挑战的摘要
     */
    EmailChallengeVO sendCode(String email, EmailVerificationPurpose purpose, Long userId);

    /**
     * 校验并单次消费验证码。
     *
     * @param challengeId 挑战ID
     * @param email 邮箱地址
     * @param purpose 验证码用途
     * @param userId 关联用户ID；匿名场景为 null
     * @param verificationCode 用户输入验证码
     */
    void verifyAndConsume(Long challengeId, String email, EmailVerificationPurpose purpose, Long userId,
                          String verificationCode);
}
