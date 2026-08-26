package com.nexarag.auth.model.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 租户成员关系数据对象，对应 tenant_member 表。
 *
 * <p>该表采用租户ID和用户ID联合主键，不能使用 MyBatis-Plus 的单主键更新或删除方法。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@TableName("tenant_member")
public class TenantMemberDO {

    /** 租户ID，联合主键之一。 */
    private String tenantId;

    /** 用户ID，联合主键之一。 */
    private Long userId;

    /** 成员角色：0所有者、1普通成员。 */
    private Integer memberRole;

    /** 成员状态：0有效、1主动退出、2被移除。 */
    private Integer memberStatus;

    /** 首次加入时间。 */
    private LocalDateTime joinedTime;

    /** 最近一次状态变更时间。 */
    private LocalDateTime statusChangedTime;
}
