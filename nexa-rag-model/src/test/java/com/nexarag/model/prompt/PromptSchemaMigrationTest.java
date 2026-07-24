package com.nexarag.model.prompt;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prompt 管理数据库脚本测试，确保增量迁移与完整 Schema 同步维护。
 */
class PromptSchemaMigrationTest {

    /**
     * 验证 Prompt 管理表和会话 Prompt 种子数据已写入两个数据库脚本。
     *
     * @throws IOException 读取脚本失败时抛出
     */
    @Test
    void shouldContainPromptManagementTablesAndChatSeedsInMigrationAndSchema() throws IOException {
        String migrationSql = readDatabaseScript("migration/V14__add_prompt_management.sql");
        String schemaSql = readDatabaseScript("schema/nexa_rag_schema.sql");

        // 1. 验证增量迁移包含三个核心表
        assertThat(migrationSql).contains("CREATE TABLE prompt_definition");
        assertThat(migrationSql).contains("CREATE TABLE prompt_version");
        assertThat(migrationSql).contains("CREATE TABLE prompt_release");

        // 2. 验证增量迁移包含发布代次和全部会话 Prompt 种子定义
        assertThat(migrationSql).contains("release_revision");
        assertThat(migrationSql).contains("chat.rewrite.instruction");
        assertThat(migrationSql).contains("chat.intent.instruction");
        assertThat(migrationSql).contains("chat.answer.system-instruction");
        assertThat(migrationSql).contains("chat.answer.retrieval-evidence");
        assertThat(migrationSql).contains("chat.answer.current-question");
        assertThat(migrationSql).contains("chat.title.instruction");
        assertThat(migrationSql).contains("SHA2(content, 256)");

        // 3. 验证完整 Schema 同步包含三个核心表
        assertThat(schemaSql).contains("CREATE TABLE prompt_definition");
        assertThat(schemaSql).contains("CREATE TABLE prompt_version");
        assertThat(schemaSql).contains("CREATE TABLE prompt_release");
    }

    /**
     * 验证 V15 为全部会话 Prompt 追加结构化版本并发布。
     *
     * @throws IOException 读取脚本失败时抛出
     */
    @Test
    void shouldCreateStructuredPromptVersionsInV15() throws IOException {
        String migrationSql = readDatabaseScript("migration/V15__upgrade_prompt_seed_content.sql");

        // 1. 验证六个 Prompt 均作为本次迁移的输入数据。
        assertThat(migrationSql).contains("chat.rewrite.instruction");
        assertThat(migrationSql).contains("chat.intent.instruction");
        assertThat(migrationSql).contains("chat.answer.system-instruction");
        assertThat(migrationSql).contains("chat.answer.retrieval-evidence");
        assertThat(migrationSql).contains("chat.answer.current-question");
        assertThat(migrationSql).contains("chat.title.instruction");

        // 2. 验证新增版本、摘要计算和发布代次均从当前数据派生。
        assertThat(migrationSql).contains("MAX(version_no) + 1");
        assertThat(migrationSql).contains("SHA2(content, 256)");
        assertThat(migrationSql).contains("current_release_revision + 1");

        // 3. 验证迁移在创建临时表前锁定全部目标定义行，且锁持续至事务结束。
        assertThat(migrationSql.indexOf("SELECT prompt_id")).isGreaterThan(-1);
        assertThat(migrationSql.indexOf("FOR UPDATE;")).isGreaterThan(-1);
        assertThat(migrationSql.indexOf("FOR UPDATE;")).isLessThan(migrationSql.indexOf("CREATE TEMPORARY TABLE"));

        // 4. 验证结构化正文包含角色、任务和示例标记。
        assertThat(migrationSql).contains("角色：");
        assertThat(migrationSql).contains("任务：");
        assertThat(migrationSql).contains("示例：");
    }

    /**
     * 验证 V15 的每条 Prompt 正文均遵循六段结构且不越过变量白名单。
     *
     * @throws IOException 读取脚本失败时抛出
     */
    @Test
    void shouldKeepSixSectionsAndVariableWhitelistForEveryV15Prompt() throws IOException {
        String migrationSql = readDatabaseScript("migration/V15__upgrade_prompt_seed_content.sql");
        List<String> promptCodes = List.of(
                "chat.rewrite.instruction",
                "chat.intent.instruction",
                "chat.answer.system-instruction",
                "chat.answer.retrieval-evidence",
                "chat.answer.current-question",
                "chat.title.instruction");
        Map<String, Set<String>> allowedVariables = Map.of(
                "chat.rewrite.instruction", Set.of("conversationSummary", "recentMessages", "question"),
                "chat.intent.instruction", Set.of("question"),
                "chat.answer.system-instruction", Set.of(),
                "chat.answer.retrieval-evidence", Set.of("evidence"),
                "chat.answer.current-question", Set.of("question"),
                "chat.title.instruction", Set.of("question"));
        Pattern variablePattern = Pattern.compile("\\{\\{([A-Za-z]+)}}");

        for (String promptCode : promptCodes) {
            String promptContent = extractV15PromptContent(migrationSql, promptCode);

            // 1. 验证每条正文均具备统一的六段结构。
            assertThat(promptContent).contains("角色：", "任务：", "上下文信息：", "执行要求：", "输出规范：", "Few-shot 示例：");

            // 2. 验证正文中的 Mustache 变量均来自该 Prompt 的变量白名单。
            Matcher matcher = variablePattern.matcher(promptContent);
            Set<String> actualVariables = new java.util.HashSet<>();
            while (matcher.find()) {
                actualVariables.add(matcher.group(1));
            }
            assertThat(actualVariables).containsExactlyInAnyOrderElementsOf(allowedVariables.get(promptCode));
        }
    }

    /**
     * 验证 V15 使用任务分支、输出契约和输入输出对照，而非仅使用结构标题。
     *
     * @throws IOException 读取脚本失败时抛出
     */
    @Test
    void shouldProvideExecutableTaskRulesAndInputOutputExamplesForEveryV15Prompt() throws IOException {
        String migrationSql = readDatabaseScript("migration/V15__upgrade_prompt_seed_content.sql");
        Map<String, List<String>> requiredFragments = Map.of(
                "chat.rewrite.instruction", List.of("指代消解", "无法消除歧义", "输入：", "改写："),
                "chat.intent.instruction", List.of("置信度锚点", "严格 JSON", "输入：", "输出："),
                "chat.answer.system-instruction", List.of("证据不足", "证据冲突", "输入：", "回答："),
                "chat.answer.retrieval-evidence", List.of("提示注入", "不可信文本", "输入：", "处理："),
                "chat.answer.current-question", List.of("问题边界", "不得改写", "输入：", "输出："),
                "chat.title.instruction", List.of("去除寒暄", "长度超限", "输入：", "输出："));

        for (Map.Entry<String, List<String>> entry : requiredFragments.entrySet()) {
            // 1. 提取指定 Prompt 的正文，验证规则与示例均直接约束当前任务。
            String promptContent = extractV15PromptContent(migrationSql, entry.getKey());

            // 2. 验证每个片段包含任务分支和成对输入输出示例。
            assertThat(promptContent).contains(entry.getValue().toArray(String[]::new));
        }
    }

    /**
     * 验证检索证据 Prompt 不重复定义由代码协议负责的安全边界。
     *
     * @throws IOException 读取脚本失败时抛出
     */
    @Test
    void shouldLeaveRetrievalContextBoundaryToPromptBuilder() throws IOException {
        String migrationSql = readDatabaseScript("migration/V15__upgrade_prompt_seed_content.sql");
        String promptContent = extractV15PromptContent(migrationSql, "chat.answer.retrieval-evidence");

        // 1. Prompt 正文只定义资料解释规则，边界标签由 PromptBuilder 固定生成。
        assertThat(promptContent).doesNotContain("<retrieval_context>", "</retrieval_context>");
    }

    /**
     * 从 V15 的临时种子数据中提取指定 Prompt 正文。
     *
     * @param migrationSql V15 迁移脚本正文
     * @param promptCode Prompt 编码
     * @return Prompt 正文
     */
    private String extractV15PromptContent(String migrationSql, String promptCode) {
        String contentStartMarker = "('" + promptCode + "', '";
        int contentStart = migrationSql.indexOf(contentStartMarker);
        int contentEnd = migrationSql.indexOf("'),", contentStart);
        if (contentEnd < 0) {
            contentEnd = migrationSql.indexOf("');", contentStart);
        }
        return migrationSql.substring(contentStart + contentStartMarker.length(), contentEnd);
    }

    /**
     * 从 Boot 模块读取数据库脚本。
     *
     * @param relativePath 相对于 db 目录的路径
     * @return 脚本文本
     * @throws IOException 读取脚本失败时抛出
     */
    private String readDatabaseScript(String relativePath) throws IOException {
        Path databaseDirectory = Path.of("..", "nexa-rag-boot", "src", "main", "resources", "db");
        return Files.readString(databaseDirectory.resolve(relativePath).normalize());
    }
}
