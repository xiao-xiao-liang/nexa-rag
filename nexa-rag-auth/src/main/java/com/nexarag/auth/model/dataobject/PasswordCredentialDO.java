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
 * 本地密码凭据数据对象，对应 auth_password_credential 表。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@TableName("auth_password_credential")
public class PasswordCredentialDO {

    /** 稳定用户ID，也是表主键。 */
    @TableId(value = "user_id", type = IdType.INPUT)
    private Long userId;

    /** Argon2id PHC 格式密码哈希，禁止保存明文密码。 */
    private String passwordHash;

    /** 当前连续密码验证失败次数。 */
    private Integer failedAttempts;

    /** 密码验证冻结截止时间；为空表示未冻结。 */
    private LocalDateTime passwordLockedUntil;

    /** 最近一次设置或重置密码的时间。 */
    private LocalDateTime passwordChangedTime;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
