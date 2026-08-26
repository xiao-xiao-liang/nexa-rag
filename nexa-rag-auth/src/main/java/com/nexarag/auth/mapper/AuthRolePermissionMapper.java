package com.nexarag.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexarag.auth.model.dataobject.AuthRolePermissionDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 全局角色权限关联数据访问 Mapper。
 *
 * <p>调用方必须按角色ID和权限ID组成明确条件，不得使用单主键更新或删除方法。</p>
 */
@Mapper
public interface AuthRolePermissionMapper extends BaseMapper<AuthRolePermissionDO> {
}
