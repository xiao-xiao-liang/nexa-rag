package com.nexarag.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexarag.auth.model.dataobject.TenantOwnershipTransferDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 企业租户所有者转交数据访问 Mapper。
 */
@Mapper
public interface TenantOwnershipTransferMapper extends BaseMapper<TenantOwnershipTransferDO> {

    /**
     * 锁定转交记录，供目标成员接受时原子更新成员角色。
     *
     * @param transferId 转交ID
     * @return 转交记录；不存在时返回 null
     */
    @Select("""
            SELECT transfer_id, tenant_id, current_owner_user_id, target_user_id, transfer_status,
                   expires_time, accepted_time, create_time, update_time
            FROM tenant_ownership_transfer WHERE transfer_id = #{transferId} FOR UPDATE
            """)
    TenantOwnershipTransferDO selectByIdForUpdate(@Param("transferId") Long transferId);
}
