package com.nexarag.auth.mail;

import com.nexarag.auth.enums.EmailVerificationPurpose;
import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * 基于应用 SMTP 配置的认证验证码邮件投递实现。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuthMailServiceImpl implements AuthMailService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${spring.mail.username:}")
    private String fromAddress;

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendVerificationCode(String email, EmailVerificationPurpose purpose, String verificationCode) {
        // 1. 确认 SMTP 发送器和发件地址可用
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null || fromAddress == null || fromAddress.isBlank()) {
            throw new ServiceException("认证邮件服务未配置");
        }

        // 2. 投递只包含用途和验证码的纯文本邮件，日志不记录验证码
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress.trim());
            message.setTo(email);
            message.setSubject("NexaRAG 邮箱验证码");
            message.setText("用途：" + purpose.name() + "\n验证码：" + verificationCode
                    + "\n验证码将在 5 分钟后失效，请勿向他人泄露。");
            mailSender.send(message);
            log.info("认证验证码邮件投递成功，purpose={}，email={}", purpose.name(), maskEmail(email));
        } catch (Exception exception) {
            log.warn("认证验证码邮件投递失败，purpose={}，email={}", purpose.name(), maskEmail(email), exception);
            throw new ServiceException("认证验证码邮件发送失败", exception, BaseErrorCode.SERVICE_ERROR);
        }
    }

    /**
     * 脱敏邮箱地址，避免认证日志泄露完整个人信息。
     *
     * @param email 原始邮箱地址
     * @return 脱敏后的邮箱地址
     */
    private String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "***";
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(atIndex);
    }
}
