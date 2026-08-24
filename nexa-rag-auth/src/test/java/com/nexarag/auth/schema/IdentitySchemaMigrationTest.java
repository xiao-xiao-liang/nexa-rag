package com.nexarag.auth.schema;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 身份、全局 RBAC 与租户数据库脚本契约测试。
 */
class IdentitySchemaMigrationTest {

    /**
     * 验证增量迁移和全量 Schema 同时定义稳定用户账号、RBAC 与租户成员关系。
     *
     * @throws IOException 读取数据库脚本失败时抛出
     */
    @Test
    void shouldDefineStableUserAccountRbacAndTenantRelations() throws IOException {
        String migrationSql = readDatabaseScript("migration/V24__add_identity_rbac_and_tenant_schema.sql");
        String schemaSql = readDatabaseScript("schema/nexa_rag_schema.sql");

        // 1. 验证迁移包含身份、RBAC 和租户主数据表。
        assertThat(migrationSql).contains("CREATE TABLE auth_user", "CREATE TABLE auth_account",
                "CREATE TABLE auth_role", "CREATE TABLE auth_permission", "CREATE TABLE auth_account_role",
                "CREATE TABLE auth_role_permission", "CREATE TABLE tenant", "CREATE TABLE tenant_member");

        // 2. 验证一对一账号、账号名和成员关系均由数据库唯一约束保护。
        assertThat(migrationSql).contains("UNIQUE KEY uk_auth_account_user", "UNIQUE KEY uk_auth_account_name_key",
                "UNIQUE KEY uk_tenant_member_user_tenant");

        // 3. 验证最小全局角色和模型管理权限已初始化。
        assertThat(migrationSql).contains("'ADMIN'", "'USER'", "'model:manage'");

        // 4. 验证全量 Schema 与增量迁移同步维护。
        assertThat(schemaSql).contains("CREATE TABLE auth_user", "CREATE TABLE auth_account",
                "CREATE TABLE auth_role", "CREATE TABLE auth_permission", "CREATE TABLE auth_account_role",
                "CREATE TABLE auth_role_permission", "CREATE TABLE tenant", "CREATE TABLE tenant_member");
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
