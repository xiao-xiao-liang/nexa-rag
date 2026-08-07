package com.nexarag.infra.alert.channel;

import com.nexarag.common.error.BaseErrorCode;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.infra.alert.model.AlertChannel;
import com.nexarag.infra.alert.model.AlertMessage;
import com.nexarag.infra.config.AlertProperties;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 邮件告警发送器，向已配置的收件人投递脱敏 HTML 消息。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "nexa.alert.email", name = "enabled", havingValue = "true")
public class EmailAlertChannelSender implements AlertChannelSender {

    private final JavaMailSender mailSender;
    private final AlertProperties properties;
    private final EmailAlertHtmlTemplate template = new EmailAlertHtmlTemplate();

    @Override
    public AlertChannel channel() {
        return AlertChannel.EMAIL;
    }

    @Override
    public void send(AlertMessage message) {
        // 1. 校验渠道开关和收发件配置，避免生成无法追踪的空邮件
        AlertProperties.EmailProperties email = properties.getEmail();
        if (!properties.isEnabled() || !email.isEnabled()) {
            throw new ServiceException("邮件告警渠道未启用");
        }
        String[] recipients = parseRecipients(email.getRecipients());
        if (email.getFrom() == null || email.getFrom().isBlank()) {
            throw new ServiceException("告警邮件发件人未配置");
        }

        // 2. 发送不包含原始异常详情的 HTML 告警邮件
        try {
            MimeMessage mailMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mailMessage, false, StandardCharsets.UTF_8.name());
            helper.setFrom(email.getFrom().trim());
            helper.setTo(recipients);
            helper.setSubject(template.subject(message));
            helper.setText(template.render(message), true);
            mailSender.send(mailMessage);
        } catch (ServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ServiceException("发送告警邮件失败，outboxId=" + message.outboxId(),
                    exception, BaseErrorCode.SERVICE_ERROR);
        }
    }

    private String[] parseRecipients(String configuredRecipients) {
        if (configuredRecipients == null || configuredRecipients.isBlank()) {
            throw new ServiceException("告警邮件收件人未配置");
        }
        String[] recipients = Arrays.stream(configuredRecipients.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toArray(String[]::new);
        if (recipients.length == 0) {
            throw new ServiceException("告警邮件收件人未配置");
        }
        return recipients;
    }

}
