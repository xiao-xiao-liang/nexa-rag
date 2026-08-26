package com.nexarag.auth.service.impl;

import com.nexarag.auth.constants.SecurityNotificationConstants;
import com.nexarag.auth.mapper.EmailCredentialMapper;
import com.nexarag.auth.service.SecurityNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 基于既有 SMTP 配置异步发送脱敏安全通知。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityNotificationServiceImpl implements SecurityNotificationService {

    private final EmailCredentialMapper emailCredentialMapper;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String from;

    /**
     * 异步发送安全操作通知；邮件投递失败不影响已提交的认证业务。
     *
     * @param userId       用户ID
     * @param eventSummary 脱敏后的安全事件摘要
     */
    @Override
    @Async(SecurityNotificationConstants.EXECUTOR_NAME)
    public void notifyUser(Long userId, String eventSummary) {
        try {
            // 1. 查询接收邮箱并校验发件配置。
            var credential = emailCredentialMapper.selectById(userId);
            if (credential == null || from == null || from.isBlank()) {
                return;
            }

            // 2. 组装并投递安全提醒邮件。
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(credential.getEmail());
            message.setSubject("NexaRAG 账号安全通知");
            message.setText("账号安全操作：" + eventSummary + "\n如非本人操作，请及时检查账号安全。");
            mailSender.send(message);
        } catch (Exception exception) {
            log.warn("安全通知投递失败，userId={}", userId, exception);
        }
    }
}
