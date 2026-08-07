package com.nexarag.document.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
