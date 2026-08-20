package com.nexarag.document;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * 知识库数据库结构契约测试。
 */
class KnowledgeBaseSchemaContractTest {

    private static final String MIGRATION_PATH =
            "nexa-rag-boot/src/main/resources/db/migration/V20__add_tenant_scoped_knowledge_base.sql";
    private static final String SCHEMA_PATH =
            "nexa-rag-boot/src/main/resources/db/schema/nexa_rag_schema.sql";

    @Test
    void schemaShouldContainTenantScopedKnowledgeBaseAndDocumentMembership() throws IOException {
        String migration = readRepositoryFile(MIGRATION_PATH);
        String schema = readRepositoryFile(SCHEMA_PATH);

        assertKnowledgeBaseDefinition(extractCreateTableDefinition(migration, "knowledge_base"));
        assertKnowledgeBaseDefinition(extractCreateTableDefinition(schema, "knowledge_base"));
        assertDocumentMembershipMigration(extractAlterTableDefinition(migration, "document"));
        assertDocumentMembershipSchema(extractCreateTableDefinition(schema, "document"));
        assertThat(migration).contains("INSERT INTO knowledge_base")
                .contains("WHERE NOT EXISTS");
    }

    private void assertKnowledgeBaseDefinition(String definition) {
        assertColumn(definition, "knowledge_base_id", "BIGINT NOT NULL");
        assertColumn(definition, "tenant_id", "VARCHAR(64) NOT NULL");
        assertColumn(definition, "name", "VARCHAR(128) NOT NULL");
        assertColumn(definition, "active_name_key", "VARCHAR(128) NULL");
        assertColumn(definition, "description", "VARCHAR(1024) NULL");
        assertColumn(definition, "is_default", "TINYINT NOT NULL DEFAULT 0");
        assertColumn(definition, "default_tenant_key", "VARCHAR(64) NULL");
        assertThat(definition)
                .contains("UNIQUE KEY uk_knowledge_base_tenant_active_name (tenant_id, active_name_key)")
                .contains("UNIQUE KEY uk_knowledge_base_default_tenant (default_tenant_key)")
                .contains("KEY idx_knowledge_base_tenant_update (tenant_id, del_flag, update_time)");
    }

    private void assertDocumentMembershipMigration(String definition) {
        assertThat(definition)
                .contains("knowledge_base_id BIGINT NOT NULL")
                .contains("KEY idx_document_knowledge_base_status (knowledge_base_id, del_flag, status)");
    }

    private void assertDocumentMembershipSchema(String definition) {
        assertColumn(definition, "knowledge_base_id", "BIGINT NOT NULL");
        assertThat(definition)
                .contains("KEY idx_document_knowledge_base_status (knowledge_base_id, del_flag, status)");
    }

    private void assertColumn(String definition, String columnName, String columnDefinition) {
        String pattern = columnDefinition.replace("(", "\\(").replace(")", "\\)").replace(" ", "\\s+");
        assertThat(definition).containsPattern("(?i)\\b" + columnName + "\\s+" + pattern + "\\b");
    }

    private String extractCreateTableDefinition(String sql, String tableName) {
        return extractDefinition(sql, "(?is)CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?"
                + Pattern.quote(tableName) + "\\s*\\(.*?\\)\\s*COMMENT\\s*=\\s*'[^']*';");
    }

    private String extractAlterTableDefinition(String sql, String tableName) {
        return extractDefinition(sql, "(?is)ALTER\\s+TABLE\\s+" + Pattern.quote(tableName) + "\\s+.*?;");
    }

    private String extractDefinition(String sql, String pattern) {
        Matcher matcher = Pattern.compile(pattern).matcher(sql);
        if (matcher.find()) {
            return matcher.group();
        }
        fail("未找到数据库定义片段，pattern=" + pattern);
        return "";
    }

    private String readRepositoryFile(String relativePath) throws IOException {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            Path target = current.resolve(relativePath);
            if (Files.isRegularFile(target)) {
                return Files.readString(target);
            }
            current = current.getParent();
        }
        throw new IOException("未找到数据库结构文件：" + relativePath);
    }
}
