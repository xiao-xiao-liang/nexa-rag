package com.nexarag.document.outbox;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文档流水线数据库结构契约测试。
 */
class DocumentPipelineSchemaContractTest {

    private static final String MIGRATION_PATH =
            "nexa-rag-boot/src/main/resources/db/migration/V11__add_document_pipeline_messaging.sql";
    private static final String SCHEMA_PATH =
            "nexa-rag-boot/src/main/resources/db/schema/nexa_rag_schema.sql";

    @Test
    void shouldContainDocumentPipelineColumnsAndOutboxSchema() throws IOException {
        // 1. 读取增量迁移与完整数据库结构文件
        String migrationSql = readRepositoryFile(MIGRATION_PATH);
        String schemaSql = readRepositoryFile(SCHEMA_PATH);

        // 2. 验证两个文件均包含文档消息字段和中文注释
        assertDocumentMessagingColumns(migrationSql);
        assertDocumentMessagingColumns(schemaSql);

        // 3. 验证两个文件均包含Outbox表及关键索引
        assertOutboxSchema(migrationSql);
        assertOutboxSchema(schemaSql);
    }

    private static String readRepositoryFile(String relativePath) throws IOException {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            Path target = current.resolve(relativePath);
            if (Files.isRegularFile(target)) {
                return Files.readString(target);
            }
            current = current.getParent();
        }
        throw new IOException("未找到数据库结构文件：" + relativePath);
    }

    private static void assertDocumentMessagingColumns(String sql) {
        assertTrue(sql.contains("process_id VARCHAR(64) NULL COMMENT '文档处理流水号'"));
        assertTrue(sql.contains("message_status VARCHAR(32) NULL COMMENT '文档流水线消息状态'"));
        assertTrue(sql.contains("consumed_times INT NOT NULL DEFAULT 0 COMMENT '消息消费次数'"));
        assertTrue(sql.contains("last_message_id VARCHAR(128) NULL COMMENT '最近消费消息ID'"));
    }

    private static void assertOutboxSchema(String sql) {
        assertTrue(sql.contains("document_pipeline_outbox"));
        assertTrue(sql.contains("message_body TEXT NOT NULL COMMENT '消息内容'"));
        assertTrue(sql.contains("publish_retry_count INT NOT NULL DEFAULT 0 COMMENT '发布重试次数'"));
        assertTrue(sql.contains("UNIQUE KEY uk_document_pipeline_outbox_message_key (message_key)"));
        assertTrue(sql.contains("KEY idx_document_pipeline_outbox_publish_retry (publish_status, next_retry_time)"));
        assertTrue(sql.contains("COMMENT='文档流水线消息Outbox表'"));
    }
}
