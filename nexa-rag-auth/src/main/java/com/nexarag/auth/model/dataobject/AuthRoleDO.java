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
 * 全局角色数据对象，对应 auth_role 表。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@TableName("auth_role")
public class AuthRoleDO {

    /** 角色ID。 */
    @TableId(value = "role_id", type = IdType.INPUT)
    private Long roleId;

    /** 角色编码。 */
    private String roleCode;

    /** 角色名称。 */
    private String roleName;

    /** 是否为系统预置角色：0否、1是。 */
    private Integer systemPredefined;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
