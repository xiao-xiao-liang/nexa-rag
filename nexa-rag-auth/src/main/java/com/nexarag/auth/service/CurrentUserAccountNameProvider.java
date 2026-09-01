package com.nexarag.auth.service;

/**
 * 当前请求账号名提供者，为业务审计提供认证数据源中的实时账号名。
 */
public interface CurrentUserAccountNameProvider {

    /**
     * 获取当前已登录用户的账号名。
     *
     * @return 当前用户的 account_name
     */
    String getCurrentAccountName();
}
