package com.nexarag.model.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexarag.model.entity.prompt.PromptDefinition;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * Prompt 定义数据访问接口。
 */
@Mapper
public interface PromptDefinitionMapper extends BaseMapper<PromptDefinition> {
    /**
     * 按唯一编码查询 Prompt 定义。
     *
     * @param promptCode Prompt 编码
     * @return Prompt 定义，不存在时返回 null
     */
    @Select("SELECT prompt_id, prompt_code, name, variable_schema, enabled, current_release_id, "
            + "current_release_revision, create_time, update_time FROM prompt_definition WHERE prompt_code = #{promptCode}")
    PromptDefinition selectByPromptCode(String promptCode);

    /**
     * 按唯一编码查询 Prompt 定义并持有排他行锁。
     *
     * @param promptCode Prompt 编码
     * @return 已锁定的 Prompt 定义，不存在时返回 null
     */
    @Select("SELECT prompt_id, prompt_code, name, variable_schema, enabled, current_release_id, "
            + "current_release_revision, create_time, update_time FROM prompt_definition "
            + "WHERE prompt_code = #{promptCode} FOR UPDATE")
    PromptDefinition selectByPromptCodeForUpdate(String promptCode);

    /**
     * 查询所有已启用 Prompt 的当前发布代次。
     *
     * @return 仅包含 Prompt 编码和当前发布代次的定义列表
     */
    @Select("SELECT prompt_code, current_release_revision FROM prompt_definition "
            + "WHERE enabled = 1 AND current_release_revision IS NOT NULL")
    List<PromptDefinition> selectEnabledReleaseRevisions();
}
