package com.nexarag.auth.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 认证用户状态，与 auth_user.status 字段保持一致。
 */
@Getter
@RequiredArgsConstructor
public enum UserStatus {

    /** 用户可正常登录和访问系统。 */
    ACTIVE(0),

    /** 用户被禁用，既有登录态应失效。 */
    DISABLED(1);

    /** 数据库存储值。 */
    private final int code;
}
