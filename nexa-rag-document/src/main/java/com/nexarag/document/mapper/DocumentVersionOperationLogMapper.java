package com.nexarag.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexarag.document.model.entity.DocumentVersionOperationLogDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文档版本操作审计数据访问接口。
 */
@Mapper
public interface DocumentVersionOperationLogMapper extends BaseMapper<DocumentVersionOperationLogDO> {
}
