package com.nexarag.document;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文档版本管理数据库结构契约测试。
 */
class DocumentVersionSchemaContractTest {

    private static final String MIGRATION_PATH =
            "nexa-rag-boot/src/main/resources/db/migration/V28__add_document_version_management.sql";
    private static final String OUTBOX_BACKFILL_MIGRATION_PATH =
            "nexa-rag-boot/src/main/resources/db/migration/V29__backfill_document_version_outbox.sql";
    private static final String OUTBOX_COMPLETION_BACKFILL_MIGRATION_PATH =
            "nexa-rag-boot/src/main/resources/db/migration/V30__complete_index_ready_document_version_outbox.sql";
    private static final String INDEX_READY_FAILURE_CONTEXT_MIGRATION_PATH =
            "nexa-rag-boot/src/main/resources/db/migration/V31__clear_index_ready_document_version_failure_context.sql";
    private static final String DOCUMENT_LEGACY_COLUMN_DROP_MIGRATION_PATH =
            "nexa-rag-boot/src/main/resources/db/migration/V32__drop_document_legacy_content_lifecycle_columns.sql";
    private static final String LEGACY_INDEXED_STATUS_NORMALIZATION_MIGRATION_PATH =
            "nexa-rag-boot/src/main/resources/db/migration/V33__normalize_legacy_indexed_document_version_status.sql";
    private static final String SCHEMA_PATH =
            "nexa-rag-boot/src/main/resources/db/schema/nexa_rag_schema.sql";
    private static final String DOCUMENT_VERSION_MAPPER_PATH =
            "nexa-rag-document/src/main/java/com/nexarag/document/mapper/DocumentVersionMapper.java";

    @Test
    void schemaAndMigrationShouldContainDocumentVersionManagementStructure() throws IOException {
        String migrationSql = readRepositoryFile(MIGRATION_PATH);
        String schemaSql = readRepositoryFile(SCHEMA_PATH);

        assertDocumentVersionManagementStructure(migrationSql);
        assertDocumentVersionManagementStructure(schemaSql);
    }

    @Test
    void outboxBackfillMigrationShouldOnlyFillMissingVersionReferences() throws IOException {
        Path migrationPath = locateRepositoryFile(OUTBOX_BACKFILL_MIGRATION_PATH);
        assertThat(Files.isRegularFile(migrationPath)).isTrue();
        String migrationSql = Files.readString(migrationPath);

        assertThat(migrationSql)
                .contains("UPDATE document_task_outbox dto")
                .contains("JOIN document_version dv")
                .contains("dv.revision_no = 1")
                .contains("SET dto.document_version_id = dv.document_version_id")
                .contains("WHERE dto.document_version_id IS NULL");
    }

    @Test
    void outboxCompletionBackfillMigrationShouldCompleteOnlyIndexReadyCurrentProcesses() throws IOException {
        Path migrationPath = locateRepositoryFile(OUTBOX_COMPLETION_BACKFILL_MIGRATION_PATH);
        assertThat(Files.isRegularFile(migrationPath)).isTrue();
        String migrationSql = Files.readString(migrationPath);

        assertThat(migrationSql)
                .contains("UPDATE document_task_outbox dto")
                .contains("JOIN document_version dv")
                .contains("dto.document_version_id = dv.document_version_id")
                .contains("dto.operation_id = dv.process_id")
                .contains("dv.status = 'INDEX_READY'")
                .contains("dto.task_status IN ('PENDING', 'PROCESSING')");
    }

    @Test
    void markIndexReadyShouldClearRecoveredFailureContext() throws IOException {
        String mapperSource = readRepositoryFile(DOCUMENT_VERSION_MAPPER_PATH);

        assertThat(mapperSource)
                .contains("failure_stage = NULL, failure_reason = NULL, failure_detail = NULL");
    }

    @Test
    void indexReadyFailureContextMigrationShouldClearOnlyRecoveredVersions() throws IOException {
        Path migrationPath = locateRepositoryFile(INDEX_READY_FAILURE_CONTEXT_MIGRATION_PATH);
        assertThat(Files.isRegularFile(migrationPath)).isTrue();
        String migrationSql = Files.readString(migrationPath);

        assertThat(migrationSql)
                .contains("UPDATE document_version")
                .contains("failure_stage = NULL")
                .contains("failure_reason = NULL")
                .contains("failure_detail = NULL")
                .contains("WHERE status = 'INDEX_READY'");
    }

    @Test
    void documentLegacyColumnDropMigrationShouldKeepOnlyStableDocumentFields() throws IOException {
        Path migrationPath = locateRepositoryFile(DOCUMENT_LEGACY_COLUMN_DROP_MIGRATION_PATH);
        assertThat(Files.isRegularFile(migrationPath)).isTrue();
        String migrationSql = Files.readString(migrationPath);

        assertThat(migrationSql)
                .contains("DROP COLUMN original_file_name")
                .contains("DROP COLUMN parsed_metadata_json")
                .contains("DROP COLUMN process_id")
                .contains("DROP COLUMN cleanup_failure_reason")
                .contains("ADD KEY idx_document_knowledge_base_del_flag (knowledge_base_id, del_flag)");
    }

    @Test
    void legacyIndexedVersionStatusMigrationShouldNormalizeToIndexReady() throws IOException {
        Path migrationPath = locateRepositoryFile(LEGACY_INDEXED_STATUS_NORMALIZATION_MIGRATION_PATH);
        assertThat(Files.isRegularFile(migrationPath)).isTrue();
        String migrationSql = Files.readString(migrationPath);

        assertThat(migrationSql)
                .contains("UPDATE document_version")
                .contains("SET status = 'INDEX_READY'")
                .contains("index_ready_time = COALESCE(index_ready_time, process_end_time, update_time, create_time)")
                .contains("WHERE status = 'INDEXED'")
                .contains("UPDATE document_task_outbox dto")
                .contains("dto.task_status = 'SUCCEEDED'")
                .contains("dv.status = 'INDEX_READY'");
    }

    /**
     * 断言数据库脚本包含文档版本管理所需的表、字段和索引。
     *
     * @param sql 数据库脚本
     */
    private void assertDocumentVersionManagementStructure(String sql) {
        assertThat(sql)
                .contains("active_version_id BIGINT NULL COMMENT '当前生效文档版本ID'")
                .contains("building_version_id BIGINT NULL COMMENT '正在构建的文档版本ID'")
                .contains("activation_generation BIGINT NOT NULL DEFAULT 0 COMMENT '生效代次'")
                .contains("CREATE TABLE IF NOT EXISTS document_version (")
                .contains("document_version_id BIGINT NOT NULL COMMENT '文档版本ID'")
                .contains("document_id BIGINT NOT NULL COMMENT '文档ID'")
                .contains("revision_no BIGINT NOT NULL COMMENT '文档内版本号'")
                .contains("UNIQUE KEY uk_document_version_document_revision (document_id, revision_no)")
                .contains("KEY idx_document_version_document_status (document_id, status)")
                .contains("document_version_id BIGINT NULL COMMENT '所属文档版本ID'")
                .contains("KEY idx_document_chunk_version (document_id, document_version_id)")
                .contains("KEY idx_document_section_version (document_id, document_version_id)")
                .contains("CREATE TABLE IF NOT EXISTS document_version_operation_log (")
                .contains("operation_log_id BIGINT NOT NULL COMMENT '版本操作审计ID'")
                .contains("operation_type VARCHAR(32) NOT NULL COMMENT '操作类型'")
                .contains("document_version_id BIGINT NULL COMMENT '文档版本ID'")
                .contains("activation_generation BIGINT NULL COMMENT '生效代次'");
    }

    /**
     * 从当前模块或聚合工程目录向上定位并读取仓库文件。
     *
     * @param relativePath 相对仓库根目录的路径
     * @return 文件内容
     */
    private String readRepositoryFile(String relativePath) throws IOException {
        Path target = locateRepositoryFile(relativePath);
        if (Files.isRegularFile(target)) {
            return Files.readString(target);
        }
        throw new IOException("未找到数据库结构文件：" + relativePath);
    }

    /**
     * 从当前模块或聚合工程目录向上定位数据库结构文件。
     *
     * @param relativePath 相对仓库根目录的路径
     * @return 目标文件路径，不存在时返回工作目录对应路径
     */
    private Path locateRepositoryFile(String relativePath) {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            Path target = current.resolve(relativePath);
            if (Files.isRegularFile(target)) {
                return target;
            }
            current = current.getParent();
        }
        return Path.of(relativePath).toAbsolutePath().normalize();
    }
}
