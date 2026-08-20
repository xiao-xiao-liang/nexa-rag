package com.nexarag.document.model.dataobject;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 知识库数据对象，对应 knowledge_base 表并保存租户内的文档容器。
 */
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("knowledge_base")
public class KnowledgeBaseDO {

    /** 知识库ID。 */
    @TableId("knowledge_base_id")
    private Long knowledgeBaseId;

    /** 租户ID。 */
    private String tenantId;

    /** 知识库名称。 */
    private String name;

    /** 有效名称规范键。 */
    private String activeNameKey;

    /** 知识库描述。 */
    private String description;

    /** 是否默认知识库：0否，1是。 */
    private Integer isDefault;

    /** 默认库租户唯一键。 */
    private String defaultTenantKey;

    /** 创建时间。 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 创建人。 */
    private String createBy;

    /** 更新人。 */
    private String updateBy;

    /** 删除标记：0未删除，1已删除。 */
    @TableLogic(value = "0", delval = "1")
    @TableField(fill = FieldFill.INSERT)
    private Integer delFlag;

    /** 删除时间。 */
    private LocalDateTime deleteTime;

    /** 乐观锁版本号。 */
    @Version
    private Integer version;
}
