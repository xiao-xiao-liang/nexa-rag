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
 * 认证用户数据对象，对应 auth_user 表并保存账号名、全局角色和默认租户。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@TableName("auth_user")
public class AuthUserDO {

    /** 稳定用户ID。 */
    @TableId(value = "user_id", type = IdType.INPUT)
    private Long userId;

    /** 用户可修改的展示账号名。 */
    private String accountName;

    /** 用于唯一匹配的账号名规范化键。 */
    private String accountNameKey;

    /** 全局角色ID。 */
    private Long roleId;

    /** 用户状态：0启用、1禁用。 */
    private Integer status;

    /** 登录后的默认当前租户ID。 */
    private String defaultTenantId;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
