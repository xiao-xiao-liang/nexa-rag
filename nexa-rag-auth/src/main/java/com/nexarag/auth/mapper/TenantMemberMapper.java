package com.nexarag.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexarag.auth.model.dataobject.TenantMemberDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 租户成员关系数据访问 Mapper。
 */
@Mapper
public interface TenantMemberMapper extends BaseMapper<TenantMemberDO> {

    /** 锁定成员关系，不过滤历史状态。 */
    @Select("""
            SELECT tenant_id, user_id, member_role, member_status, joined_time, status_changed_time
            FROM tenant_member WHERE tenant_id = #{tenantId} AND user_id = #{userId} FOR UPDATE
            """)
    TenantMemberDO selectByTenantIdAndUserIdForUpdate(@Param("tenantId") String tenantId,
                                                       @Param("userId") Long userId);

    /**
     * 锁定有效成员关系，用于切换租户、退出、移除和所有权转交。
     *
     * @param tenantId 租户ID
     * @param userId 用户ID
     * @return 已锁定成员关系；不存在或不是有效成员时返回 null
     */
    @Select("""
            SELECT tenant_id, user_id, member_role, member_status, joined_time, status_changed_time
            FROM tenant_member
            WHERE tenant_id = #{tenantId}
              AND user_id = #{userId}
              AND member_status = 0
            FOR UPDATE
            """)
    TenantMemberDO selectActiveByTenantIdAndUserIdForUpdate(@Param("tenantId") String tenantId,
                                                            @Param("userId") Long userId);

    /** 更新成员角色与状态。 */
    @Update("""
            UPDATE tenant_member SET member_role = #{memberRole}, member_status = #{memberStatus},
                status_changed_time = #{statusChangedTime}
            WHERE tenant_id = #{tenantId} AND user_id = #{userId}
            """)
    int updateRoleAndStatus(@Param("tenantId") String tenantId, @Param("userId") Long userId,
                            @Param("memberRole") Integer memberRole, @Param("memberStatus") Integer memberStatus,
                            @Param("statusChangedTime") java.time.LocalDateTime statusChangedTime);

    /** 统计当前启用的所有者数，防止产生无所有者租户。 */
    @Select("""
            SELECT COUNT(1) FROM tenant_member
            WHERE tenant_id = #{tenantId} AND member_role = 0 AND member_status = 0
            """)
    long countActiveOwners(@Param("tenantId") String tenantId);
}
