package com.nexarag.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexarag.auth.model.dataobject.AuthPermissionDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 全局权限数据访问 Mapper。
 */
@Mapper
public interface AuthPermissionMapper extends BaseMapper<AuthPermissionDO> {
}
