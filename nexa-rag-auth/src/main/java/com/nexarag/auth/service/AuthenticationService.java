package com.nexarag.auth.service;

import com.nexarag.auth.model.dto.AccountPasswordLoginDTO;
import com.nexarag.auth.model.dto.EmailCodeLoginDTO;
import com.nexarag.auth.model.dto.EmailCodeSendDTO;
import com.nexarag.auth.model.dto.EmailPasswordLoginDTO;
import com.nexarag.auth.model.vo.EmailChallengeVO;
import com.nexarag.auth.model.vo.LoginSessionVO;

/**
 * 本地认证服务，负责校验凭据并建立 Sa-Token 登录态。
 */
public interface AuthenticationService {

    /**
     * 使用账号名和密码建立登录态。
     *
     * @param loginDTO 账号密码登录请求
     * @return 已建立的登录会话结果
     */
    LoginSessionVO loginByAccountPassword(AccountPasswordLoginDTO loginDTO);

    /**
     * 使用当前绑定邮箱和密码建立登录态。
     *
     * @param loginDTO 邮箱密码登录请求
     * @return 已建立的登录会话结果
     */
    LoginSessionVO loginByEmailPassword(EmailPasswordLoginDTO loginDTO);

    /**
     * 根据允许的匿名用途发送邮箱验证码。
     *
     * @param sendDTO 邮箱验证码发送请求
     * @return 新建挑战展示对象
     */
    EmailChallengeVO sendAnonymousEmailCode(EmailCodeSendDTO sendDTO);

    /**
     * 使用当前绑定邮箱和已验证验证码建立登录态。
     *
     * @param loginDTO 邮箱验证码登录请求
     * @return 已建立的登录会话结果
     */
    LoginSessionVO loginByEmailCode(EmailCodeLoginDTO loginDTO);
}
