package com.nexarag.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexarag.document.model.entity.DocumentChunk;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文档片段数据访问接口，映射正文片段及其所属章节。
 */
@Mapper
public interface DocumentChunkMapper extends BaseMapper<DocumentChunk> {
}
