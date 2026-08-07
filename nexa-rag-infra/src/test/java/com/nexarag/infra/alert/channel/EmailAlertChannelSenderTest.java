package com.nexarag.infra.alert.channel;

import com.nexarag.common.exception.ServiceException;
import com.nexarag.infra.alert.model.AlertChannel;
import com.nexarag.infra.alert.model.AlertMessage;
import com.nexarag.infra.alert.model.AlertSeverity;
import com.nexarag.infra.config.AlertProperties;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.LocalDateTime;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 邮件告警渠道测试，验证未启用渠道不会被静默忽略。
 */
class EmailAlertChannelSenderTest {

    @Test
    void shouldRejectSendingWhenEmailChannelIsDisabled() {
        AlertProperties properties = new AlertProperties();
        properties.getEmail().setEnabled(false);
        EmailAlertChannelSender sender = new EmailAlertChannelSender(mock(JavaMailSender.class), properties);

        assertThatThrownBy(() -> sender.send(message()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("邮件告警渠道未启用");
    }

    @Test
    void shouldSendHtmlMimeMessage() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        AlertProperties properties = enabledEmailProperties();
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        EmailAlertChannelSender sender = new EmailAlertChannelSender(mailSender, properties);

        sender.send(message());

        verify(mailSender).send(mimeMessage);
        mimeMessage.saveChanges();
        assertThat(mimeMessage.getSubject()).contains("任务最终失败");
        assertThat(mimeMessage.getContentType()).contains("text/html");
    }

    private AlertProperties enabledEmailProperties() {
        AlertProperties properties = new AlertProperties();
        properties.setEnabled(true);
        properties.getEmail().setEnabled(true);
        properties.getEmail().setFrom("sender@example.com");
        properties.getEmail().setRecipients("recipient@example.com");
        return properties;
    }

    private AlertMessage message() {
        return new AlertMessage(11L, 7L, 3L, "operation-1", "CLEAN_DOCUMENT_INDEX",
                AlertSeverity.ERROR, AlertChannel.EMAIL, "索引清理失败", 5,
                LocalDateTime.of(2026, 8, 7, 18, 0));
    }
}
