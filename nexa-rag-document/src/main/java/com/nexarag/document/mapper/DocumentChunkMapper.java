package com.nexarag.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexarag.document.model.entity.DocumentChunk;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 文档片段数据访问接口，映射正文片段及其所属章节。
 */
@Mapper
public interface DocumentChunkMapper extends BaseMapper<DocumentChunk> {

    /**
     * 按版本物理删除片段，供历史版本永久删除使用。
     */
    @Delete("""
            DELETE FROM document_chunk
            WHERE document_version_id = #{documentVersionId}
            """)
    int physicalDeleteByDocumentVersionId(@Param("documentVersionId") Long documentVersionId);
}
