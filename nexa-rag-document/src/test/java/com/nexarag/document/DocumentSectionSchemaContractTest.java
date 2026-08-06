package com.nexarag.document;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.nexarag.document.entity.DocumentSectionDO;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * 文档章节结构数据库契约测试。
 */
class DocumentSectionSchemaContractTest {

    @Test
    void schemaShouldContainDocumentSectionStructure() throws IOException {
        String migration = Files.readString(repositoryRoot()
                .resolve("nexa-rag-boot/src/main/resources/db/migration/V16__add_document_section_structure.sql"));
        String schema = Files.readString(repositoryRoot()
                .resolve("nexa-rag-boot/src/main/resources/db/schema/nexa_rag_schema.sql"));

        assertDocumentSectionDefinition(extractCreateTableDefinition(migration, "document_section"));
        assertDocumentSectionDefinition(extractCreateTableDefinition(schema, "document_section"));
        assertDocumentChunkMigrationDefinition(extractAlterTableDefinition(migration, "document_chunk"));
        assertDocumentChunkSchemaDefinition(extractCreateTableDefinition(schema, "document_chunk"));
        assertDocumentSectionEntityMapping();
    }

    /**
     * 断言文档章节表定义包含完整的结构字段和索引。
     *
     * @param definition 文档章节表定义
     */
    private void assertDocumentSectionDefinition(String definition) {
        assertColumn(definition, "section_id", "BIGINT NOT NULL");
        assertColumn(definition, "document_id", "BIGINT NOT NULL");
        assertColumn(definition, "parent_section_id", "BIGINT NULL");
        assertColumn(definition, "title", "VARCHAR(512) NOT NULL");
        assertColumn(definition, "heading_path_json", "TEXT NOT NULL");
        assertColumn(definition, "heading_level", "INT NOT NULL");
        assertColumn(definition, "start_line", "INT NOT NULL");
        assertColumn(definition, "end_line", "INT NOT NULL");
        assertColumn(definition, "create_time", "DATETIME NOT NULL");
        assertColumn(definition, "update_time", "DATETIME NOT NULL");
        assertColumn(definition, "del_flag", "TINYINT NOT NULL DEFAULT 0");
        assertThat(definition)
                .containsPattern("(?i)\\bKEY\\s+idx_document_section_document\\s*\\(\\s*document_id\\s*\\)")
                .containsPattern("(?i)\\bKEY\\s+idx_document_section_parent\\s*\\(\\s*document_id\\s*,\\s*parent_section_id\\s*\\)");
    }

    /**
     * 断言文档片段定义保留父片段关系并新增章节和索引内容字段。
     *
     * @param definition 文档片段表定义
     */
    private void assertDocumentChunkMigrationDefinition(String definition) {
        assertThat(definition).containsPattern("(?i)\\bsection_id\\s+BIGINT\\s+NULL\\b.*?\\bAFTER\\s+parent_chunk_id\\b");
        assertColumn(definition, "index_content", "MEDIUMTEXT NULL");
        assertDocumentChunkSectionIndex(definition);
    }

    /**
     * 断言全量安装 schema 中的文档片段定义保留父片段关系并新增章节和索引内容字段。
     *
     * @param definition 文档片段表定义
     */
    private void assertDocumentChunkSchemaDefinition(String definition) {
        assertColumn(definition, "parent_chunk_id", "VARCHAR(64) NULL");
        assertColumn(definition, "section_id", "BIGINT NULL");
        assertColumn(definition, "index_content", "MEDIUMTEXT NULL");
        assertDocumentChunkSectionIndex(definition);
    }

    /**
     * 断言章节数据对象使用自动填充器支持的逻辑删除字段。
     */
    private void assertDocumentSectionEntityMapping() {
        assertThat(DocumentSectionDO.class.getDeclaredFields())
                .extracting(field -> field.getName())
                .contains("delFlag")
                .doesNotContain("deleted");
        assertThat(DocumentSectionDO.class.getDeclaredFields())
                .filteredOn(field -> field.getName().equals("delFlag"))
                .singleElement()
                .extracting(field -> field.getAnnotation(TableLogic.class))
                .isNotNull();
    }

    /**
     * 断言文档片段表定义使用约定的文档章节索引名称及列顺序。
     *
     * @param definition 文档片段表定义
     */
    private void assertDocumentChunkSectionIndex(String definition) {
        assertThat(definition)
                .containsPattern("(?i)\\b(?:ADD\\s+)?KEY\\s+idx_document_chunk_section\\s*\\(\\s*document_id\\s*,\\s*section_id\\s*\\)");
    }

    /**
     * 断言表定义中包含指定列及其类型约束。
     *
     * @param definition 表定义
     * @param columnName 列名
     * @param columnDefinition 列类型及约束
     */
    private void assertColumn(String definition, String columnName, String columnDefinition) {
        String normalizedColumnDefinition = columnDefinition.replace("(", "\\(").replace(")", "\\)")
                .replace(" ", "\\s+");
        assertThat(definition).containsPattern("(?i)\\b" + columnName + "\\s+" + normalizedColumnDefinition + "\\b");
    }

    /**
     * 提取指定建表语句，避免字段在其他表中出现时造成误判。
     *
     * @param sql 数据库建表脚本
     * @param tableName 表名
     * @return 指定表的建表语句
     */
    private String extractCreateTableDefinition(String sql, String tableName) {
        return extractDefinition(sql, "(?is)CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?"
                + Pattern.quote(tableName) + "\\s*\\(.*?\\)\\s*COMMENT\\s*=\\s*'[^']*';");
    }

    /**
     * 提取指定改表语句，避免字段在其他变更中出现时造成误判。
     *
     * @param sql 数据库建表脚本
     * @param tableName 表名
     * @return 指定表的改表语句
     */
    private String extractAlterTableDefinition(String sql, String tableName) {
        return extractDefinition(sql, "(?is)ALTER\\s+TABLE\\s+" + Pattern.quote(tableName) + "\\s+.*?;");
    }

    /**
     * 按正则表达式提取唯一的数据库定义片段。
     *
     * @param sql 数据库建表脚本
     * @param pattern 定义片段匹配表达式
     * @return 匹配到的定义片段
     */
    private String extractDefinition(String sql, String pattern) {
        Matcher matcher = Pattern.compile(pattern).matcher(sql);
        if (matcher.find()) {
            return matcher.group();
        }
        fail("未找到数据库定义片段，pattern=" + pattern);
        return "";
    }

    /**
     * 定位仓库根目录，兼容从聚合工程或子模块运行测试。
     *
     * @return 仓库根目录
     */
    private Path repositoryRoot() {
        Path currentPath = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (currentPath != null) {
            if (Files.isDirectory(currentPath.resolve("nexa-rag-boot"))) {
                return currentPath;
            }
            currentPath = currentPath.getParent();
        }
        throw new IllegalStateException("未找到仓库根目录");
    }
}
