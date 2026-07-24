package com.nexarag.model.entity.prompt;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Prompt 不可变正文版本实体，对应 prompt_version 表。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("prompt_version")
public class PromptVersion {
    /** 版本ID。 */
    @TableId("version_id")
    private Long versionId;
    /** Prompt 定义ID。 */
    private Long promptId;
    /** 定义内版本号。 */
    private Long versionNo;
    /** Mustache 模板正文。 */
    private String content;
    /** 正文 SHA-256 摘要。 */
    private String contentChecksum;
    /** 创建时的变量契约快照 JSON。 */
    private String variableSchemaSnapshot;
    /** 创建人。 */
    private String createdBy;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 变更说明。 */
    private String remark;
}
