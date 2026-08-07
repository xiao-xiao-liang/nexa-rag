package com.nexarag.infra.alert.channel;

import com.nexarag.infra.alert.model.AlertChannel;
import com.nexarag.infra.alert.model.AlertMessage;
import com.nexarag.infra.alert.model.AlertSeverity;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 邮件 HTML 模板测试，验证告警信息展示与动态字段转义。
 */
class EmailAlertHtmlTemplateTest {

    @Test
    void shouldRenderEscapedHtmlForErrorAlert() {
        AlertMessage message = new AlertMessage(11L, 7L, 3L, "operation-1", "CLEAN_DOCUMENT_INDEX",
                AlertSeverity.ERROR, AlertChannel.EMAIL, "索引<失败>&重试", 5,
                LocalDateTime.of(2026, 8, 7, 18, 0));

        String html = new EmailAlertHtmlTemplate().render(message);

        assertThat(html).contains("NexaRAG 任务失败告警")
                .contains("#D92D20")
                .contains("索引&lt;失败&gt;&amp;重试")
                .contains("文档 ID")
                .contains("2026-08-07 18:00:00");
    }
}
