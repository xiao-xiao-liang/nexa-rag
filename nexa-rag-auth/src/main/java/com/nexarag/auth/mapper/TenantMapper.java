package com.nexarag.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexarag.auth.model.dataobject.TenantDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 租户主数据访问 Mapper。
 */
@Mapper
public interface TenantMapper extends BaseMapper<TenantDO> {

    /** 锁定并查询启用租户。 */
    @Select("""
            SELECT tenant_id, tenant_name, tenant_name_key, tenant_type, status, creator_user_id, create_time, update_time
            FROM tenant WHERE tenant_id = #{tenantId} AND status = 0 FOR UPDATE
            """)
    TenantDO selectActiveByIdForUpdate(@Param("tenantId") String tenantId);

    /** 按规范化名称查询租户。 */
    @Select("""
            SELECT tenant_id, tenant_name, tenant_name_key, tenant_type, status, creator_user_id, create_time, update_time
            FROM tenant WHERE tenant_name_key = #{tenantNameKey} LIMIT 1
            """)
    TenantDO selectByTenantNameKey(@Param("tenantNameKey") String tenantNameKey);
}
