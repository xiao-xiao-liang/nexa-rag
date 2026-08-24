# 身份认证、授权与租户 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 以 Sa-Token 替换固定开发用户认证，交付真实账号、多凭据登录、RBAC、设备安全与企业租户协作能力。

**Architecture:** `nexa-rag-auth` 成为身份领域模块，持有用户、账号、凭据、RBAC、租户成员和安全审计的服务端事实；`nexa-rag-boot` 负责 Sa-Token Web 装配与数据库迁移。Sa-Token 只保存会话与授权状态，`CurrentUserContext` 仍是业务模块读取可信 `userId`/`accountId`/当前 `tenantId` 的兼容边界。

**Tech Stack:** Java 21、Spring Boot 3.5.13、Sa-Token Spring Boot 3 Starter 1.46.0、MyBatis-Plus 3.5.16、MySQL、Redis、Flyway、JUnit 5、Mockito、MockMvc。

> **执行约束：** 数据库字段、索引和初始数据必须先按任务 1 产出并经用户评审，再执行任何 Flyway 迁移。当前工作区存在用户未提交改动；实施期间仅修改本计划列出的文件，不得重置、暂存或覆盖无关改动。未经用户明确授权，不执行 Git commit。

---

## 交付拆分

1. **计划 A：身份与授权基础**（任务 1–6）—— 数据库方案、用户/账号/RBAC、Sa-Token、默认拒绝和默认管理员。
2. **计划 B：本地与邮箱凭据、会话安全**（任务 7–11）—— 注册、密码、验证码、最近验证、设备与审计。
3. **计划 C：第三方认证与企业租户**（任务 12–15）—— OAuth、外部绑定、租户、邀请、工作空间切换。

每段在开始下一段前运行相应模块测试和全量 `mvn test`；浏览器 Cookie/OAuth 回调在具备 HTTPS 测试环境后执行端到端验证。

## 文件结构与职责

| 路径 | 责任 |
| --- | --- |
| `pom.xml` | 管理 Sa-Token 统一版本。 |
| `nexa-rag-auth/pom.xml` | 身份模块引入 Sa-Token、MyBatis-Plus、Spring Mail、Redis 所需依赖。 |
| `nexa-rag-boot/pom.xml` | 保留启动层依赖并引入测试支持。 |
| `nexa-rag-boot/src/main/resources/db/migration/V24__add_identity_rbac_and_tenant_schema.sql` | 用户、账号、RBAC、租户、成员与默认管理员历史映射。 |
| `nexa-rag-boot/src/main/resources/db/migration/V25__add_auth_credentials_and_security_schema.sql` | 密码、邮箱、验证码、第三方身份、最近验证、审计和设备摘要。 |
| `nexa-rag-boot/src/main/resources/db/migration/V26__add_tenant_invitation_schema.sql` | 邀请、所有者转交与租户状态扩展。 |
| `nexa-rag-boot/src/main/resources/db/schema/nexa_rag_schema.sql` | 与增量迁移完全一致的全量 Schema。 |
| `nexa-rag-auth/src/main/java/com/nexarag/auth/**` | 身份实体、Mapper、服务、控制器、Sa-Token 适配、异常和安全策略。 |
| `nexa-rag-boot/src/main/java/com/nexarag/boot/config/AuthWebConfiguration.java` | 取代固定用户过滤器的 Web 认证、CSRF 与授权装配。 |
| `nexa-rag-auth/src/main/java/com/nexarag/auth/context/CurrentUser*.java` | 业务模块可安全读取的请求主体上下文。 |
| `nexa-rag-boot/src/main/java/com/nexarag/boot/NexaRagApplication.java` | 增加 `com.nexarag.auth.mapper` 扫描。 |
| `nexa-rag-boot/src/main/resources/application.yml` | 仅增加环境变量引用的认证配置；不写入密码、OAuth Secret、SMTP 密钥或其他真实凭据。 |
| `nexa-rag-model/src/main/java/com/nexarag/model/controller/**` | 不嵌入角色判断；由统一 `/api/model/**` 鉴权边界保护。 |
| `nexa-rag-boot/src/main/java/com/nexarag/boot/prompt/CurrentUserPromptOperatorProvider.java` | 继续记录不可变 `userId` 作为提示词操作人。 |

## 任务 1：完成并评审身份与租户数据库方案

**Files:**

- Create: `docs/design/identity-auth-schema-review.md`
- Create: `nexa-rag-boot/src/main/resources/db/migration/V24__add_identity_rbac_and_tenant_schema.sql`
- Modify: `nexa-rag-boot/src/main/resources/db/schema/nexa_rag_schema.sql`
- Test: `nexa-rag-auth/src/test/java/com/nexarag/auth/schema/IdentitySchemaMigrationTest.java`

- [ ] **Step 1: 先写失败的 Schema 契约测试。**

测试同时读取增量迁移和全量 Schema，断言二者包含以下表及不可缺少的关系：`auth_user`、`auth_account`、`auth_role`、`auth_permission`、`auth_account_role`、`auth_role_permission`、`tenant`、`tenant_member`。断言 `auth_account.user_id` 唯一、账号名规范化键唯一、`tenant_member(user_id, tenant_id)` 唯一，以及 `ADMIN`、`USER`、模型模块权限初始数据存在。

```java
@Test
void shouldDefineStableUserAccountRbacAndTenantRelations() {
    String migration = read("migration/V24__add_identity_rbac_and_tenant_schema.sql");
    assertThat(migration).contains("CREATE TABLE auth_user", "CREATE TABLE auth_account",
            "CREATE TABLE auth_role", "CREATE TABLE auth_permission", "CREATE TABLE auth_account_role",
            "CREATE TABLE auth_role_permission", "CREATE TABLE tenant", "CREATE TABLE tenant_member");
    assertThat(migration).contains("UNIQUE KEY uk_auth_account_user", "UNIQUE KEY uk_auth_account_name_key",
            "UNIQUE KEY uk_tenant_member_user_tenant", "'ADMIN'", "'USER'", "'model:manage'");
}
```

- [ ] **Step 2: 运行测试并确认当前失败。**

Run: `mvn -pl nexa-rag-auth -am "-Dtest=IdentitySchemaMigrationTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`

Expected: FAIL，提示迁移文件或表定义不存在。

- [ ] **Step 3: 写入可评审的 DDL 方案并暂停等待用户确认。**

在 `docs/design/identity-auth-schema-review.md` 明确列出所有字段、主键、唯一约束、索引、状态枚举、初始数据和历史 `user_id=864019719617777664` 映射。核心表必须采用以下关系：

```text
auth_user(user_id BIGINT PK) 1 ── 1 auth_account(account_id BIGINT PK, user_id UNIQUE)
auth_account N ── N auth_role        通过 auth_account_role
auth_role    N ── N auth_permission  通过 auth_role_permission
auth_user    N ── N tenant           通过 tenant_member(role_code OWNER|MEMBER)
```

默认管理员初始化必须幂等：用配置提供的规范化账号名和邮箱定位保留账号；历史用户 `864019719617777664` 映射到该 `auth_user`；禁止 SQL 写入初始密码、验证码或 OAuth 密钥。提交 DDL 前由用户评审此文档，只有明确确认后才继续本计划的后续步骤。

- [ ] **Step 4: 在用户确认后实现迁移与全量 Schema。**

`V24` 创建用户、账号、角色、权限、账号角色、角色权限、租户和成员表，并创建共享默认租户和默认知识库兼容关系。账号表保存 `account_name`、`account_name_key`、`status`、`user_id`、时间和乐观锁版本；不得把业务 `user_id` 改成账号名。角色/权限种子使 `ADMIN` 拥有 `model:manage`，`USER` 不拥有该权限。

- [ ] **Step 5: 运行 Schema 契约测试。**

Run: `mvn -pl nexa-rag-auth -am "-Dtest=IdentitySchemaMigrationTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`

Expected: PASS。

## 任务 2：建立身份领域实体、Mapper 与错误码

**Files:**

- Create: `nexa-rag-auth/src/main/java/com/nexarag/auth/entity/{AuthUser,AuthAccount,AuthRole,AuthPermission,AuthAccountRole,AuthRolePermission,Tenant,TenantMember}.java`
- Create: `nexa-rag-auth/src/main/java/com/nexarag/auth/mapper/{AuthUserMapper,AuthAccountMapper,AuthRoleMapper,AuthPermissionMapper,AuthAccountRoleMapper,AuthRolePermissionMapper,TenantMapper,TenantMemberMapper}.java`
- Create: `nexa-rag-auth/src/main/java/com/nexarag/auth/enums/{AccountStatus,GlobalRoleCode,TenantMemberRole}.java`
- Create: `nexa-rag-auth/src/main/java/com/nexarag/auth/error/AuthErrorCode.java`
- Modify: `nexa-rag-boot/src/main/java/com/nexarag/boot/NexaRagApplication.java`
- Test: `nexa-rag-auth/src/test/java/com/nexarag/auth/mapper/AuthAccountMapperTest.java`

- [ ] **Step 1: 写失败测试，验证账号名查找和角色/权限查询的 SQL 契约。**

测试应要求 `selectByAccountNameKey`、`selectActiveAccountByVerifiedEmail`、`selectRoleCodesByAccountId`、`selectPermissionCodesByAccountId` 和成员资格查询均存在，并在并发凭据绑定场景使用 `FOR UPDATE` 查询。

- [ ] **Step 2: 运行失败测试。**

Run: `mvn -pl nexa-rag-auth -am -Dtest=AuthAccountMapperTest test`

Expected: FAIL，身份实体和 Mapper 尚不存在。

- [ ] **Step 3: 实现最小实体与 Mapper。**

每个实体使用 `@TableName`、`@TableId(type = IdType.INPUT)`、`Long` ID 和中文 JavaDoc；创建时间使用 `LocalDateTime`。Mapper 继承 `BaseMapper<T>`，对并发改变的账号、成员和角色关系提供显式加锁查询。启动类 MapperScan 加入 `com.nexarag.auth.mapper`。

- [ ] **Step 4: 运行模块测试。**

Run: `mvn -pl nexa-rag-auth -am test`

Expected: PASS。

## 任务 3：替换固定用户过滤器，接入 Sa-Token、Redis 与请求主体上下文

**Files:**

- Modify: `pom.xml`
- Modify: `nexa-rag-auth/pom.xml`
- Modify: `nexa-rag-boot/pom.xml`
- Delete: `nexa-rag-auth/src/main/java/com/nexarag/auth/constants/AuthConstants.java`
- Delete: `nexa-rag-auth/src/main/java/com/nexarag/auth/filter/FixedUserAuthenticationFilter.java`
- Modify: `nexa-rag-auth/src/main/java/com/nexarag/auth/context/CurrentUser.java`
- Modify: `nexa-rag-auth/src/main/java/com/nexarag/auth/context/CurrentUserContext.java`
- Create: `nexa-rag-auth/src/main/java/com/nexarag/auth/context/CurrentSubjectResolver.java`
- Create: `nexa-rag-auth/src/main/java/com/nexarag/auth/config/SaTokenConfiguration.java`
- Modify: `nexa-rag-boot/src/main/java/com/nexarag/boot/config/AuthWebConfiguration.java`
- Modify: `nexa-rag-boot/src/main/resources/application.yml`
- Test: `nexa-rag-auth/src/test/java/com/nexarag/auth/context/CurrentSubjectResolverTest.java`
- Test: `nexa-rag-boot/src/test/java/com/nexarag/boot/config/AuthWebConfigurationTest.java`

- [ ] **Step 1: 写失败测试，要求 Sa-Token 登录身份解析出完整业务主体。**

```java
@Test
void shouldResolveUserAccountAndCurrentTenantFromAuthenticatedSession() {
    when(subjectRepository.findRequired(1001L)).thenReturn(
            new AuthenticatedSubject("864019719617777664", 1001L, "default-tenant"));
    StpUtil.login(1001L);
    assertThat(resolver.resolveRequired()).isEqualTo(
            new CurrentUser("864019719617777664", 1001L, "default-tenant"));
}
```

- [ ] **Step 2: 运行失败测试。**

Run: `mvn -pl nexa-rag-auth -am -Dtest=CurrentSubjectResolverTest test`

Expected: FAIL，Sa-Token 与解析器尚未接入。

- [ ] **Step 3: 添加依赖与配置。**

父 POM 定义 `sa-token.version` 为 `1.46.0`；认证模块引入 `cn.dev33:sa-token-spring-boot3-starter` 与 Redis 序列化适配。配置 `sa-token`：Cookie 名称以 `__Host-` 开头、`timeout=259200`、`active-timeout=-1`、`is-concurrent=true`、`is-share=false`、Cookie `http-only=true`、`secure=true`、`same-site=Strict`、`path=/`、无 Domain。所有可变密钥通过 `${NEXA_AUTH_...}` 环境变量引用。

- [ ] **Step 4: 实现认证过滤链和上下文。**

`CurrentUser` 扩展为 `String userId, Long accountId, String tenantId`。请求过滤链先让 Sa-Token 校验登录态，再由 `CurrentSubjectResolver` 从数据库/会话可靠解析主体并写入 ThreadLocal，finally 清理。不得继续注入固定用户。对流式请求确保 Reactor 回调开始前已经取得不可变主体快照，不能在异步线程直接读取遗留 ThreadLocal。

- [ ] **Step 5: 运行认证模块与启动层测试。**

Run: `mvn -pl nexa-rag-auth,nexa-rag-boot -am test`

Expected: PASS；旧 `FixedUserAuthenticationFilter` 测试被替换，不再有固定 ID 认证。

## 任务 4：实现默认拒绝、CSRF 与 RBAC 授权适配

**Files:**

- Create: `nexa-rag-auth/src/main/java/com/nexarag/auth/authorization/AuthPermissionProvider.java`
- Create: `nexa-rag-auth/src/main/java/com/nexarag/auth/authorization/SaTokenStpInterface.java`
- Create: `nexa-rag-auth/src/main/java/com/nexarag/auth/web/AuthRoutePolicy.java`
- Modify: `nexa-rag-boot/src/main/java/com/nexarag/boot/config/AuthWebConfiguration.java`
- Create: `nexa-rag-auth/src/main/java/com/nexarag/auth/web/CsrfTokenService.java`
- Create: `nexa-rag-auth/src/main/java/com/nexarag/auth/web/CsrfRequestValidator.java`
- Test: `nexa-rag-boot/src/test/java/com/nexarag/boot/config/ApiAuthorizationIntegrationTest.java`

- [ ] **Step 1: 写失败的 MockMvc 授权矩阵测试。**

```java
@Test
void shouldRequireLoginForBusinessAndAdminPermissionForModelRoutes() throws Exception {
    mockMvc.perform(get("/api/chat/conversations")).andExpect(status().isUnauthorized());
    loginAsUser();
    mockMvc.perform(get("/api/model/prompts")).andExpect(status().isForbidden());
    loginAsAdmin();
    mockMvc.perform(get("/api/model/prompts")).andExpect(status().isOk());
}
```

另加测试：仅精确的登录、注册、验证码发送/验证、密码重置、OAuth 发起/回调和管理员首次激活匿名放行；已登录账号设置接口不可因 `/api/auth/` 前缀匿名放行。

- [ ] **Step 2: 运行失败测试。**

Run: `mvn -pl nexa-rag-boot -am -Dtest=ApiAuthorizationIntegrationTest test`

Expected: FAIL，当前所有请求仍会被固定用户过滤器放行。

- [ ] **Step 3: 实现路由策略与权限读取。**

`SaTokenStpInterface` 仅根据已认证 `accountId` 读取 `auth_account_role → auth_role_permission`，返回权限码；不得按账号名、请求参数或前端角色判断。`/api/model/**` 统一要求 `model:manage`，其余 `/api/**` 默认 `checkLogin()`。所有状态变更请求校验自定义 CSRF Header、同源 Origin 和 Fetch Metadata；OAuth 回调只校验服务端 state，不依赖 Strict Cookie。

- [ ] **Step 4: 统一未认证/未授权响应。**

将 Sa-Token 未登录和无权限异常转换为项目 `Result` 格式，并保留 HTTP 401/403。认证失败类响应不得暴露账号、邮箱、状态或凭据是否存在。

- [ ] **Step 5: 运行测试。**

Run: `mvn -pl nexa-rag-boot -am test`

Expected: PASS。

## 任务 5：实现默认管理员初始化、历史数据映射与受控恢复骨架

**Files:**

- Create: `nexa-rag-auth/src/main/java/com/nexarag/auth/config/BootstrapAdministratorProperties.java`
- Create: `nexa-rag-auth/src/main/java/com/nexarag/auth/service/BootstrapAdministratorService.java`
- Create: `nexa-rag-auth/src/main/java/com/nexarag/auth/service/impl/BootstrapAdministratorServiceImpl.java`
- Create: `nexa-rag-auth/src/main/java/com/nexarag/auth/runner/BootstrapAdministratorRunner.java`
- Create: `nexa-rag-auth/src/main/java/com/nexarag/auth/service/ControlledAdministratorRecoveryService.java`
- Test: `nexa-rag-auth/src/test/java/com/nexarag/auth/service/BootstrapAdministratorServiceImplTest.java`

- [ ] **Step 1: 写失败测试，覆盖幂等初始化。**

测试第一次创建保留管理员用户、账号、`ADMIN` 角色与默认租户成员；第二次执行不新增记录；历史 `userId` 保持 `864019719617777664`；未激活账号没有密码和登录态。

- [ ] **Step 2: 实现环境绑定与初始化。**

仅在 `nexa.auth.bootstrap-admin.enabled=true` 且账号名/邮箱环境变量齐全时执行。初始化使用事务和唯一约束重试，创建 `ACTIVE` 但尚无可用密码、已验证邮箱或第三方凭据的保留账号；不发送验证码、不输出邮箱完整值、不写入任何密码。首次激活在任务 8 的邮箱验证成功后开放。

- [ ] **Step 3: 实现受控恢复服务接口但不暴露 Web Controller。**

恢复服务要求部署层传入受审计工单标识和新的预置邮箱，原账号全部会话、挑战和最近验证授权先失效，再建立或重置为 `ACTIVE` 且无可用凭据的保留管理员账号。该接口只由受控运维命令/Runner 调用，绝不映射公开 HTTP 路由。

- [ ] **Step 4: 运行测试。**

Run: `mvn -pl nexa-rag-auth -am -Dtest=BootstrapAdministratorServiceImplTest test`

Expected: PASS。

## 任务 6：迁移现有业务上下文并完成计划 A 回归

**Files:**

- Modify: `nexa-rag-boot/src/main/java/com/nexarag/boot/controller/ChatController.java`
- Modify: `nexa-rag-chat/src/main/java/com/nexarag/chat/{advisor/ConversationContextAdvisor.java,controller/ConversationController.java}`
- Modify: `nexa-rag-boot/src/main/java/com/nexarag/boot/prompt/CurrentUserPromptOperatorProvider.java`
- Modify: 现有 `CurrentUser` 构造器引用的测试文件
- Test: `nexa-rag-chat/src/test/java/com/nexarag/chat/controller/ConversationControllerTest.java`
- Test: `nexa-rag-boot/src/test/java/com/nexarag/boot/controller/ChatControllerTest.java`

- [ ] **Step 1: 更新失败测试为完整主体构造器。**

所有测试通过 `new CurrentUser("u1", 1001L, "default-tenant")` 设置上下文；断言聊天、会话和提示词操作人仍使用 `userId`，不误改为 `accountId` 或账号名。

- [ ] **Step 2: 修正业务代码只读取正确身份字段。**

个人聊天数据继续取 `CurrentUserContext.getRequired().userId()`；涉及租户的资源入口改为同时读取 `tenantId` 并在任务 15 统一复验成员资格。提示词操作人继续为 `userId`，因为它是业务审计主体。

- [ ] **Step 3: 运行计划 A 回归。**

Run: `mvn -pl nexa-rag-auth,nexa-rag-chat,nexa-rag-model,nexa-rag-boot -am test`

Expected: PASS。

## 任务 7：实现密码凭据与账号密码登录

**Files:**

- Create: `nexa-rag-boot/src/main/resources/db/migration/V25__add_auth_credentials_and_security_schema.sql`
- Modify: `nexa-rag-boot/src/main/resources/db/schema/nexa_rag_schema.sql`
- Create: `nexa-rag-auth/src/main/java/com/nexarag/auth/entity/{PasswordCredential,EmailCredential,EmailVerificationChallenge,RecentVerificationGrant,SecurityAuditEvent,DeviceSession}.java`
- Create: 相应 `mapper/**Mapper.java`
- Create: `nexa-rag-auth/src/main/java/com/nexarag/auth/service/{PasswordService,AuthenticationService,SessionService}.java`
- Create: `nexa-rag-auth/src/main/java/com/nexarag/auth/service/impl/{PasswordServiceImpl,AuthenticationServiceImpl,SessionServiceImpl}.java`
- Test: `nexa-rag-auth/src/test/java/com/nexarag/auth/service/PasswordServiceImplTest.java`

- [ ] **Step 1: 写失败测试。**

覆盖 GitHub 结构规则、Argon2id PHC 哈希、每个密码独立盐、连续五次失败冻结 15 分钟、成功清零、通用错误响应、密码重置撤销所有会话、设置/修改密码不撤销会话。

- [ ] **Step 2: 实现 V25 凭据和安全表。**

密码表一账号一行，保存 PHC 哈希和失败计数/冻结时间；邮箱凭据表以规范化邮箱全局唯一；验证码表保存安全哈希、purpose、context、过期/尝试/消费状态；最近验证授权绑定 `accountId` 与 Sa-Token token-session；安全审计与设备会话只保存脱敏安全摘要。所有唯一约束和查询索引写入增量与全量 Schema。

- [ ] **Step 3: 实现密码服务。**

Argon2id 参数固定为内存 19,456 KiB、迭代 2、并行度 1，并支持成功验证后的重哈希。密码重置事务内更新哈希、清除失败状态、撤销全部 Sa-Token 会话和最近验证授权；不执行 `StpUtil.login`。密码设置/修改只在二次验证满足时可执行。

- [ ] **Step 4: 运行单元测试。**

Run: `mvn -pl nexa-rag-auth -am -Dtest=PasswordServiceImplTest test`

Expected: PASS。

## 任务 8：实现 SMTP 验证码、邮箱注册、邮箱登录与邮箱生命周期

**Files:**

- Create: `nexa-rag-auth/src/main/java/com/nexarag/auth/mail/AuthMailService.java`
- Create: `nexa-rag-auth/src/main/java/com/nexarag/auth/service/{EmailChallengeService,EmailCredentialService,RegistrationService}.java`
- Create: 对应 `impl/*Impl.java`
- Create: `nexa-rag-auth/src/main/java/com/nexarag/auth/controller/AuthController.java`
- Create: `nexa-rag-auth/src/main/java/com/nexarag/auth/dto/auth/**`
- Test: `nexa-rag-auth/src/test/java/com/nexarag/auth/service/EmailChallengeServiceImplTest.java`
- Test: `nexa-rag-auth/src/test/java/com/nexarag/auth/controller/AuthControllerTest.java`

- [ ] **Step 1: 写失败测试。**

覆盖六位码、五分钟、五次验证、同用途 60 秒重发、重发作废旧码、同邮箱跨用途每天 10 次、发送失败不创建挑战、验证码单次消费、注册原子创建、邮箱验证码登录自动签发最近验证授权。

- [ ] **Step 2: 实现邮件与挑战服务。**

邮件正文仅包含用途和验证码；日志只记录 challenge ID、purpose、脱敏邮箱和投递结果。发送限流以 Redis 原子操作实现，日期键固定 Asia/Shanghai。验证在数据库事务中锁定未消费挑战、递增尝试计数并原子标记消费。

- [ ] **Step 3: 实现邮箱生命周期。**

实现首个邮箱绑定（最近验证 + 新邮箱码）、双邮箱验证更换（旧/新码均成功后原子变更并释放旧邮箱）、邮箱密码登录、邮箱验证码登录、密码重置和安全通知。邮箱更换不撤销现有会话；密码重置遵循任务 7 的全会话撤销。

- [ ] **Step 4: 运行测试。**

Run: `mvn -pl nexa-rag-auth -am -Dtest=EmailChallengeServiceImplTest,AuthControllerTest test`

Expected: PASS。

## 任务 9：实现最近验证、账户安全操作和设备会话

**Files:**

- Create: `nexa-rag-auth/src/main/java/com/nexarag/auth/service/{RecentVerificationService,AccountSecurityService,DeviceSessionService,SecurityAuditService}.java`
- Create: 对应 `impl/*Impl.java`
- Create: `nexa-rag-auth/src/main/java/com/nexarag/auth/controller/AccountSecurityController.java`
- Create: `nexa-rag-auth/src/main/java/com/nexarag/auth/web/DeviceIdCookieService.java`
- Test: `nexa-rag-auth/src/test/java/com/nexarag/auth/service/RecentVerificationServiceImplTest.java`
- Test: `nexa-rag-auth/src/test/java/com/nexarag/auth/service/DeviceSessionServiceImplTest.java`

- [ ] **Step 1: 写失败测试。**

覆盖 15 分钟、账号+当前 token-session 绑定、邮箱验证码/第三方登录/注册自动签发、密码登录不签发、新会话/登出/撤销/重置失效、指定设备立即下线、退出所有设备以及三天滑动续期。

- [ ] **Step 2: 实现最近验证和敏感操作门禁。**

敏感操作先校验当前会话的授权记录，未满足时仅允许当前密码或已验证邮箱验证码发起二次验证。邮箱更换与密码重置走独立流程，不可由授权复用绕过。

- [ ] **Step 3: 实现设备摘要和审计。**

设备 Cookie 使用 `__Host-nexa-device-id`、`HttpOnly`、`Secure`、`SameSite=Strict`、`Path=/`、一年滑动。设备名从 User-Agent 派生并允许用户后续标记；禁止硬件指纹。设备列表和安全活动只显示脱敏 IP、约略市、时间、事件和结果，安全活动查询限制近 90 天，清理任务保留 180 天。

- [ ] **Step 4: 运行测试。**

Run: `mvn -pl nexa-rag-auth -am -Dtest=RecentVerificationServiceImplTest,DeviceSessionServiceImplTest test`

Expected: PASS。

## 任务 10：实现可配置 IP 地区策略与安全通知

**Files:**

- Create: `nexa-rag-auth/src/main/java/com/nexarag/auth/ip/{IpLocationStrategy,IpLocation,IpLocationProperties,LocalIpLocationStrategy,TencentIpLocationStrategy,AMapIpLocationStrategy}.java`
- Create: `nexa-rag-auth/src/main/java/com/nexarag/auth/ip/IpLocationConfiguration.java`
- Create: `nexa-rag-auth/src/main/java/com/nexarag/auth/service/SecurityNotificationService.java`
- Modify: `nexa-rag-boot/src/main/resources/application.yml`
- Test: `nexa-rag-auth/src/test/java/com/nexarag/auth/ip/IpLocationConfigurationTest.java`

- [ ] **Step 1: 写失败测试。**

覆盖 `local`、`tencent`、`amap` 策略选择，默认 `tencent`，选定 provider 缺少配置时启动失败，私有 IP/超时/远端失败返回未知城市且不阻止登录。

- [ ] **Step 2: 实现策略和环境变量配置。**

使用 `nexa.auth.ip-location.provider` 选择策略；腾讯/高德 Key 只能通过环境变量读取。高德实现仅对其支持的国内 IPv4 进行调用，其余返回未知。HTTP 客户端必须有连接和读取超时。

- [ ] **Step 3: 接入安全通知。**

密码、邮箱、第三方绑定、设备撤销和账号状态成功变更后异步发送脱敏通知；邮箱更换通知新旧邮箱。通知失败记录监控事件但不回滚业务事务。

- [ ] **Step 4: 运行计划 B 回归。**

Run: `mvn -pl nexa-rag-auth,nexa-rag-boot -am test`

Expected: PASS。

## 任务 11：完成密码/邮箱登录接口的端到端 Cookie 验证

**Files:**

- Modify: `nexa-rag-auth/src/main/java/com/nexarag/auth/controller/AuthController.java`
- Modify: `nexa-rag-auth/src/main/java/com/nexarag/auth/controller/AccountSecurityController.java`
- Test: `nexa-rag-boot/src/test/java/com/nexarag/boot/auth/AuthenticationCookieIntegrationTest.java`

- [ ] **Step 1: 写失败测试。**

用 MockMvc 覆盖注册后直接登录、账号/邮箱密码登录、邮箱验证码登录、密码重置后无新 Cookie、Cookie 属性、滑动续期、CSRF 拒绝跨站状态变更。

- [ ] **Step 2: 实现控制器 DTO 校验和统一结果。**

所有请求 DTO 使用 Jakarta Validation；登录响应不回显密码、验证码、Token 或完整外部主体 ID。账户名可用性检查不能泄露保留管理员邮箱或状态。

- [ ] **Step 3: 运行测试。**

Run: `mvn -pl nexa-rag-boot -am -Dtest=AuthenticationCookieIntegrationTest test`

Expected: PASS。

## 任务 12：实现第三方 OAuth 登录、首次补全与唯一绑定

**Files:**

- Create: `nexa-rag-auth/src/main/java/com/nexarag/auth/oauth/{OAuthProvider,OAuthProviderClient,OAuthStateService,ExternalIdentityService}.java`
- Create: `nexa-rag-auth/src/main/java/com/nexarag/auth/oauth/{qq,feishu,google,github}/**`
- Create: `nexa-rag-auth/src/main/java/com/nexarag/auth/controller/OAuthController.java`
- Create: `nexa-rag-auth/src/main/java/com/nexarag/auth/config/OAuthProviderProperties.java`
- Test: `nexa-rag-auth/src/test/java/com/nexarag/auth/oauth/OAuthStateServiceTest.java`
- Test: `nexa-rag-auth/src/test/java/com/nexarag/auth/service/ExternalIdentityServiceImplTest.java`

- [ ] **Step 1: 写失败测试。**

覆盖 state 单次消费、过期与 provider/context 绑定、PKCE verifier/challenge、外部主体 `(provider, subject)` 全局唯一、首次外部登录要求账号名、账号创建+绑定+默认租户+登录原子性、跨账号绑定拒绝、解绑后释放但不允许删掉最后登录凭据。

- [ ] **Step 2: 实现提供方适配。**

QQ、飞书、Google、GitHub 各自仅实现授权 URL、code 交换和稳定 subject 读取；使用官方最小 scope。访问/刷新 Token 仅在单次交换内存中使用，不写数据库或日志。生产回调固定 `/api/auth/oauth/{provider}/callback`。

- [ ] **Step 3: 实现绑定/解绑二次验证。**

绑定开始生成一次性 action challenge，OAuth 回调仅能完成相同账户/会话的挑战；绑定和解绑均要求最近验证授权，成功写安全审计和通知。

- [ ] **Step 4: 运行测试。**

Run: `mvn -pl nexa-rag-auth -am -Dtest=OAuthStateServiceTest,ExternalIdentityServiceImplTest test`

Expected: PASS。

## 任务 13：实现企业租户、成员和邀请

**Files:**

- Create: `nexa-rag-boot/src/main/resources/db/migration/V26__add_tenant_invitation_schema.sql`
- Modify: `nexa-rag-boot/src/main/resources/db/schema/nexa_rag_schema.sql`
- Create: `nexa-rag-auth/src/main/java/com/nexarag/auth/entity/{TenantInvitation,TenantOwnershipTransfer}.java`
- Create: 对应 `mapper/**Mapper.java`
- Create: `nexa-rag-auth/src/main/java/com/nexarag/auth/service/{TenantService,TenantInvitationService,TenantMembershipService}.java`
- Create: 对应 `impl/*Impl.java`
- Create: `nexa-rag-auth/src/main/java/com/nexarag/auth/controller/TenantController.java`
- Test: `nexa-rag-auth/src/test/java/com/nexarag/auth/service/TenantInvitationServiceImplTest.java`

- [ ] **Step 1: 写失败测试。**

覆盖仅全局管理员可创建企业租户、创建者成为唯一 `OWNER`、仅 `OWNER` 可邀请/撤销/移除、邀请需受邀用户登录接受、普通成员退出、唯一所有者不能退出、移除/退出立即失效。

- [ ] **Step 2: 实现邀请与成员事务。**

邀请目标只接受已验证邮箱或账号名，服务端解析唯一 `userId`；邀请包含目标租户、目标用户、状态、有效期和审计关联。接受邀请时锁定邀请与成员关系，确保幂等且不会重复成员；撤销/过期/拒绝不能创建成员。

- [ ] **Step 3: 运行测试。**

Run: `mvn -pl nexa-rag-auth -am -Dtest=TenantInvitationServiceImplTest test`

Expected: PASS。

## 任务 14：实现当前租户会话、回退与所有者转交

**Files:**

- Create: `nexa-rag-auth/src/main/java/com/nexarag/auth/tenant/CurrentTenantService.java`
- Create: `nexa-rag-auth/src/main/java/com/nexarag/auth/tenant/TenantAccessGuard.java`
- Modify: `nexa-rag-auth/src/main/java/com/nexarag/auth/context/CurrentSubjectResolver.java`
- Modify: `nexa-rag-auth/src/main/java/com/nexarag/auth/service/impl/SessionServiceImpl.java`
- Modify: `nexa-rag-auth/src/main/java/com/nexarag/auth/controller/TenantController.java`
- Test: `nexa-rag-auth/src/test/java/com/nexarag/auth/tenant/CurrentTenantServiceTest.java`

- [ ] **Step 1: 写失败测试。**

覆盖登录默认租户、成员切换到企业租户、非成员切换拒绝、成员移除/租户禁用后下一请求回退默认租户且不退出全部登录态、所有者转交的最近验证与接收确认、原 OWNER 降为 MEMBER。

- [ ] **Step 2: 实现服务端会话租户上下文。**

当前 `tenantId` 只保存在当前 Sa-Token token-session；切换前通过 `TenantAccessGuard` 查询有效成员资格。每个请求和异步/流式执行边界复验成员资格；失效则写审计并把会话值改回默认租户。不得允许请求参数、Header 或路径 tenantId 覆盖会话值。

- [ ] **Step 3: 实现双确认所有者转交。**

当前 OWNER 必须具有未过期最近验证授权；创建只面向现有 MEMBER 的待接受转交记录。接收成员确认后，在单一事务中将接收者升为 OWNER、原所有者降为 MEMBER，并确保任意时刻至少有一个 OWNER。

- [ ] **Step 4: 运行测试。**

Run: `mvn -pl nexa-rag-auth -am -Dtest=CurrentTenantServiceTest test`

Expected: PASS。

## 任务 15：将租户访问复验接入业务模块并完成全量验证

**Files:**

- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/service/impl/KnowledgeBaseServiceImpl.java`
- Modify: 与知识库、文档、检索入口相关的 Controller/Service/Mapper 查询
- Modify: `nexa-rag-chat` 与 `nexa-rag-workflow` 中携带租户范围的异步任务、缓存键和流式执行边界
- Test: `nexa-rag-document/src/test/java/**/TenantAccess*Test.java`
- Test: `nexa-rag-workflow/src/test/java/**/TenantAccess*Test.java`
- Test: `nexa-rag-boot/src/test/java/com/nexarag/boot/auth/EndToEndAuthorizationTest.java`

- [ ] **Step 1: 写失败的跨租户拒绝测试。**

测试成员在默认租户和受邀企业租户间切换后只能读取当前租户资源；成员移除后不能通过旧资源 ID、缓存、异步任务或 SSE 流继续读取企业数据；管理员的 `/api/model/**` 权限不自动绕过企业数据成员校验。

- [ ] **Step 2: 在业务边界加入可信租户范围。**

知识库和文档查询必须带 `CurrentUserContext.getRequired().tenantId()`；异步消息负载携带创建时可信 tenantId，但消费者执行前再次调用 `TenantAccessGuard`。缓存键加入 tenantId，避免不同企业空间命中相同业务缓存。

- [ ] **Step 3: 执行分层回归。**

Run: `mvn -pl nexa-rag-document,nexa-rag-chat,nexa-rag-workflow,nexa-rag-model,nexa-rag-boot -am test`

Expected: PASS。

- [ ] **Step 4: 执行完整构建与人工 HTTPS 验收。**

Run: `mvn test`

Expected: PASS。

在 HTTPS 测试环境人工验证 Cookie 的 `__Host-` 属性、OAuth 回调 state、跨站 CSRF 拒绝、管理员/普通用户路由差异、邮箱投递、设备下线和租户回退。不得在 HTTP 环境降低 `Secure` Cookie 或 OAuth 回调要求。

## 实施前检查清单

- [ ] 用户已评审并确认任务 1 的精确表结构。
- [ ] 部署环境已准备默认管理员账号名/邮箱、Sa-Token 密钥、Redis、SMTP、QQ/飞书/Google/GitHub OAuth 凭据和 IP 定位 Key；所有敏感值均通过环境变量或密钥管理服务提供。
- [ ] 生产 TLS、Nginx 可信转发头和 `__Host-` Cookie 前置条件已经验证。
- [ ] 现有明文配置中的非认证敏感值不在本次无关改动中复制、展示或提交；认证新增配置不允许新增明文密钥。
