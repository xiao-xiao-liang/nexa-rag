package com.nexarag.document.outbox.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexarag.document.outbox.entity.DocumentPipelineOutbox;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文档流水线Outbox数据访问接口。
 */
@Mapper
public interface DocumentPipelineOutboxMapper extends BaseMapper<DocumentPipelineOutbox> {
}
