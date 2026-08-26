package com.nexarag.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexarag.auth.model.dataobject.EmailVerificationChallengeDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 邮箱验证码挑战元数据数据访问 Mapper。
 */
@Mapper
public interface EmailVerificationChallengeMapper extends BaseMapper<EmailVerificationChallengeDO> {

    /**
     * 删除已过期的验证码挑战元数据；验证码哈希由 Redis TTL 自动清理。
     *
     * @param expiresBefore 过期时间上界
     * @return 删除记录数
     */
    @Delete("""
            DELETE FROM auth_email_verification_challenge
            WHERE expires_time < #{expiresBefore}
            """)
    int deleteExpiredBefore(@Param("expiresBefore") LocalDateTime expiresBefore);

    /**
     * 在挑战仍可使用时原子增加错误验证次数。
     *
     * @param challengeId 挑战 ID
     * @param now 当前时间
     * @param maxVerifyAttempts 最大错误次数
     * @return 受影响行数
     */
    @Update("""
            UPDATE auth_email_verification_challenge
            SET verify_attempts = verify_attempts + 1
            WHERE challenge_id = #{challengeId}
              AND consumed_time IS NULL
              AND invalidated_time IS NULL
              AND expires_time > #{now}
              AND verify_attempts < #{maxVerifyAttempts}
            """)
    int incrementVerifyAttemptsIfActive(@Param("challengeId") Long challengeId,
                                         @Param("now") LocalDateTime now,
                                         @Param("maxVerifyAttempts") int maxVerifyAttempts);

    /**
     * 按完整验证码上下文原子消费挑战，避免并发请求重复使用同一验证码。
     *
     * @param challengeId 挑战 ID
     * @param emailKey 规范化邮箱键
     * @param purposeCode 验证码用途
     * @param userId 关联用户 ID；匿名场景为空
     * @param contextHash 挑战上下文哈希
     * @param now 当前时间
     * @param maxVerifyAttempts 最大错误次数
     * @return 受影响行数；1 表示当前请求成功消费
     */
    @Update("""
            UPDATE auth_email_verification_challenge
            SET consumed_time = #{now}
            WHERE challenge_id = #{challengeId}
              AND email_key = #{emailKey}
              AND purpose_code = #{purposeCode}
              AND ((user_id IS NULL AND #{userId} IS NULL) OR user_id = #{userId})
              AND context_hash = #{contextHash}
              AND consumed_time IS NULL
              AND invalidated_time IS NULL
              AND expires_time > #{now}
              AND verify_attempts < #{maxVerifyAttempts}
            """)
    int consumeIfActive(@Param("challengeId") Long challengeId,
                        @Param("emailKey") String emailKey,
                        @Param("purposeCode") String purposeCode,
                        @Param("userId") Long userId,
                        @Param("contextHash") String contextHash,
                        @Param("now") LocalDateTime now,
                        @Param("maxVerifyAttempts") int maxVerifyAttempts);

    /**
     * 将同一邮箱和用途的历史未消费验证码作废。
     *
     * @param emailKey 规范化邮箱键
     * @param purposeCode 验证码用途编码
     * @param invalidatedTime 作废时间
     * @return 被作废记录数量
     */
    @Update("""
            UPDATE auth_email_verification_challenge
            SET invalidated_time = #{invalidatedTime}
            WHERE email_key = #{emailKey}
              AND purpose_code = #{purposeCode}
              AND consumed_time IS NULL
              AND invalidated_time IS NULL
            """)
    int invalidateActiveByEmailKeyAndPurpose(@Param("emailKey") String emailKey,
                                             @Param("purposeCode") String purposeCode,
                                             @Param("invalidatedTime") LocalDateTime invalidatedTime);
}
