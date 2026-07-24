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
 * Prompt 发布决策记录实体，对应 prompt_release 表。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("prompt_release")
public class PromptRelease {
    /** 发布记录ID。 */
    @TableId("release_id")
    private Long releaseId;
    /** Prompt 定义ID。 */
    private Long promptId;
    /** 正式版本ID。 */
    private Long stableVersionId;
    /** 灰度版本ID。 */
    private Long canaryVersionId;
    /** 灰度规则 JSON。 */
    private String canaryRule;
    /** 发布代次。 */
    private Long releaseRevision;
    /** 发布人。 */
    private String releasedBy;
    /** 发布时间。 */
    private LocalDateTime releasedAt;
    /** 回滚来源发布记录ID。 */
    private Long rollbackFromReleaseId;
    /** 发布说明。 */
    private String remark;
}
