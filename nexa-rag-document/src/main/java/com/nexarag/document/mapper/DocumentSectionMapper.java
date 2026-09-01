package com.nexarag.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexarag.document.model.entity.DocumentSectionDO;
import org.apache.ibatis.annotations.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 文档章节数据访问接口，为章节树的持久化和查询提供基础能力。
 */
@Mapper
public interface DocumentSectionMapper extends BaseMapper<DocumentSectionDO> {

    /**
     * 按文档ID逻辑删除未删除的章节记录。
     *
     * @param documentId 文档ID
     * @return 受影响行数
     */
    @Update("""
            UPDATE document_section
            SET del_flag = 1, update_time = NOW()
            WHERE document_id = #{documentId} AND del_flag = 0
            """)
    int logicDeleteByDocumentId(@Param("documentId") Long documentId);

    /**
     * 按文档ID物理删除全部章节记录。
     *
     * @param documentId 文档ID
     * @return 受影响行数
     */
    @Delete("""
            DELETE FROM document_section
            WHERE document_id = #{documentId}
            """)
    int physicalDeleteByDocumentId(@Param("documentId") Long documentId);

    /**
     * 按文档版本ID物理删除全部章节记录。
     *
     * @param documentVersionId 文档版本ID
     * @return 受影响行数
     */
    @Delete("""
            DELETE FROM document_section
            WHERE document_version_id = #{documentVersionId}
            """)
    int physicalDeleteByDocumentVersionId(@Param("documentVersionId") Long documentVersionId);

    /**
     * 批量查询指定父章节集合的未删除子章节ID。
     *
     * @param documentId       文档ID
     * @param parentSectionIds 父章节ID集合
     * @return 子章节ID列表
     */
    @Select("""
            <script>
            SELECT section_id
            FROM document_section
            WHERE document_id = #{documentId}
              AND parent_section_id IN
              <foreach collection="parentSectionIds" item="parentSectionId" open="(" separator="," close=")">
                #{parentSectionId}
              </foreach>
              AND del_flag = 0
            ORDER BY start_line ASC, section_id ASC
            </script>
            """)
    List<Long> selectActiveChildSectionIds(@Param("documentId") Long documentId,
                                           @Param("parentSectionIds") List<Long> parentSectionIds);

    /**
     * 批量查询指定文档版本中指定父章节集合的未删除子章节ID。
     *
     * @param documentId        文档ID
     * @param documentVersionId 文档版本ID
     * @param parentSectionIds  父章节ID集合
     * @return 子章节ID列表
     */
    @Select("""
            <script>
            SELECT section_id
            FROM document_section
            WHERE document_id = #{documentId}
              AND document_version_id = #{documentVersionId}
              AND parent_section_id IN
              <foreach collection="parentSectionIds" item="parentSectionId" open="(" separator="," close=")">
                #{parentSectionId}
              </foreach>
              AND del_flag = 0
            ORDER BY start_line ASC, section_id ASC
            </script>
            """)
    List<Long> selectActiveChildSectionIdsByVersion(@Param("documentId") Long documentId,
                                                    @Param("documentVersionId") Long documentVersionId,
                                                    @Param("parentSectionIds") List<Long> parentSectionIds);

    /**
     * 查询指定根章节的全部后代章节ID，不包含根章节自身。
     *
     * @param documentId    文档ID
     * @param rootSectionId 根章节ID
     * @return 按层级遍历顺序排列的后代章节ID列表
     */
    default List<Long> selectDescendantSectionIds(Long documentId, Long rootSectionId) {
        if (documentId == null || rootSectionId == null) {
            return List.of();
        }

        Set<Long> visitedSectionIds = new LinkedHashSet<>();
        visitedSectionIds.add(rootSectionId);
        Set<Long> descendantSectionIds = new LinkedHashSet<>();
        List<Long> parentSectionIds = List.of(rootSectionId);

        while (!parentSectionIds.isEmpty()) {
            List<Long> childSectionIds = selectActiveChildSectionIds(documentId, parentSectionIds);
            Set<Long> nextParentSectionIds = new LinkedHashSet<>();
            if (childSectionIds != null) {
                for (Long sectionId : childSectionIds) {
                    if (sectionId != null && visitedSectionIds.add(sectionId)) {
                        descendantSectionIds.add(sectionId);
                        nextParentSectionIds.add(sectionId);
                    }
                }
            }
            parentSectionIds = new ArrayList<>(nextParentSectionIds);
        }
        return new ArrayList<>(descendantSectionIds);
    }

    /**
     * 查询指定文档版本根章节的全部后代章节ID，不包含根章节自身。
     */
    default List<Long> selectDescendantSectionIds(Long documentId, Long documentVersionId, Long rootSectionId) {
        if (documentId == null || documentVersionId == null || rootSectionId == null) {
            return List.of();
        }

        Set<Long> visitedSectionIds = new LinkedHashSet<>();
        visitedSectionIds.add(rootSectionId);
        Set<Long> descendantSectionIds = new LinkedHashSet<>();
        List<Long> parentSectionIds = List.of(rootSectionId);
        while (!parentSectionIds.isEmpty()) {
            List<Long> childSectionIds = selectActiveChildSectionIdsByVersion(documentId, documentVersionId,
                    parentSectionIds);
            Set<Long> nextParentSectionIds = new LinkedHashSet<>();
            if (childSectionIds != null) {
                for (Long sectionId : childSectionIds) {
                    if (sectionId != null && visitedSectionIds.add(sectionId)) {
                        descendantSectionIds.add(sectionId);
                        nextParentSectionIds.add(sectionId);
                    }
                }
            }
            parentSectionIds = new ArrayList<>(nextParentSectionIds);
        }
        return new ArrayList<>(descendantSectionIds);
    }
}
