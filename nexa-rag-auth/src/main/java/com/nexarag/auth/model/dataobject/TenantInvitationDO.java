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
 * 企业租户成员邀请数据对象，对应 tenant_invitation 表。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@TableName("tenant_invitation")
public class TenantInvitationDO {

    /** 邀请ID。 */
    @TableId(value = "invitation_id", type = IdType.INPUT)
    private Long invitationId;
    /** 目标租户ID。 */
    private String tenantId;
    /** 受邀用户ID。 */
    private Long invitedUserId;
    /** 邀请人用户ID。 */
    private Long inviterUserId;
    /** 邀请状态。 */
    private Integer invitationStatus;
    /** 邀请过期时间。 */
    private LocalDateTime expiresTime;
    /** 用户响应时间。 */
    private LocalDateTime respondedTime;
    /** 创建时间。 */
    private LocalDateTime createTime;
    /** 更新时间。 */
    private LocalDateTime updateTime;
}
