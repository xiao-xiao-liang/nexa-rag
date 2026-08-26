package com.nexarag.auth.model.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 租户主数据对象，对应 tenant 表。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@TableName("tenant")
public class TenantDO {

    /** 租户ID。 */
    @TableId(value = "tenant_id", type = IdType.INPUT)
    private String tenantId;

    /** 租户名称。 */
    private String tenantName;

    /** 用于唯一匹配的租户名称规范化键。 */
    private String tenantNameKey;

    /** 租户类型：0内建默认、1企业。 */
    private Integer tenantType;

    /** 租户状态：0启用、1禁用。 */
    private Integer status;

    /** 创建租户的用户ID；内建租户为空。 */
    private Long creatorUserId;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
