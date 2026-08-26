package com.nexarag.auth.model.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 邮箱验证码挑战元数据对象，对应 auth_email_verification_challenge 表。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@TableName("auth_email_verification_challenge")
public class EmailVerificationChallengeDO {

    /** 验证码挑战ID。 */
    @TableId(value = "challenge_id", type = IdType.INPUT)
    private Long challengeId;

    /** 关联用户ID；匿名注册场景为空。 */
    private Long userId;

    /** 用于匹配的规范化邮箱键。 */
    private String emailKey;

    /** 验证码用途编码。 */
    private String purposeCode;

    /** 绑定用户、邮箱和用途的上下文哈希。 */
    private String contextHash;

    /** 验证码过期时间。 */
    private LocalDateTime expiresTime;

    /** 当前验证失败次数。 */
    private Integer verifyAttempts;

    /** 验证码成功消费时间。 */
    private LocalDateTime consumedTime;

    /** 重发或主动撤销时间。 */
    private LocalDateTime invalidatedTime;

    /** 创建时间。 */
    private LocalDateTime createTime;
}
