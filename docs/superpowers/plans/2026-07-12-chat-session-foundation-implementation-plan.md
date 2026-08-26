# 对话会话基础能力实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `nexa-rag-chat` 中实现可被后续 Workflow 组合调用的会话、消息、摘要、Redis 活跃上下文和模型消息适配基础能力。

**Architecture:** MySQL 保存完整会话历史和摘要版本，Redis 保存“摘要 + 最近 N 轮已完成消息”的活跃上下文快照。会话模块通过 `ConversationContextService` 暴露稳定领域接口，Spring AI Advisor 和 `ModelGateway` 只作为上下文消费适配层，不负责业务消息持久化。

**Tech Stack:** Java 21、Spring Boot 3、MyBatis-Plus、Flyway、MySQL、Spring Data Redis、Redisson、Spring AI 1.1.2、JUnit 5、Mockito、AssertJ。

---

## 文件结构

新增文件集中在以下边界：

```text
nexa-rag-auth/src/main/java/com/nexarag/auth
├── constants/AuthSessionConstants.java
├── context/CurrentUser.java
└── context/UserContext.java

nexa-rag-model/src/main/java/com/nexarag/model/gateway/chat
└── ChatModelMessage.java

nexa-rag-chat/src/main/java/com/nexarag/chat
├── domain
├── enums
├── mapper
├── repository
├── service
├── service/impl
├── context
├── cache
├── config
└── advisor
```

业务表通过 `nexa-rag-boot/src/main/resources/db/migration/V12__add_chat_session_foundation.sql` 创建，并同步更新 `nexa-rag-boot/src/main/resources/db/schema/nexa_rag_schema.sql` 的基准结构。

---

### 任务 1：补充顶层模型消息对象

**Files:**
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/gateway/chat/ChatModelMessage.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/gateway/chat/ChatModelRequest.java`
- Modify: `nexa-rag-model/src/test/java/com/nexarag/model/controller/ModelChatControllerTest.java`
- Modify: `nexa-rag-model/src/test/java/com/nexarag/model/provider/ChatProviderTest.java`

- [ ] **Step 1: 新增顶层传输对象**

```java
package com.nexarag.model.gateway.chat;

/**
 * 模型网关使用的单条聊天消息。
 *
 * @param role 消息角色，支持 SYSTEM、USER、ASSISTANT
 * @param content 消息内容
 */
public record ChatModelMessage(String role, String content) {
}
```

- [ ] **Step 2: 将 `ChatModelRequest` 的消息字段改为 `List<ChatModelMessage>`**

删除 `ChatModelRequest` 内部的 `ChatMessage` 类型，并同步修改 Controller、Provider 和测试中的构造方式。

- [ ] **Step 3: 运行模型模块测试**

运行：`mvn -pl nexa-rag-model -am test`

预期：模型网关、Provider 和 Controller 相关测试全部通过。

---

### 任务 2：增加用户身份边界

**Files:**
- Create: `nexa-rag-auth/src/main/java/com/nexarag/auth/constants/AuthSessionConstants.java`
- Create: `nexa-rag-auth/src/main/java/com/nexarag/auth/context/CurrentUser.java`
- Create: `nexa-rag-auth/src/main/java/com/nexarag/auth/context/UserContext.java`

- [ ] **Step 1: 定义当前租户 Token-Session 键**

将当前租户的 Token-Session 键写入 `AuthSessionConstants.CURRENT_TENANT_ID`。登录成功后由认证服务写入该键，用户切换租户时更新该键。

- [ ] **Step 2: 实现请求上下文门面**

`UserContext` 从 `StpUtil.getLoginIdAsString()` 获取稳定用户 ID，并从 `StpUtil.getTokenSession()` 读取当前租户；不维护 `ThreadLocal`。未登录由 Sa-Token 拒绝，未设置当前租户则失败，禁止降级到默认租户。

- [ ] **Step 3: 由 Sa-Token 路由拦截器完成登录校验**

登录校验由 `SaInterceptor` 与 `SaRouter` 完成，不额外实现固定用户过滤器。异步任务必须在提交前显式复制 `CurrentUser.userId()`。

- [ ] **Step 4: 运行认证模块测试**

运行：`mvn -pl nexa-rag-auth -am test`

预期：未登录请求由 Sa-Token 拒绝，已登录请求可从 `UserContext` 读取用户与当前租户。

---

### 任务 3：创建会话数据库迁移

**Files:**
- Create: `nexa-rag-boot/src/main/resources/db/migration/V12__add_chat_session_foundation.sql`
- Modify: `nexa-rag-boot/src/main/resources/db/schema/nexa_rag_schema.sql`

- [ ] **Step 1: 创建 `chat_conversation` 表**

主键使用 `conversation_id VARCHAR(64)`；`user_id VARCHAR(64)`；状态使用 `VARCHAR(32)`；版本使用 `INT NOT NULL DEFAULT 0`。增加 `(user_id, status, update_time)` 索引。

- [ ] **Step 2: 创建 `chat_message` 表**

主键使用 `message_id VARCHAR(64)`；会话内顺序使用 `BIGINT`；正文使用 `MEDIUMTEXT`；引用使用 `TEXT`；Token 字段使用 `INT`；失败信息使用 `VARCHAR(512)`。增加：

```text
(conversation_id, sequence)
(conversation_id, status, sequence)
(user_id, conversation_id, sequence)
```

- [ ] **Step 3: 创建 `chat_conversation_summary` 表**

主键使用 `summary_id VARCHAR(64)`；摘要正文使用 `TEXT`；保存 `last_message_id` 和 `summary_version`；增加 `(conversation_id, summary_version)` 与 `(user_id, conversation_id, summary_version)` 索引。

- [ ] **Step 4: 同步基准 schema**

将三张表完整同步到 `nexa_rag_schema.sql`，所有表和字段注释使用简体中文。

- [ ] **Step 5: 校验 Flyway 脚本**

运行：`mvn -pl nexa-rag-boot -am -DskipTests compile`

预期：资源能够打包，Flyway 迁移文件命名和 SQL 语法无错误。

---

### 任务 4：实现会话与消息领域模型

**Files:**
- Create: `nexa-rag-chat/src/main/java/com/nexarag/chat/enums/ConversationStatus.java`
- Create: `nexa-rag-chat/src/main/java/com/nexarag/chat/enums/ChatMessageRole.java`
- Create: `nexa-rag-chat/src/main/java/com/nexarag/chat/enums/ChatMessageStatus.java`
- Create: `nexa-rag-chat/src/main/java/com/nexarag/chat/domain/ChatConversation.java`
- Create: `nexa-rag-chat/src/main/java/com/nexarag/chat/domain/ChatMessage.java`
- Create: `nexa-rag-chat/src/main/java/com/nexarag/chat/domain/ChatConversationSummary.java`
- Create: `nexa-rag-chat/src/main/java/com/nexarag/chat/domain/ConversationContext.java`
- Create: `nexa-rag-chat/src/test/java/com/nexarag/chat/domain/ConversationContextTest.java`

- [ ] **Step 1: 定义枚举**

枚举值必须与数据库状态字符串一致：`ACTIVE`、`ARCHIVED`、`DELETED`；`USER`、`ASSISTANT`；`COMPLETED`、`GENERATING`、`FAILED`、`CANCELLED`。

- [ ] **Step 2: 定义领域对象**

使用项目已有 Lombok 风格或不可变 record 风格，字段名称与设计文档和数据库保持一致。每个类添加简体中文 JavaDoc，说明类作用；关键工厂方法添加参数、返回值和状态语义说明。

- [ ] **Step 3: 增加上下文不变量测试**

验证上下文只允许保存用户消息和已完成助手消息；消息按 `sequence` 升序；当前用户问题不属于快照本身。

---

### 任务 5：实现 MySQL Repository 与会话消息服务

**Files:**
- Create: `nexa-rag-chat/src/main/java/com/nexarag/chat/repository/ChatConversationRepository.java`
- Create: `nexa-rag-chat/src/main/java/com/nexarag/chat/repository/ChatMessageRepository.java`
- Create: `nexa-rag-chat/src/main/java/com/nexarag/chat/repository/ChatConversationSummaryRepository.java`
- Create: `nexa-rag-chat/src/main/java/com/nexarag/chat/entity/ChatConversationEntity.java`
- Create: `nexa-rag-chat/src/main/java/com/nexarag/chat/entity/ChatMessageEntity.java`
- Create: `nexa-rag-chat/src/main/java/com/nexarag/chat/entity/ChatConversationSummaryEntity.java`
- Create: `nexa-rag-chat/src/main/java/com/nexarag/chat/repository/impl/MyBatisChatConversationRepository.java`
- Create: `nexa-rag-chat/src/main/java/com/nexarag/chat/repository/impl/MyBatisChatMessageRepository.java`
- Create: `nexa-rag-chat/src/main/java/com/nexarag/chat/repository/impl/MyBatisChatConversationSummaryRepository.java`
- Create: `nexa-rag-chat/src/main/java/com/nexarag/chat/mapper/ChatConversationMapper.java`
- Create: `nexa-rag-chat/src/main/java/com/nexarag/chat/mapper/ChatMessageMapper.java`
- Create: `nexa-rag-chat/src/main/java/com/nexarag/chat/mapper/ChatConversationSummaryMapper.java`
- Create: `nexa-rag-chat/src/main/java/com/nexarag/chat/service/ConversationService.java`
- Create: `nexa-rag-chat/src/main/java/com/nexarag/chat/service/ConversationMessageService.java`
- Create: `nexa-rag-chat/src/main/java/com/nexarag/chat/service/impl/ConversationServiceImpl.java`
- Create: `nexa-rag-chat/src/main/java/com/nexarag/chat/service/impl/ConversationMessageServiceImpl.java`
- Create: `nexa-rag-chat/src/test/java/com/nexarag/chat/service/impl/ConversationServiceImplTest.java`
- Create: `nexa-rag-chat/src/test/java/com/nexarag/chat/service/impl/ConversationMessageServiceImplTest.java`

- [ ] **Step 1: 实现会话归属查询**

所有 Repository 查询必须同时使用 `conversationId` 和 `userId`；不存在或归属不匹配时返回客户端异常，不允许返回其他用户数据。

- [ ] **Step 2: 实现消息序号生成**

在数据库事务内按会话获取下一个 `sequence`，写入用户消息或 assistant 占位消息。消息 ID、会话 ID 和摘要 ID 使用项目统一的雪花字符串生成方式。

- [ ] **Step 3: 实现 assistant 生命周期**

实现：创建 `GENERATING` 占位记录、完成更新、失败更新和取消更新。完成更新必须同时写入正文、Token、引用和更新时间。

- [ ] **Step 4: 添加事务测试**

使用 Mockito 验证：

```text
用户消息保存失败时不创建 assistant 占位记录
assistant 完成时更新正文和 Token
assistant 失败时不进入上下文查询结果
不同 userId 不能读取或修改同一 conversationId
```

- [ ] **Step 5: 运行 Chat 模块测试**

运行：`mvn -pl nexa-rag-chat -am test`

预期：会话和消息服务测试通过。

---

### 任务 6：实现 Redis 活跃上下文缓存

**Files:**
- Create: `nexa-rag-chat/src/main/java/com/nexarag/chat/cache/ConversationContextCache.java`
- Create: `nexa-rag-chat/src/main/java/com/nexarag/chat/cache/RedisConversationContextCache.java`
- Create: `nexa-rag-chat/src/main/java/com/nexarag/chat/config/ChatContextProperties.java`
- Create: `nexa-rag-chat/src/main/java/com/nexarag/chat/constants/ChatContextConstants.java`
- Create: `nexa-rag-chat/src/test/java/com/nexarag/chat/cache/RedisConversationContextCacheTest.java`

- [ ] **Step 1: 定义缓存接口**

接口只提供：

```text
get(userId, conversationId)
put(context)
evict(userId, conversationId)
refreshTtl(userId, conversationId)
```

- [ ] **Step 2: 实现 Redis JSON 序列化**

使用项目现有 Spring Data Redis 能力，将 `ConversationContext` 序列化为 JSON。键固定为：

```text
nexa:chat:context:{userId}:{conversationId}:v1
```

缓存异常只记录中文告警并返回未命中，不得阻断 MySQL 回源。

- [ ] **Step 3: 增加分布式锁封装**

锁键固定为：

```text
nexa:chat:context:lock:{userId}:{conversationId}
```

缓存重建和写入必须使用锁，并按 `version` 或 `lastMessageId` 拒绝旧快照覆盖新快照。

- [ ] **Step 4: 测试命中、未命中和 Redis 异常**

验证命中刷新 TTL、未命中返回空、Redis 异常降级为空结果、旧版本不能覆盖新版本。

---

### 任务 7：实现上下文加载与摘要服务

**Files:**
- Create: `nexa-rag-chat/src/main/java/com/nexarag/chat/service/ConversationContextService.java`
- Create: `nexa-rag-chat/src/main/java/com/nexarag/chat/service/ConversationSummaryService.java`
- Create: `nexa-rag-chat/src/main/java/com/nexarag/chat/service/impl/ConversationContextServiceImpl.java`
- Create: `nexa-rag-chat/src/main/java/com/nexarag/chat/service/impl/ConversationSummaryServiceImpl.java`
- Create: `nexa-rag-chat/src/main/java/com/nexarag/chat/context/ConversationContextBuilder.java`
- Create: `nexa-rag-chat/src/main/java/com/nexarag/chat/context/ConversationMessageSelector.java`
- Create: `nexa-rag-chat/src/test/java/com/nexarag/chat/service/impl/ConversationContextServiceImplTest.java`
- Create: `nexa-rag-chat/src/test/java/com/nexarag/chat/service/impl/ConversationSummaryServiceImplTest.java`

- [ ] **Step 1: 实现 `loadForTurn`**

严格执行：

```text
1. 校验会话归属
2. 读取 Redis 活跃上下文
3. 命中时刷新 TTL 并返回
4. 未命中时读取最新摘要和最近 N 轮已完成消息
5. 构建上下文并写入 Redis
6. 返回当前用户问题之前的上下文
```

- [ ] **Step 2: 实现最近 N 轮选择**

以用户消息为轮次边界，查询最近 `historyKeepTurns` 轮，并按 `sequence` 升序返回。过滤空正文和未完成助手消息。

- [ ] **Step 3: 实现摘要增量生成**

仅在 assistant 消息完成后异步触发。使用 Redisson 会话锁，读取最新摘要的 `lastMessageId`，只总结该 ID 之后的新消息，并保存新的 `summaryVersion`。

- [ ] **Step 4: 实现摘要失败降级**

摘要模型调用失败时保留旧摘要，记录会话 ID、用户 ID、消息数量和异常，不阻断主对话。

- [ ] **Step 5: 实现事务后缓存重建**

消息事务提交后重建 Redis；摘要写入成功后重建 Redis。缓存重建失败只记录告警，下一次读取时从 MySQL 回源。

- [ ] **Step 6: 测试关键场景**

验证缓存命中不访问 Repository、缓存未命中能回源、摘要按 `lastMessageId` 增量、摘要锁阻止重复生成、失败保留旧摘要、生成中消息不进入上下文。

---

### 任务 8：实现模型消息映射和只读 Advisor

**Files:**
- Create: `nexa-rag-chat/src/main/java/com/nexarag/chat/mapper/ConversationContextMessageMapper.java`
- Create: `nexa-rag-chat/src/main/java/com/nexarag/chat/advisor/ConversationContextAdvisor.java`
- Create: `nexa-rag-chat/src/test/java/com/nexarag/chat/mapper/ConversationContextMessageMapperTest.java`
- Create: `nexa-rag-chat/src/test/java/com/nexarag/chat/advisor/ConversationContextAdvisorTest.java`

- [ ] **Step 1: 实现领域消息到模型消息的映射**

映射规则：摘要变成一条 `SYSTEM` 消息，历史消息保留 `USER` 或 `ASSISTANT` 角色，当前用户问题由调用方在映射结果末尾显式追加。

- [ ] **Step 2: 实现只读 `ConversationContextAdvisor`**

Advisor 只读取 `ConversationContextService` 并将上下文注入 Spring AI 调用，不调用消息保存、摘要保存或 Redis 更新方法。

- [ ] **Step 3: 验证 ModelGateway 适配**

使用 `ConversationContextMessageMapper` 生成 `List<ChatModelMessage>`，确保 `nexa-rag-chat` 不暴露数据库实体，`nexa-rag-model` 不依赖 `nexa-rag-chat`。

- [ ] **Step 4: 验证不重复写消息**

Advisor 测试必须断言消息服务和缓存服务均未被调用。

---

### 任务 9：接入固定用户过滤器和模块启动验证

**Files:**
- Modify: `nexa-rag-boot/pom.xml`（仅在依赖传递不足时补充 `nexa-rag-chat`）
- Modify: `nexa-rag-boot/src/main/resources/application.yml`（仅补充会话模块必要配置，不修改现有用户改动）
- Create: `nexa-rag-boot/src/test/java/com/nexarag/boot/config/ChatSessionConfigurationTest.java`
- Modify: `nexa-rag-boot/src/test/java/com/nexarag/architecture/ModuleDependencyTest.java`（仅在新增边界需要时修改）

- [ ] **Step 1: 注册固定用户过滤器**

确认 `FixedUserAuthenticationFilter` 被 Spring Boot 扫描并在请求链路中执行；不新增用户 ID YAML 配置。

- [ ] **Step 2: 确认 Chat 模块 Bean 装配**

验证 Repository、Service、Redis Cache、Summary Service 和 Advisor 能在 Boot 上下文中完成装配。

- [ ] **Step 3: 运行启动与架构测试**

运行：`mvn -pl nexa-rag-boot -am -Dtest=ChatSessionConfigurationTest,ModuleDependencyTest "-Dsurefire.failIfNoSpecifiedTests=false" test`

预期：上下文加载成功，模块依赖方向符合现有架构测试。

---

### 任务 10：全量验证和提交

**Files:**
- Verify: 本计划涉及的全部新增和修改文件

- [ ] **Step 1: 运行 Chat 模块测试**

运行：`mvn -pl nexa-rag-chat -am test`

- [ ] **Step 2: 运行 Auth 模块测试**

运行：`mvn -pl nexa-rag-auth -am test`

- [ ] **Step 3: 运行 Boot 启动和架构测试**

运行：`mvn -pl nexa-rag-boot -am -Dtest=ChatSessionConfigurationTest,ModuleDependencyTest "-Dsurefire.failIfNoSpecifiedTests=false" test`

- [ ] **Step 4: 检查工作区**

运行：`git status --short --branch`

确认不包含既有的 `nexa-rag-boot/src/main/resources/application.yml` 修改和 `.superpowers/` 目录。

- [ ] **Step 5: 按功能拆分提交**

使用中文 Conventional Commit：

```text
feat(auth): 增加固定用户身份上下文
feat(model): 提取顶层聊天消息对象
feat(chat): 实现会话消息和摘要基础能力
feat(chat): 增加活跃会话上下文缓存
feat(chat): 增加模型上下文适配
```

每个提交只包含对应功能文件，并在提交前复核暂存区。

---

## 计划自检

- 设计文档中的会话、消息、摘要、上下文、Redis 快照和 Advisor 均有对应任务。
- 用户 ID 固定为雪花字符串，不增加 YAML 配置，不在请求内重新生成。
- 当前用户消息在 `loadForTurn` 之后持久化，避免缓存未命中导致重复加入。
- MySQL 是完整事实源，Redis 只保存可重建上下文快照。
- Advisor 不负责消息持久化，避免与业务消息服务重复写入。
- 计划不包含对话 Workflow 的 Node、Edge 或流程编排。
- 所有步骤均包含明确的文件、行为和验证方式，没有未定义的任务占位内容。
