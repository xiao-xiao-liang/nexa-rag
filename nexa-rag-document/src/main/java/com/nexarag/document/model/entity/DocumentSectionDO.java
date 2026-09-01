package com.nexarag.document.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 文档章节数据对象，对应 document_section 表，用于持久化文档标题层级。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("document_section")
public class DocumentSectionDO {

    /**
     * 章节ID。
     */
    @TableId(value = "section_id", type = IdType.INPUT)
    private Long sectionId;

    /**
     * 文档ID。
     */
    private Long documentId;

    /**
     * 所属文档版本ID。
     */
    private Long documentVersionId;

    /**
     * 父章节ID。
     */
    private Long parentSectionId;

    /**
     * 章节标题。
     */
    private String title;

    /**
     * 标题层级路径JSON。
     */
    private String headingPathJson;

    /**
     * 标题层级。
     */
    private Integer headingLevel;

    /**
     * 章节起始行号。
     */
    private Integer startLine;

    /**
     * 章节结束行号。
     */
    private Integer endLine;

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
     * 删除标记：0未删除，1已删除。
     */
    @TableLogic(value = "0", delval = "1")
    @TableField(fill = FieldFill.INSERT)
    private Integer delFlag;
}
