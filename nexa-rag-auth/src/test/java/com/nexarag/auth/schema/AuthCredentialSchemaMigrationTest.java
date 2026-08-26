package com.nexarag.auth.schema;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 凭据与安全审计数据库脚本契约测试。
 */
class AuthCredentialSchemaMigrationTest {

    /**
     * 验证凭据迁移只保存长期凭据和验证码审计元数据，不保存验证码本身。
     *
     * @throws IOException 读取数据库脚本失败时抛出
     */
    @Test
    void shouldDefineCredentialAndSecurityTablesWithoutVerificationCodeStorage() throws IOException {
        String migrationSql = readDatabaseScript("migration/V25__add_auth_credentials_and_security_schema.sql");
        String schemaSql = readDatabaseScript("schema/nexa_rag_schema.sql");

        // 1. 验证长期凭据、挑战元数据、设备和审计表均已定义。
        assertThat(migrationSql).contains("CREATE TABLE auth_password_credential",
                "CREATE TABLE auth_email_credential", "CREATE TABLE auth_external_identity",
                "CREATE TABLE auth_email_verification_challenge", "CREATE TABLE auth_device_session",
                "CREATE TABLE auth_security_audit_event");

        // 2. 验证密码哈希、邮箱唯一性和验证码元数据边界。
        assertThat(migrationSql).contains("password_hash VARCHAR(512) NOT NULL",
                "UNIQUE KEY uk_auth_email_credential_email_key", "context_hash CHAR(64) NOT NULL",
                "sa_token_session_key_hash CHAR(64) NOT NULL", "idx_auth_email_challenge_expire");
        assertThat(migrationSql).doesNotContain("code_hash", "verification_code", "password VARCHAR");

        // 3. 验证全量 Schema 与增量迁移同步维护。
        assertThat(schemaSql).contains("CREATE TABLE auth_password_credential",
                "CREATE TABLE auth_email_credential", "CREATE TABLE auth_external_identity",
                "CREATE TABLE auth_email_verification_challenge", "CREATE TABLE auth_device_session",
                "CREATE TABLE auth_security_audit_event");
    }

    /**
     * 从 Boot 模块读取数据库脚本。
     *
     * @param relativePath 相对于 db 目录的路径
     * @return 脚本文本
     * @throws IOException 读取数据库脚本失败时抛出
     */
    private String readDatabaseScript(String relativePath) throws IOException {
        Path databaseDirectory = Path.of("..", "nexa-rag-boot", "src", "main", "resources", "db");
        return Files.readString(databaseDirectory.resolve(relativePath).normalize());
    }
}
