package com.nexarag.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexarag.auth.model.dataobject.EmailCredentialDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 已验证邮箱凭据数据访问 Mapper。
 */
@Mapper
public interface EmailCredentialMapper extends BaseMapper<EmailCredentialDO> {

    /**
     * 按用户ID锁定其当前邮箱凭据。
     *
     * @param userId 用户ID
     * @return 邮箱凭据；不存在时返回 null
     */
    @Select("""
            SELECT user_id, email, email_key, verified_time, create_time, update_time
            FROM auth_email_credential
            WHERE user_id = #{userId}
            FOR UPDATE
            """)
    EmailCredentialDO selectByUserIdForUpdate(@Param("userId") Long userId);

    /**
     * 按规范化邮箱键查询当前邮箱凭据。
     *
     * @param emailKey 规范化邮箱键
     * @return 邮箱凭据；不存在时返回 null
     */
    @Select("""
            SELECT user_id, email, email_key, verified_time, create_time, update_time
            FROM auth_email_credential
            WHERE email_key = #{emailKey}
            LIMIT 1
            """)
    EmailCredentialDO selectByEmailKey(@Param("emailKey") String emailKey);

    /**
     * 按规范化邮箱键锁定当前邮箱凭据，用于邮箱登录与邮箱更换流程。
     *
     * @param emailKey 规范化邮箱键
     * @return 已锁定邮箱凭据；不存在时返回 null
     */
    @Select("""
            SELECT user_id, email, email_key, verified_time, create_time, update_time
            FROM auth_email_credential
            WHERE email_key = #{emailKey}
            FOR UPDATE
            """)
    EmailCredentialDO selectByEmailKeyForUpdate(@Param("emailKey") String emailKey);
}
