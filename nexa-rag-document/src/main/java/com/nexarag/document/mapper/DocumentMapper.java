package com.nexarag.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexarag.document.model.bo.KnowledgeBaseDocumentStatisticsBO;
import com.nexarag.document.model.entity.Document;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Collection;
import java.util.List;

/**
 * 文档 Mapper。
 */
@Mapper
public interface DocumentMapper extends BaseMapper<Document> {

    /**
     * 聚合指定知识库中全部文档及其当前生效版本的处理状态。
     *
     * @param knowledgeBaseIds 知识库ID集合
     * @return 每个知识库一条统计结果
     */
    @Select("""
            <script>
            SELECT d.knowledge_base_id AS knowledgeBaseId,
                   COUNT(1) AS totalCount,
                   SUM(CASE WHEN dv.status = 'UPLOADED' THEN 1 ELSE 0 END) AS pendingCount,
                   SUM(CASE WHEN dv.status IN ('QUEUED', 'PARSING', 'PARSED', 'CHUNKING', 'CHUNKED', 'INDEXING')
                            THEN 1 ELSE 0 END) AS processingCount,
                   SUM(CASE WHEN dv.status = 'INDEX_READY' THEN 1 ELSE 0 END) AS indexedCount,
                   SUM(CASE WHEN dv.status = 'FAILED' THEN 1 ELSE 0 END) AS failedCount
            FROM document d
            LEFT JOIN document_version dv ON dv.document_version_id = d.active_version_id
            WHERE d.del_flag = 0
              AND d.knowledge_base_id IN
              <foreach collection='knowledgeBaseIds' item='knowledgeBaseId' open='(' separator=',' close=')'>
                #{knowledgeBaseId}
              </foreach>
            GROUP BY d.knowledge_base_id
            </script>
            """)
    List<KnowledgeBaseDocumentStatisticsBO> aggregateStatisticsByKnowledgeBaseIds(
            @Param("knowledgeBaseIds") Collection<Long> knowledgeBaseIds);

    /**
     * 在文档尚无构建中版本时原子占用构建指针。
     *
     * @param documentId        文档ID
     * @param documentVersionId 待构建的文档版本ID
     * @return 受影响行数，1表示占用成功，0表示已有构建任务或文档不可用
     */
    @Update("""
            UPDATE document
            SET building_version_id = #{documentVersionId}, update_time = NOW()
            WHERE document_id = #{documentId}
              AND del_flag = 0
              AND building_version_id IS NULL
            """)
    int trySetBuildingVersionId(@Param("documentId") Long documentId,
                                @Param("documentVersionId") Long documentVersionId);

    /**
     * 仅在构建指针仍指向指定版本时释放构建槽位。
     *
     * @param documentId        文档ID
     * @param documentVersionId 文档版本ID
     * @return 受影响行数
     */
    @Update("""
            UPDATE document
            SET building_version_id = NULL, update_time = NOW()
            WHERE document_id = #{documentId}
              AND building_version_id = #{documentVersionId}
            """)
    int clearBuildingVersionId(@Param("documentId") Long documentId,
                               @Param("documentVersionId") Long documentVersionId);

    /**
     * 将已完成预热的构建版本切换为当前生效版本，并递增生效代次。
     */
    @Update("""
            UPDATE document
            SET active_version_id = #{documentVersionId},
                building_version_id = NULL,
                activation_generation = activation_generation + 1,
                update_time = NOW()
            WHERE document_id = #{documentId}
              AND building_version_id = #{documentVersionId}
            """)
    int activateVersion(@Param("documentId") Long documentId,
                        @Param("documentVersionId") Long documentVersionId);

    /**
     * 将已预热的历史版本设为生效版本，不影响构建指针。
     */
    @Update("""
            UPDATE document
            SET active_version_id = #{documentVersionId}, activation_generation = activation_generation + 1,
                update_time = NOW()
            WHERE document_id = #{documentId} AND del_flag = 0
              AND active_version_id <> #{documentVersionId}
            """)
    int activateReadyVersion(@Param("documentId") Long documentId,
                             @Param("documentVersionId") Long documentVersionId);
}
