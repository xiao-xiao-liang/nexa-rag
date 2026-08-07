package com.nexarag.document.outbox;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文档任务 Outbox 数据库迁移契约测试。
 */
class DocumentTaskSchemaContractTest {

    @Test
    void migrationShouldKeepHistoricalTasksNotTracked() throws IOException {
        Path workspace = Path.of("").toAbsolutePath();
        Path bootModule = Files.exists(workspace.resolve("nexa-rag-boot"))
                ? workspace.resolve("nexa-rag-boot")
                : workspace.getParent().resolve("nexa-rag-boot");
        String migration = Files.readString(bootModule.resolve(Path.of("src", "main", "resources", "db",
                "migration", "V17__evolve_document_task_outbox.sql")));

        assertThat(migration).contains("RENAME TABLE document_pipeline_outbox TO document_task_outbox");
        assertThat(migration).contains("CHANGE COLUMN process_id operation_id VARCHAR(64) NULL");
        assertThat(migration).contains("ADD COLUMN task_status VARCHAR(32) NOT NULL DEFAULT 'NOT_TRACKED'");
        assertThat(migration).contains("ADD COLUMN parent_outbox_id BIGINT NULL");
    }
}
