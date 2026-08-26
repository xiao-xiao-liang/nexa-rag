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
 * 全局权限数据对象，对应 auth_permission 表。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@TableName("auth_permission")
public class AuthPermissionDO {

    /** 权限ID。 */
    @TableId(value = "permission_id", type = IdType.INPUT)
    private Long permissionId;

    /** 权限编码。 */
    private String permissionCode;

    /** 权限名称。 */
    private String permissionName;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
