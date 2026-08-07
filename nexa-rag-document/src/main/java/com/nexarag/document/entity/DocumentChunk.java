package com.nexarag.document.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.nexarag.document.enums.ChunkStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 文档片段实体，对应 document_chunk 表，保存切分后的文本片段。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("document_chunk")
public class DocumentChunk {

    /**
     * 片段ID。
     */
    @TableId("chunk_id")
    private String chunkId;

    /**
     * 文档ID。
     */
    private Long documentId;

    /**
     * 片段顺序。
     */
    private Integer chunkOrder;

    /**
     * 父片段ID。
     */
    private String parentChunkId;

    /**
     * 所属章节ID。
     */
    private Long sectionId;

    /**
     * 片段文本。
     */
    private String text;

    /**
     * 用于索引的片段内容。
     */
    private String indexContent;

    /**
     * 元数据JSON。
     */
    private String metadataJson;

    /**
     * Token数量。
     */
    private Integer tokenCount;

    /**
     * 片段状态。
     */
    private ChunkStatus status;

    /**
     * 是否跳过索引：0否，1是。
     */
    private Integer skipIndex;

    /**
     * 向量索引ID。
     */
    private String vectorId;

    /**
     * 关键词索引ID。
     */
    private String keywordIndexId;

    /**
     * 失败原因。
     */
    private String failureReason;

    /**
     * 创建时间。
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间。
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 创建人。
     */
    private String createBy;

    /**
     * 更新人。
     */
    private String updateBy;

    /**
     * 删除标记：0未删除，1已删除。
     */
    @TableLogic(value = "0", delval = "1")
    @TableField(fill = FieldFill.INSERT)
    private Integer delFlag;

    /**
     * 删除时间。
     */
    private LocalDateTime deleteTime;

    /**
     * 乐观锁版本号。
     */
    @Version
    private Integer version;
}
