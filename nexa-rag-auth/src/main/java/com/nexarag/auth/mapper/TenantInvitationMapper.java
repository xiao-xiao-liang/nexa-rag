package com.nexarag.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexarag.auth.model.dataobject.TenantInvitationDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 企业租户邀请数据访问 Mapper。
 */
@Mapper
public interface TenantInvitationMapper extends BaseMapper<TenantInvitationDO> {

    /**
     * 锁定邀请，供接受、拒绝和撤销操作保持状态一致。
     *
     * @param invitationId 邀请ID
     * @return 邀请记录；不存在时返回 null
     */
    @Select("""
            SELECT invitation_id, tenant_id, invited_user_id, inviter_user_id, invitation_status,
                   expires_time, responded_time, create_time, update_time
            FROM tenant_invitation WHERE invitation_id = #{invitationId} FOR UPDATE
            """)
    TenantInvitationDO selectByIdForUpdate(@Param("invitationId") Long invitationId);
}
