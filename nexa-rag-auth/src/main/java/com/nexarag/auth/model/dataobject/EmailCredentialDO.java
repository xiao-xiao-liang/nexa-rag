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
 * 已验证邮箱凭据数据对象，对应 auth_email_credential 表。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@TableName("auth_email_credential")
public class EmailCredentialDO {

    /** 稳定用户ID，也是表主键。 */
    @TableId(value = "user_id", type = IdType.INPUT)
    private Long userId;

    /** 用户展示邮箱地址。 */
    private String email;

    /** 用于唯一匹配的规范化邮箱键。 */
    private String emailKey;

    /** 邮箱验证通过时间。 */
    private LocalDateTime verifiedTime;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
