package com.nexarag.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexarag.auth.model.dataobject.AuthUserDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 认证用户数据访问 Mapper。
 */
@Mapper
public interface AuthUserMapper extends BaseMapper<AuthUserDO> {

    /**
     * 按规范化账号名查询用户。
     *
     * @param accountNameKey 小写规范化账号名
     * @return 用户；不存在时返回 null
     */
    @Select("""
            SELECT user_id, account_name, display_name, account_name_key, role_id, status, default_tenant_id,
                   email, email_verified_time, create_time, update_time
            FROM auth_user
            WHERE account_name_key = #{accountNameKey}
            LIMIT 1
            """)
    AuthUserDO selectByAccountNameKey(@Param("accountNameKey") String accountNameKey);

    /**
     * 按规范化账号名锁定用户记录，用于密码验证期间保证账号状态一致。
     *
     * @param accountNameKey 小写规范化账号名
     * @return 已锁定用户；不存在时返回 null
     */
    @Select("""
            SELECT user_id, account_name, display_name, account_name_key, role_id, status, default_tenant_id,
                   email, email_verified_time, create_time, update_time
            FROM auth_user
            WHERE account_name_key = #{accountNameKey}
            FOR UPDATE
            """)
    AuthUserDO selectByAccountNameKeyForUpdate(@Param("accountNameKey") String accountNameKey);

    /**
     * 锁定用户记录，用于账号名、状态或默认租户等并发变更。
     *
     * @param userId 用户ID
     * @return 已锁定用户；不存在时返回 null
     */
    @Select("""
            SELECT user_id, account_name, display_name, account_name_key, role_id, status, default_tenant_id,
                   email, email_verified_time, create_time, update_time
            FROM auth_user
            WHERE user_id = #{userId}
            FOR UPDATE
            """)
    AuthUserDO selectByUserIdForUpdate(@Param("userId") Long userId);

    /**
     * 按规范化邮箱查询用户。
     *
     * @param email 规范化邮箱地址
     * @return 用户；不存在时返回 null
     */
    @Select("""
            SELECT user_id, account_name, display_name, account_name_key, role_id, status, default_tenant_id,
                   email, email_verified_time, create_time, update_time
            FROM auth_user
            WHERE email = #{email}
            LIMIT 1
            """)
    AuthUserDO selectByEmail(@Param("email") String email);

    /**
     * 按规范化邮箱锁定用户，用于邮箱归属变更和登录最终确认。
     *
     * @param email 规范化邮箱地址
     * @return 已锁定用户；不存在时返回 null
     */
    @Select("""
            SELECT user_id, account_name, display_name, account_name_key, role_id, status, default_tenant_id,
                   email, email_verified_time, create_time, update_time
            FROM auth_user
            WHERE email = #{email}
            FOR UPDATE
            """)
    AuthUserDO selectByEmailForUpdate(@Param("email") String email);

    /**
     * 按用户 ID 游标分页读取已绑定邮箱，用于低峰重建 BloomFilter。
     *
     * @param lastUserId 上一页最大用户 ID；首次传 0
     * @param limit      单页条数
     * @return 当前页已绑定邮箱的用户
     */
    @Select("""
            SELECT user_id, account_name, display_name, account_name_key, role_id, status, default_tenant_id,
                   email, email_verified_time, create_time, update_time
            FROM auth_user
            WHERE user_id > #{lastUserId}
              AND email IS NOT NULL
            ORDER BY user_id
            LIMIT #{limit}
            """)
    List<AuthUserDO> selectEmailUsersAfterUserId(@Param("lastUserId") long lastUserId, @Param("limit") long limit);

    /**
     * 查询启用用户通过其全局角色获得的权限编码。
     *
     * @param userId 用户ID
     * @return 权限编码列表；用户不存在、已禁用或无权限时返回空列表
     */
    @Select("""
            SELECT DISTINCT permission.permission_code
            FROM auth_user user
            INNER JOIN auth_role_permission role_permission ON role_permission.role_id = user.role_id
            INNER JOIN auth_permission permission ON permission.permission_id = role_permission.permission_id
            WHERE user.user_id = #{userId}
              AND user.status = 0
            """)
    List<String> selectPermissionCodesByUserId(@Param("userId") Long userId);

    /**
     * 查询启用用户的全局角色编码。
     *
     * @param userId 用户ID
     * @return 全局角色编码列表
     */
    @Select("""
            SELECT role.role_code
            FROM auth_user user
            INNER JOIN auth_role role ON role.role_id = user.role_id
            WHERE user.user_id = #{userId}
              AND user.status = 0
            """)
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);
}
