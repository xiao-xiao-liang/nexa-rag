package com.nexarag.document.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 文档稳定身份数据对象，对应 document 表，保存业务归属、版本指针和文档级审计。
 */
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("document")
public class Document {

    /**
     * 文档ID。
     */
    @TableId("document_id")
    private Long documentId;

    /**
     * 当前生效文档版本ID。
     */
    private Long activeVersionId;

    /**
     * 正在构建的文档版本ID。
     */
    private Long buildingVersionId;

    /**
     * 当前版本指针的生效代次。
     */
    private Long activationGeneration;

    /**
     * 所属知识库ID。
     */
    private Long knowledgeBaseId;

    /**
     * 文档标题。
     */
    private String title;

    /**
     * 文档描述。
     */
    private String description;

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
