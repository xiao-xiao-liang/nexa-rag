package com.nexarag.auth.service;

import com.nexarag.auth.model.dto.RegisterAccountDTO;
import com.nexarag.auth.model.vo.LoginSessionVO;

/**
 * 无密码注册服务，负责创建用户、首个邮箱凭据和默认租户成员关系。
 */
public interface RegistrationService {

    /**
     * 使用已验证的首个邮箱注册账号并建立登录态。
     *
     * @param registerDTO 注册请求
     * @return 注册后建立的登录会话结果
     */
    LoginSessionVO register(RegisterAccountDTO registerDTO);
}
