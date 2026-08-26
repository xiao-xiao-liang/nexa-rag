package com.nexarag.auth.model.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 全局角色与权限关联数据对象，对应 auth_role_permission 表。
 *
 * <p>该表采用联合主键，不能使用 MyBatis-Plus 的单主键更新或删除方法。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@TableName("auth_role_permission")
public class AuthRolePermissionDO {

    /** 角色ID，联合主键之一。 */
    private Long roleId;

    /** 权限ID，联合主键之一。 */
    private Long permissionId;

    /** 创建时间。 */
    private LocalDateTime createTime;
}
