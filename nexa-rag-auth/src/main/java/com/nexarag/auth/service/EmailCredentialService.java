package com.nexarag.auth.service;

import com.nexarag.auth.model.dto.EmailChangeDTO;
import com.nexarag.auth.model.dto.EmailCodeSendDTO;
import com.nexarag.auth.model.dto.EmailVerificationDTO;
import com.nexarag.auth.model.vo.EmailChallengeVO;

/**
 * 已登录账号的首个邮箱绑定与双邮箱换绑服务。
 */
public interface EmailCredentialService {

    /**
     * 向当前账号可验证的旧邮箱或待绑定新邮箱发送验证码。
     *
     * @param sendDTO 验证码发送请求，仅允许 CHANGE_EMAIL_OLD 或 CHANGE_EMAIL_NEW 用途
     * @return 挑战摘要
     */
    EmailChallengeVO sendEmailChangeCode(EmailCodeSendDTO sendDTO);

    /**
     * 为尚无邮箱凭据的当前账号绑定首个邮箱。
     *
     * @param verificationDTO 新邮箱验证码
     */
    void bindFirstEmail(EmailVerificationDTO verificationDTO);

    /**
     * 在旧、新邮箱验证码均成功后原子替换当前邮箱凭据。
     *
     * @param changeDTO 双邮箱验证信息
     */
    void changeEmail(EmailChangeDTO changeDTO);
}
