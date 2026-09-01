package com.nexarag.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexarag.document.model.bo.DocumentChunkIndexWriteBO;
import com.nexarag.document.model.entity.DocumentChunk;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 文档片段数据访问接口，映射正文片段及其所属章节。
 */
@Mapper
public interface DocumentChunkMapper extends BaseMapper<DocumentChunk> {

    /**
     * 批量回写片段索引成功状态及外部索引ID。
     *
     * @param chunks 待回写的片段集合
     * @return 受影响行数
     */
    @Update("""
            <script>
            UPDATE document_chunk
            SET status = 'INDEXED',
                vector_id = CASE chunk_id
                <foreach collection='chunks' item='chunk'>
                    WHEN #{chunk.chunkId} THEN #{chunk.vectorId,jdbcType=VARCHAR}
                </foreach>
                ELSE vector_id END,
                keyword_index_id = CASE chunk_id
                <foreach collection='chunks' item='chunk'>
                    WHEN #{chunk.chunkId} THEN #{chunk.keywordIndexId,jdbcType=VARCHAR}
                </foreach>
                ELSE keyword_index_id END,
                failure_reason = NULL,
                update_time = NOW()
            WHERE chunk_id IN
            <foreach collection='chunks' item='chunk' open='(' separator=',' close=')'>
                #{chunk.chunkId}
            </foreach>
            </script>
            """)
    int batchMarkIndexed(@Param("chunks") List<DocumentChunkIndexWriteBO> chunks);

    /**
     * 按版本物理删除片段，供历史版本永久删除使用。
     */
    @Delete("""
            DELETE FROM document_chunk
            WHERE document_version_id = #{documentVersionId}
            """)
    int physicalDeleteByDocumentVersionId(@Param("documentVersionId") Long documentVersionId);
}
