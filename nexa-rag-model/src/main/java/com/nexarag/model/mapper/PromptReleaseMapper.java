package com.nexarag.model.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexarag.model.entity.prompt.PromptRelease;
import org.apache.ibatis.annotations.Mapper;

/**
 * Prompt 发布记录数据访问接口。
 */
@Mapper
public interface PromptReleaseMapper extends BaseMapper<PromptRelease> {
}
