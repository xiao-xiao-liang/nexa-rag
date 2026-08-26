package com.nexarag.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexarag.auth.model.dataobject.AuthRoleDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 全局角色数据访问 Mapper。
 */
@Mapper
public interface AuthRoleMapper extends BaseMapper<AuthRoleDO> {

    /**
     * 按系统角色编码查询角色。
     *
     * @param roleCode 角色编码
     * @return 角色记录；不存在时返回 null
     */
    @Select("""
            SELECT role_id, role_code, role_name, system_predefined, create_time, update_time
            FROM auth_role
            WHERE role_code = #{roleCode}
            LIMIT 1
            """)
    AuthRoleDO selectByRoleCode(@Param("roleCode") String roleCode);
}
