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
 * Prompt 稳定定义实体，对应 prompt_definition 表。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("prompt_definition")
public class PromptDefinition {
    /** Prompt 定义ID。 */
    @TableId("prompt_id")
    private Long promptId;
    /** Prompt 唯一编码。 */
    private String promptCode;
    /** Prompt 名称。 */
    private String name;
    /** 变量契约 JSON。 */
    private String variableSchema;
    /** 是否启用。 */
    private Boolean enabled;
    /** 当前发布记录ID。 */
    private Long currentReleaseId;
    /** 当前发布代次。 */
    private Long currentReleaseRevision;
    /** 创建时间。 */
    private LocalDateTime createTime;
    /** 更新时间。 */
    private LocalDateTime updateTime;
}
