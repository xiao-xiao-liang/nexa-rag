package com.nexarag.auth.service;

import com.nexarag.auth.model.dto.EmailCodeSendDTO;
import com.nexarag.auth.model.dto.PasswordSetDTO;
import com.nexarag.auth.model.vo.EmailChallengeVO;

/**
 * 已登录用户的本地密码设置与修改服务。
 */
public interface PasswordManagementService {

    /**
     * 向当前用户的绑定邮箱发送设置密码验证码。
     *
     * @param sendDTO 邮箱验证码发送请求
     * @return 新建挑战摘要
     */
    EmailChallengeVO sendPasswordSetCode(EmailCodeSendDTO sendDTO);

    /**
     * 使用当前绑定邮箱验证码设置或修改密码，不撤销已有登录态。
     *
     * @param setDTO 设置密码请求
     */
    void setPassword(PasswordSetDTO setDTO);
}
