package com.nexarag.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexarag.auth.model.dataobject.PasswordCredentialDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 本地密码凭据数据访问 Mapper。
 */
@Mapper
public interface PasswordCredentialMapper extends BaseMapper<PasswordCredentialDO> {

    /**
     * 锁定指定用户的密码凭据，保证失败次数与冻结状态并发更新正确。
     *
     * @param userId 稳定用户ID
     * @return 已锁定的密码凭据；未设置密码时返回 null
     */
    @Select("""
            SELECT user_id, password_hash, failed_attempts, password_locked_until, password_changed_time,
                   create_time, update_time
            FROM auth_password_credential
            WHERE user_id = #{userId}
            FOR UPDATE
            """)
    PasswordCredentialDO selectByUserIdForUpdate(@Param("userId") Long userId);
}
