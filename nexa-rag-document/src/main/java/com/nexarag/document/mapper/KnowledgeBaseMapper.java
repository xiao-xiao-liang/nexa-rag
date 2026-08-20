package com.nexarag.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexarag.document.model.dataobject.KnowledgeBaseDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识库数据访问 Mapper。
 */
@Mapper
public interface KnowledgeBaseMapper extends BaseMapper<KnowledgeBaseDO> {
}
