package com.nexarag.infra.alert.channel;

import com.nexarag.infra.alert.model.AlertChannel;
import com.nexarag.infra.alert.model.AlertMessage;
import com.nexarag.infra.alert.model.AlertSeverity;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 飞书告警卡片模板测试，验证卡片结构、严重级别配色与实际换行。
 */
class FeishuAlertCardTemplateTest {

    @Test
    void shouldRenderRedCardForErrorAlert() {
        Map<String, Object> card = new FeishuAlertCardTemplate().render(message(AlertSeverity.ERROR));

        assertThat(card).containsEntry("schema", "2.0");
        assertThat(header(card)).containsEntry("template", "red");
        assertThat(markdownContent(card)).contains("**严重级别**：错误")
                .contains("**任务类型**：CLEAN\\_DOCUMENT\\_INDEX")
                .contains("\n")
                .doesNotContain("\\n");
    }

    @Test
    void shouldRenderOrangeCardForWarningAlert() {
        Map<String, Object> card = new FeishuAlertCardTemplate().render(message(AlertSeverity.WARNING));

        assertThat(header(card)).containsEntry("template", "orange");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> header(Map<String, Object> card) {
        return (Map<String, Object>) card.get("header");
    }

    @SuppressWarnings("unchecked")
    private String markdownContent(Map<String, Object> card) {
        Map<String, Object> body = (Map<String, Object>) card.get("body");
        List<Map<String, Object>> elements = (List<Map<String, Object>>) body.get("elements");
        return (String) elements.getFirst().get("content");
    }

    private AlertMessage message(AlertSeverity severity) {
        return new AlertMessage(11L, 7L, 3L, "operation-1", "CLEAN_DOCUMENT_INDEX", severity,
                AlertChannel.FEISHU, "索引清理失败", 5, LocalDateTime.of(2026, 8, 7, 18, 0));
    }
}
