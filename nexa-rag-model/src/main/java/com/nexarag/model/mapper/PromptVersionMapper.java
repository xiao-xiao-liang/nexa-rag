package com.nexarag.model.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexarag.model.entity.prompt.PromptVersion;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Mapper;

/**
 * Prompt 版本数据访问接口。
 */
@Mapper
public interface PromptVersionMapper extends BaseMapper<PromptVersion> {

    /** 查询下一个定义内版本号。 */
    @Select("SELECT COALESCE(MAX(version_no), 0) + 1 FROM prompt_version WHERE prompt_id = #{promptId}")
    Long selectNextVersionNo(Long promptId);

    /** 按正文摘要查询同定义内的既有版本。 */
    @Select("SELECT version_id, prompt_id, version_no, content, content_checksum, variable_schema_snapshot, "
            + "created_by, created_at, remark FROM prompt_version WHERE prompt_id = #{promptId} "
            + "AND content_checksum = #{contentChecksum} LIMIT 1")
    PromptVersion selectByContentChecksum(Long promptId, String contentChecksum);
}
