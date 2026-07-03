package com.nexarag.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexarag.document.entity.Document;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文档 Mapper。
 */
@Mapper
public interface DocumentMapper extends BaseMapper<Document> {
}
