package com.nexarag.document.alert;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文档流水线日志告警测试，验证最终失败会输出可检索的结构化错误日志。
 */
class LoggingDocumentPipelineAlertServiceTest {

    @Test
    void shouldWriteStructuredErrorLog() {
        Logger logger = (Logger) LoggerFactory.getLogger(LoggingDocumentPipelineAlertService.class);
        ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            LoggingDocumentPipelineAlertService service = new LoggingDocumentPipelineAlertService();
            service.alert(new DocumentPipelineFailureEvent(
                    1L, "process-1", "INDEXING", "索引失败", "detail", 6,
                    "message-1", LocalDateTime.of(2026, 7, 11, 12, 0)));

            assertThat(appender.list).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getFormattedMessage())
                        .contains("documentId=1", "processId=process-1", "failureStage=INDEXING", "messageId=message-1");
            });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
