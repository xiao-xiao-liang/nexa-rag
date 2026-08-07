package com.nexarag.infra.alert.channel;

import com.nexarag.infra.alert.model.AlertMessage;
import com.nexarag.infra.alert.model.AlertSeverity;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 飞书告警卡片模板，负责将脱敏告警消息渲染为飞书 interactive 卡片结构。
 */
public final class FeishuAlertCardTemplate {

    private static final DateTimeFormatter FAILURE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 渲染飞书卡片。
     *
     * @param message 已脱敏的告警消息
     * @return 飞书卡片结构
     */
    public Map<String, Object> render(AlertMessage message) {
        // 1. 校验消息并构造基础卡片结构
        Objects.requireNonNull(message, "告警消息不能为空");
        Map<String, Object> header = Map.of(
                "title", Map.of("tag", "plain_text", "content", "NexaRAG 任务失败告警"),
                "template", headerTemplate(message.severity()),
                "padding", "12px 12px 12px 12px");

        // 2. 组装任务摘要与失败原因，动态字段转义后写入 Markdown 区块
        List<Map<String, Object>> elements = List.of(
                Map.of("tag", "markdown", "content", formatTaskSummary(message), "text_align", "left",
                        "text_size", "normal_v2", "margin", "0px 0px 12px 0px"),
                Map.of("tag", "markdown", "content", "**失败原因**\n" + escapeMarkdown(message.failureReason()),
                        "text_align", "left", "text_size", "normal_v2", "margin", "0px 0px 0px 0px"));
        Map<String, Object> body = Map.of("direction", "vertical", "padding", "16px 16px 16px 16px",
                "elements", elements);
        return Map.of("schema", "2.0", "config", Map.of("update_multi", true), "header", header, "body", body);
    }

    private String formatTaskSummary(AlertMessage message) {
        return "**严重级别**：" + severityText(message.severity()) + "\n"
                + "**任务类型**：" + escapeMarkdown(message.taskType()) + "\n"
                + "**文档 ID**：" + message.documentId() + "\n"
                + "**父任务 ID**：" + message.parentOutboxId() + "\n"
                + "**操作版本 ID**：" + escapeMarkdown(message.operationId()) + "\n"
                + "**消费重试次数**：" + message.consumeRetryCount() + "\n"
                + "**失败时间**：" + FAILURE_TIME_FORMATTER.format(message.failureTime());
    }

    private String headerTemplate(AlertSeverity severity) {
        return severity == AlertSeverity.ERROR ? "red" : "orange";
    }

    private String severityText(AlertSeverity severity) {
        return severity == AlertSeverity.ERROR ? "错误" : "警告";
    }

    private String escapeMarkdown(String value) {
        return value.replace("\\", "\\\\")
                .replace("*", "\\*")
                .replace("_", "\\_")
                .replace("`", "\\`")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("(", "\\(")
                .replace(")", "\\)");
    }
}
