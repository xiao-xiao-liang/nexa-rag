package com.nexarag.infra.alert.channel;

import com.nexarag.infra.alert.model.AlertMessage;
import com.nexarag.infra.alert.model.AlertSeverity;
import org.springframework.web.util.HtmlUtils;

import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * 邮件告警 HTML 模板，负责将脱敏告警消息渲染为可读邮件正文。
 */
public final class EmailAlertHtmlTemplate {

    private static final String ERROR_COLOR = "#D92D20";
    private static final String WARNING_COLOR = "#F79009";
    private static final DateTimeFormatter FAILURE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 构造邮件主题。
     *
     * @param message 已脱敏的告警消息
     * @return 邮件主题
     */
    public String subject(AlertMessage message) {
        Objects.requireNonNull(message, "告警消息不能为空");
        return "[NexaRAG][" + severityText(message.severity()) + "] 任务最终失败";
    }

    /**
     * 渲染 HTML 邮件正文。
     *
     * @param message 已脱敏的告警消息
     * @return HTML 邮件正文
     */
    public String render(AlertMessage message) {
        // 1. 校验消息并准备展示字段，动态内容均在模板边界进行转义
        Objects.requireNonNull(message, "告警消息不能为空");
        String accentColor = accentColor(message.severity());
        String severity = escape(severityText(message.severity()));
        String taskType = escape(message.taskType());
        String documentId = escape(String.valueOf(message.documentId()));
        String parentOutboxId = escape(String.valueOf(message.parentOutboxId()));
        String operationId = escape(message.operationId());
        String retryCount = escape(String.valueOf(message.consumeRetryCount()));
        String failureTime = escape(FAILURE_TIME_FORMATTER.format(message.failureTime()));
        String failureReason = escape(message.failureReason());

        // 2. 组装兼容常见邮箱客户端的内联样式 HTML
        return """
                <!doctype html>
                <html lang="zh-CN">
                <body style="margin:0;padding:0;background:#f5f7fa;font-family:Arial,'Microsoft YaHei',sans-serif;color:#1d2939;">
                <div style="max-width:680px;margin:24px auto;background:#ffffff;border:1px solid #eaecf0;border-radius:12px;overflow:hidden;">
                <div style="padding:24px 28px;background:%s;color:#ffffff;">
                <div style="font-size:12px;letter-spacing:1.2px;text-transform:uppercase;opacity:0.9;">NexaRAG Alert</div>
                <div style="margin-top:6px;font-size:22px;font-weight:700;">NexaRAG 任务失败告警</div>
                </div>
                <div style="padding:28px;">
                <div style="margin-bottom:20px;padding:14px 16px;background:#fff7ed;border-left:4px solid %s;border-radius:4px;">
                <strong style="color:%s;">严重级别：%s</strong><br>
                <span style="display:inline-block;margin-top:6px;color:#475467;font-size:14px;">请及时检查任务状态并按需发起重试。</span>
                </div>
                <table role="presentation" style="width:100%%;border-collapse:collapse;font-size:14px;">
                <tr><td style="width:150px;padding:12px 0;border-bottom:1px solid #eaecf0;color:#667085;">任务类型</td><td style="padding:12px 0;border-bottom:1px solid #eaecf0;font-weight:600;">%s</td></tr>
                <tr><td style="padding:12px 0;border-bottom:1px solid #eaecf0;color:#667085;">文档 ID</td><td style="padding:12px 0;border-bottom:1px solid #eaecf0;">%s</td></tr>
                <tr><td style="padding:12px 0;border-bottom:1px solid #eaecf0;color:#667085;">父任务 ID</td><td style="padding:12px 0;border-bottom:1px solid #eaecf0;">%s</td></tr>
                <tr><td style="padding:12px 0;border-bottom:1px solid #eaecf0;color:#667085;">操作版本 ID</td><td style="padding:12px 0;border-bottom:1px solid #eaecf0;">%s</td></tr>
                <tr><td style="padding:12px 0;border-bottom:1px solid #eaecf0;color:#667085;">消费重试次数</td><td style="padding:12px 0;border-bottom:1px solid #eaecf0;">%s</td></tr>
                <tr><td style="padding:12px 0;border-bottom:1px solid #eaecf0;color:#667085;">失败时间</td><td style="padding:12px 0;border-bottom:1px solid #eaecf0;">%s</td></tr>
                </table>
                <div style="margin-top:22px;">
                <div style="margin-bottom:8px;color:#667085;font-size:14px;">失败原因</div>
                <div style="padding:14px 16px;background:#f9fafb;border:1px solid #eaecf0;border-radius:6px;line-height:1.6;word-break:break-word;">%s</div>
                </div>
                </div>
                <div style="padding:16px 28px;background:#f9fafb;color:#98a2b3;font-size:12px;">此邮件由 NexaRAG 自动发送，请勿直接回复。</div>
                </div>
                </body>
                </html>
                """.formatted(accentColor, accentColor, accentColor, severity, taskType, documentId,
                parentOutboxId, operationId, retryCount, failureTime, failureReason);
    }

    private String accentColor(AlertSeverity severity) {
        return severity == AlertSeverity.ERROR ? ERROR_COLOR : WARNING_COLOR;
    }

    private String severityText(AlertSeverity severity) {
        return severity == AlertSeverity.ERROR ? "错误" : "警告";
    }

    private String escape(String value) {
        return HtmlUtils.htmlEscape(value);
    }
}
