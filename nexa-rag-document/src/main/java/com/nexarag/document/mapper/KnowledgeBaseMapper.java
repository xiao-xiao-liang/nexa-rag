package com.nexarag.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexarag.document.model.dataobject.KnowledgeBaseDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 知识库数据访问 Mapper。
 */
@Mapper
public interface KnowledgeBaseMapper extends BaseMapper<KnowledgeBaseDO> {

    /**
     * 锁定当前租户内仍有效的知识库，用于与文档创建、删除操作串行化。
     *
     * @param knowledgeBaseId 知识库ID
     * @param tenantId 租户ID
     * @return 已锁定的知识库；不存在或已删除时返回 null
     */
    @Select("""
            SELECT knowledge_base_id, tenant_id, name, active_name_key, description, is_default,
                   default_tenant_key, create_time, update_time, create_by, update_by, del_flag,
                   delete_time, version
            FROM knowledge_base
            WHERE knowledge_base_id = #{knowledgeBaseId}
              AND tenant_id = #{tenantId}
              AND del_flag = 0
            FOR UPDATE
            """)
    KnowledgeBaseDO selectActiveByIdForUpdate(@Param("knowledgeBaseId") Long knowledgeBaseId,
                                              @Param("tenantId") String tenantId);
}
