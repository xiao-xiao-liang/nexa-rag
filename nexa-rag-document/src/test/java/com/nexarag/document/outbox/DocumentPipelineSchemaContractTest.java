package com.nexarag.document.outbox;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文档流水线数据库结构契约测试。
 */
class DocumentPipelineSchemaContractTest {

    private static final String MIGRATION_PATH =
            "nexa-rag-boot/src/main/resources/db/migration/V17__evolve_document_task_outbox.sql";
    private static final String SCHEMA_PATH =
            "nexa-rag-boot/src/main/resources/db/schema/nexa_rag_schema.sql";

    @Test
    void shouldContainDocumentPipelineColumnsAndOutboxSchema() throws IOException {
        // 1. 读取增量迁移与完整数据库结构文件
        String migrationSql = readRepositoryFile(MIGRATION_PATH);
        String schemaSql = readRepositoryFile(SCHEMA_PATH);

        // 2. 验证完整结构包含当前文档消息字段
        assertDocumentMessagingColumns(schemaSql);

        // 3. 验证迁移与完整结构均反映当前任务型 Outbox 模型
        assertTaskOutboxMigration(migrationSql);
        assertOutboxSchema(schemaSql);
    }

    @Test
    void shouldNormalizeSqlLineSeparators() {
        assertEquals("第一行\n第二行\n第三行", normalizeLineSeparators("第一行\r\n第二行\r第三行"));
    }

    private static String readRepositoryFile(String relativePath) throws IOException {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            Path target = current.resolve(relativePath);
            if (Files.isRegularFile(target)) {
                return normalizeLineSeparators(Files.readString(target));
            }
            current = current.getParent();
        }
        throw new IOException("未找到数据库结构文件：" + relativePath);
    }

    private static String normalizeLineSeparators(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static void assertDocumentMessagingColumns(String sql) {
        assertTrue(sql.contains("process_id VARCHAR(64) NULL COMMENT '当前文档处理轮次ID'"));
        assertTrue(sql.contains("message_status VARCHAR(32) NULL COMMENT '当前处理轮次消息状态'"));
        assertTrue(sql.contains("consumed_times INT NOT NULL DEFAULT 0 COMMENT '当前处理轮次已消费次数'"));
        assertTrue(sql.contains("last_message_id VARCHAR(128) NULL COMMENT '最近一次消费的RocketMQ消息ID'"));
    }

    private static void assertOutboxSchema(String sql) {
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS document_task_outbox ("));
        assertTrue(sql.contains("outbox_id BIGINT NOT NULL COMMENT 'Outbox记录ID'"));
        assertTrue(sql.contains("document_id BIGINT NOT NULL COMMENT '文档ID'"));
        assertTrue(sql.contains("operation_id VARCHAR(64) NULL COMMENT '任务操作版本ID'"));
        assertTrue(sql.contains("task_type VARCHAR(64) NOT NULL COMMENT '任务类型'"));
        assertTrue(sql.contains("task_status VARCHAR(32) NOT NULL COMMENT '任务最终状态'"));
        assertTrue(sql.contains("message_key VARCHAR(128) NOT NULL COMMENT '消息唯一键'"));
        assertTrue(sql.contains("topic VARCHAR(128) NOT NULL COMMENT '消息主题'"));
        assertTrue(sql.contains("message_body TEXT NOT NULL COMMENT '消息内容'"));
        assertTrue(sql.contains("publish_status VARCHAR(32) NOT NULL COMMENT '发布状态'"));
        assertTrue(sql.contains("publish_retry_count INT NOT NULL DEFAULT 0 COMMENT '发布重试次数'"));
        assertTrue(sql.contains("next_retry_time DATETIME NULL COMMENT '下次重试时间'"));
        assertTrue(sql.contains("lock_owner VARCHAR(128) NULL COMMENT '锁持有者'"));
        assertTrue(sql.contains("lock_time DATETIME NULL COMMENT '加锁时间'"));
        assertTrue(sql.contains("published_time DATETIME NULL COMMENT '发布时间'"));
        assertTrue(sql.contains("publish_failure_reason VARCHAR(1024) NULL COMMENT '消息发布失败原因'"));
        assertTrue(sql.contains("create_time DATETIME NOT NULL COMMENT '创建时间'"));
        assertTrue(sql.contains("update_time DATETIME NOT NULL COMMENT '更新时间'"));
        assertTrue(sql.contains("-- 主键：Outbox记录ID\n    PRIMARY KEY (outbox_id)"));
        assertTrue(sql.contains("-- 唯一索引：保证消息唯一键不重复\n"
                + "    UNIQUE KEY uk_document_task_outbox_message_key (message_key)"));
        assertTrue(sql.contains("-- 任务索引：按发布状态和下次重试时间扫描待发布任务\n"
                + "    KEY idx_document_task_outbox_publish_task (publish_status, next_retry_time)"));
        assertTrue(sql.contains("UNIQUE KEY uk_document_task_outbox_message_key (message_key)"));
        assertTrue(sql.contains("COMMENT='文档任务消息Outbox表'"));
    }

    private static void assertTaskOutboxMigration(String sql) {
        assertTrue(sql.contains("RENAME TABLE document_pipeline_outbox TO document_task_outbox;"));
        assertTrue(sql.contains("CHANGE COLUMN process_id operation_id VARCHAR(64) NULL COMMENT '任务操作版本ID'"));
        assertTrue(sql.contains("ADD COLUMN task_type VARCHAR(64) NOT NULL DEFAULT 'PROCESS_DOCUMENT'"));
        assertTrue(sql.contains("ADD KEY idx_document_task_outbox_status (task_type, task_status, update_time)"));
    }
}
