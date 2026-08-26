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
 * 企业租户所有者双确认转交数据对象，对应 tenant_ownership_transfer 表。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@TableName("tenant_ownership_transfer")
public class TenantOwnershipTransferDO {

    /** 转交ID。 */
    @TableId(value = "transfer_id", type = IdType.INPUT)
    private Long transferId;
    /** 租户ID。 */
    private String tenantId;
    /** 当前所有者用户ID。 */
    private Long currentOwnerUserId;
    /** 待接收所有者用户ID。 */
    private Long targetUserId;
    /** 转交状态。 */
    private Integer transferStatus;
    /** 接受确认过期时间。 */
    private LocalDateTime expiresTime;
    /** 接受时间。 */
    private LocalDateTime acceptedTime;
    /** 创建时间。 */
    private LocalDateTime createTime;
    /** 更新时间。 */
    private LocalDateTime updateTime;
}
