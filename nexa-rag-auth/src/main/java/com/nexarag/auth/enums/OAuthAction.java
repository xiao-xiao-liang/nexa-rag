package com.nexarag.auth.enums;

/**
 * OAuth state 所代表的本地业务动作。
 */
public enum OAuthAction {

    /** 第三方身份登录；未绑定身份可据此注册账号。 */
    LOGIN,

    /** 已登录用户绑定新的第三方身份。 */
    BIND
}
