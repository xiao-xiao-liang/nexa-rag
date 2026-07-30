package com.nexarag.model.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexarag.model.entity.ModelGovernanceConfig;
import org.apache.ibatis.annotations.Delete;

/**
 * 模型治理配置 Mapper，负责 model_governance_config 表数据访问。
 */
public interface ModelGovernanceConfigMapper extends BaseMapper<ModelGovernanceConfig> {

    /**
     * 物理删除指定治理配置，用于以数据库默认值重建同一主键记录。
     *
     * @param governanceId 治理配置ID
     * @return 删除行数
     */
    @Delete("DELETE FROM model_governance_config WHERE governance_id = #{governanceId}")
    int deletePhysicallyByGovernanceId(Long governanceId);
}
