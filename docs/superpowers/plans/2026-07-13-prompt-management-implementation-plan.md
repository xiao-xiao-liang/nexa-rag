# Prompt 统一管理体系实施计划

> **面向执行代理：** 必须逐任务执行并使用复选框跟踪；在 `master` 分支开发，完成后保留全部改动供用户 Review，未经用户确认不得提交。

**目标：** 建立数据库驱动的全局 Prompt 在线管理、发布、灰度、回滚、缓存刷新与 Workflow 请求级版本快照能力。

**架构：** `PromptBuilder` 固定消息协议和检索证据安全边界；`PromptReleaseResolver` 在 Workflow 开始时绑定版本；`PromptRenderService` 按绑定版本渲染。Redis Pub/Sub 用于快速失效，数据库发布代次定时对账补偿漏消息。

**技术栈：** Spring Boot、MyBatis-Plus、MySQL、Flyway、Spring Data Redis、Mustache.java、JUnit 5、AssertJ。

---

## 文件结构

| 路径 | 职责 |
| --- | --- |
| `nexa-rag-boot/src/main/resources/db/migration/V14__add_prompt_management.sql` | Prompt 定义、版本、发布记录与种子数据。 |
| `nexa-rag-model/src/main/java/com/nexarag/model/prompt/**` | 持久化、校验、发布、版本解析、渲染和缓存。 |
| `nexa-rag-model/src/main/java/com/nexarag/model/prompt/refresh/**` | Redis 刷新消息和发布代次对账。 |
| `nexa-rag-model/src/main/java/com/nexarag/model/controller/PromptController.java` | 在线查询、预览、提交、灰度与回滚接口。 |
| `nexa-rag-model/src/main/java/com/nexarag/model/prompt/PromptBuilder.java` | 固定消息顺序并渲染 Prompt，不依赖 Workflow、Chat 或 Retrieval 模块。 |
| `nexa-rag-workflow/src/main/java/com/nexarag/workflow/service/chat/ChatWorkflowRunner.java` | 请求开始时创建 PromptExecutionSnapshot。 |

### 任务 1：创建 Prompt 数据库模型与种子数据

**文件：**

- 新建：`nexa-rag-boot/src/main/resources/db/migration/V14__add_prompt_management.sql`
- 修改：`nexa-rag-boot/src/main/resources/db/schema/nexa_rag_schema.sql`
- 测试：`nexa-rag-model/src/test/java/com/nexarag/model/prompt/PromptSchemaMigrationTest.java`

- [ ] **步骤 1：编写失败测试**

断言 V14 包含 `prompt_definition`、`prompt_version`、`prompt_release`，且有 `prompt_code` 唯一索引、`release_revision` 与六个会话 Prompt 种子 Code。

- [ ] **步骤 2：运行失败测试**

运行：`mvn -pl nexa-rag-model -Dtest=PromptSchemaMigrationTest test`

预期：失败，提示 V14 或种子定义不存在。

- [ ] **步骤 3：编写迁移**

使用以下定义关系：

```sql
prompt_definition(prompt_id, prompt_code, name, variable_schema, enabled,
                  current_release_id, current_release_revision, create_time, update_time)
prompt_version(version_id, prompt_id, version_no, content, content_checksum,
               variable_schema_snapshot, created_by, created_at, remark)
prompt_release(release_id, prompt_id, stable_version_id, canary_version_id,
               canary_rule, release_revision, released_by, released_at,
               rollback_from_release_id, remark)
```

将现有 `ChatWorkflowPromptBuilder` 中的中文正文导入版本表，并为每个定义创建初始正式发布记录。

- [ ] **步骤 4：验证并提交**

运行：`mvn -pl nexa-rag-model -Dtest=PromptSchemaMigrationTest test`

执行 `git diff --check` 与目标测试；保留改动，等待用户 Review 后再提交。

### 任务 2：实现持久化、模板校验与发布服务

**文件：**

- 新建：`nexa-rag-model/src/main/java/com/nexarag/model/prompt/entity/PromptDefinition.java`
- 新建：`nexa-rag-model/src/main/java/com/nexarag/model/prompt/entity/PromptVersion.java`
- 新建：`nexa-rag-model/src/main/java/com/nexarag/model/prompt/entity/PromptRelease.java`
- 新建：`nexa-rag-model/src/main/java/com/nexarag/model/prompt/mapper/PromptDefinitionMapper.java`
- 新建：`nexa-rag-model/src/main/java/com/nexarag/model/prompt/mapper/PromptVersionMapper.java`
- 新建：`nexa-rag-model/src/main/java/com/nexarag/model/prompt/mapper/PromptReleaseMapper.java`
- 新建：`nexa-rag-model/src/main/java/com/nexarag/model/prompt/PromptTemplateValidator.java`
- 新建：`nexa-rag-model/src/main/java/com/nexarag/model/prompt/PromptPublishService.java`
- 测试：`nexa-rag-model/src/test/java/com/nexarag/model/prompt/PromptTemplateValidatorTest.java`
- 测试：`nexa-rag-model/src/test/java/com/nexarag/model/prompt/PromptPublishServiceTest.java`

- [ ] **步骤 1：编写失败测试**

覆盖 Mustache 不闭合、未登记变量、空正文、重复正文；覆盖提交时新增不可变版本和发布记录，回滚时只新增发布记录且不改写历史正文。

- [ ] **步骤 2：实现最小接口**

```java
public interface PromptTemplateValidator {
    void validate(String promptCode, String content, PromptVariableSchema schema);
}

public interface PromptPublishService {
    PromptReleaseResult submit(String promptCode, String content, String operator);
    PromptReleaseResult release(String promptCode, Long stableVersionId, Long canaryVersionId,
                                PromptCanaryRule canaryRule, String operator);
    PromptReleaseResult rollback(String promptCode, Long targetVersionId, String operator);
}
```

`submit` 在一个事务中校验、创建版本、创建正式发布记录、递增发布代次和更新当前指针。变量缺失在渲染期失败，不能静默填空。

- [ ] **步骤 3：运行测试并提交**

运行：`mvn -pl nexa-rag-model -Dtest=PromptTemplateValidatorTest,PromptPublishServiceTest test`

执行 `git diff --check` 与目标测试；保留改动，等待用户 Review 后再提交。

### 任务 3：实现版本快照、灰度选择和渲染缓存

**文件：**

- 新建：`nexa-rag-model/src/main/java/com/nexarag/model/prompt/PromptExecutionSnapshot.java`
- 新建：`nexa-rag-model/src/main/java/com/nexarag/model/prompt/PromptReleaseResolver.java`
- 新建：`nexa-rag-model/src/main/java/com/nexarag/model/prompt/PromptRenderService.java`
- 新建：`nexa-rag-model/src/main/java/com/nexarag/model/prompt/PromptSnapshotCache.java`
- 修改：`nexa-rag-model/src/main/java/com/nexarag/model/config/ModelConfiguration.java`
- 测试：`nexa-rag-model/src/test/java/com/nexarag/model/prompt/PromptReleaseResolverTest.java`
- 测试：`nexa-rag-model/src/test/java/com/nexarag/model/prompt/PromptRenderServiceTest.java`

- [ ] **步骤 1：编写失败测试**

覆盖正式版本、同一用户同一代次稳定灰度命中、代次变化后重新计算、旧快照不受新发布影响、缺失必填变量失败以及原文输出不进行 HTML 转义。

- [ ] **步骤 2：实现解析和渲染**

```java
PromptExecutionSnapshot resolve(Set<String> promptCodes, String subjectId);
RenderedPrompt render(PromptExecutionSnapshot snapshot, String promptCode,
                      Map<String, Object> variables);
```

灰度桶固定为 `hash(promptCode + releaseRevision + subjectId) % 10000`。当前发布缓存键为 `promptCode`，指定版本缓存键为 `promptCode + versionId`；只设置最大权重，不设置 TTL，不缓存渲染后的会话和证据内容。

- [ ] **步骤 3：运行测试并提交**

运行：`mvn -pl nexa-rag-model -Dtest=PromptReleaseResolverTest,PromptRenderServiceTest test`

执行 `git diff --check` 与目标测试；保留改动，等待用户 Review 后再提交。

### 任务 4：实现 Redis 快速失效和发布代次对账

**文件：**

- 新建：`nexa-rag-model/src/main/java/com/nexarag/model/prompt/refresh/PromptReleaseChangedMessage.java`
- 新建：`nexa-rag-model/src/main/java/com/nexarag/model/prompt/refresh/PromptRefreshPublisher.java`
- 新建：`nexa-rag-model/src/main/java/com/nexarag/model/prompt/refresh/redis/RedisPromptRefreshMessageClient.java`
- 新建：`nexa-rag-model/src/main/java/com/nexarag/model/prompt/refresh/redis/RedisPromptRefreshSubscriber.java`
- 新建：`nexa-rag-model/src/main/java/com/nexarag/model/prompt/PromptReleaseReconciler.java`
- 新建：`nexa-rag-model/src/main/java/com/nexarag/model/config/PromptRefreshProperties.java`
- 修改：`nexa-rag-boot/src/main/resources/application.yml`
- 测试：`nexa-rag-model/src/test/java/com/nexarag/model/prompt/refresh/PromptReleaseReconcilerTest.java`

- [ ] **步骤 1：编写失败测试**

断言更高代次事件仅失效对应当前发布缓存；旧事件被忽略；漏收事件时对账发现数据库高代次并失效；Redis 重连后立即触发对账。

- [ ] **步骤 2：实现刷新**

复用 `nexa-rag-model` 中模型注册表的 Redis Pub/Sub 配置模式。事务提交后先执行 `invalidateCurrent(promptCode)`，再向 `nexa.prompt.release.changed` 发布 JSON；对账任务只查询 `promptCode` 与 `current_release_revision`，绝不读取 Prompt 正文。

- [ ] **步骤 3：运行测试并提交**

运行：`mvn -pl nexa-rag-model -Dtest=PromptReleaseReconcilerTest test`

执行 `git diff --check` 与目标测试；保留改动，等待用户 Review 后再提交。

### 任务 5：提供在线管理接口

**文件：**

- 新建：`nexa-rag-model/src/main/java/com/nexarag/model/controller/PromptController.java`
- 新建：`nexa-rag-model/src/main/java/com/nexarag/model/dto/prompt/PromptSubmitRequest.java`
- 新建：`nexa-rag-model/src/main/java/com/nexarag/model/dto/prompt/PromptPreviewRequest.java`
- 新建：`nexa-rag-model/src/main/java/com/nexarag/model/dto/prompt/PromptReleaseRequest.java`
- 新建：`nexa-rag-model/src/main/java/com/nexarag/model/dto/prompt/PromptResponse.java`
- 测试：`nexa-rag-model/src/test/java/com/nexarag/model/controller/PromptControllerTest.java`

- [ ] **步骤 1：编写失败测试**

覆盖查询、版本历史、预览不写数据库且不调用模型、提交立即生效、灰度范围为 0 至 100、回滚版本必须属于当前 Prompt。

- [ ] **步骤 2：实现 REST 接口**

实现 `GET /api/model/prompts`、`GET /api/model/prompts/{promptCode}`、`POST /api/model/prompts/{promptCode}/preview`、`POST /api/model/prompts/{promptCode}/submit`、`POST /api/model/prompts/{promptCode}/release`、`POST /api/model/prompts/{promptCode}/rollback`。操作者取自 `UserContext`；预览只使用脱敏变量渲染，不调用模型。

- [ ] **步骤 3：运行测试并提交**

运行：`mvn -pl nexa-rag-model -Dtest=PromptControllerTest test`

执行 `git diff --check` 与目标测试；保留改动，等待用户 Review 后再提交。

### 任务 6：将会话 Workflow 迁移到请求级快照

**文件：**

- 新建：`nexa-rag-model/src/main/java/com/nexarag/model/prompt/PromptBuilder.java`
- 删除：`nexa-rag-workflow/src/main/java/com/nexarag/workflow/prompt/ChatWorkflowPromptBuilder.java`
- 修改：`nexa-rag-workflow/src/main/java/com/nexarag/workflow/constants/ChatWorkflowStateKeys.java`
- 修改：`nexa-rag-workflow/src/main/java/com/nexarag/workflow/service/chat/ChatWorkflowRunner.java`
- 修改：`nexa-rag-workflow/src/main/java/com/nexarag/workflow/node/chat/QuestionRewriteNode.java`
- 修改：`nexa-rag-workflow/src/main/java/com/nexarag/workflow/node/chat/IntentRecognitionNode.java`
- 修改：`nexa-rag-workflow/src/main/java/com/nexarag/workflow/node/chat/AnswerGenerationNode.java`
- 测试：`nexa-rag-workflow/src/test/java/com/nexarag/workflow/prompt/PromptBuilderTest.java`
- 测试：`nexa-rag-workflow/src/test/java/com/nexarag/workflow/service/chat/ChatWorkflowRunnerTest.java`

- [ ] **步骤 1：编写失败测试**

断言 Runner 在 Graph 执行前写入 `PROMPT_EXECUTION_SNAPSHOT`；新版本发布不会改变旧快照；改写、意图、回答的消息角色和顺序不变；检索证据边界由固定代码生成。

- [ ] **步骤 2：实现 PromptBuilder 与节点迁移**

`PromptBuilder` 只接收字符串、变量映射、`PromptExecutionSnapshot` 和 `ChatModelMessage`，不得依赖 Workflow、Chat 或 Retrieval 模块。Workflow 负责把 `ConversationContext` 与 `RetrievalChunk` 格式化为摘要、历史消息和证据字符串，再调用 Builder。Runner 使用 `USER_ID` 为本次 Workflow 的所有 Code 解析快照。三个节点删除对旧 Builder 的依赖；模型调用日志传递 `promptCode`、`versionId`、`releaseId` 和 `releaseRevision`。

- [ ] **步骤 3：运行测试并提交**

运行：`mvn -pl nexa-rag-workflow -am -Dtest=PromptBuilderTest,ChatWorkflowRunnerTest,QuestionRewriteNodeTest,IntentRecognitionNodeTest "-Dsurefire.failIfNoSpecifiedTests=false" test`

执行 `git diff --check` 与工作流定向测试；保留改动，等待用户 Review 后再提交。

### 任务 7：移除旧本地模板运行时路径并回归

**文件：**

- 修改：`nexa-rag-model/src/main/java/com/nexarag/model/config/ModelConfiguration.java`
- 删除：`nexa-rag-model/src/main/java/com/nexarag/model/prompt/LocalPromptTemplateRepository.java`
- 删除：`nexa-rag-model/src/main/java/com/nexarag/model/prompt/PromptTemplateRepository.java`
- 删除：`nexa-rag-model/src/main/java/com/nexarag/model/prompt/PromptTemplate.java`
- 修改或删除：`nexa-rag-model/src/main/java/com/nexarag/model/prompt/PromptTemplateService.java`
- 修改或删除：`nexa-rag-model/src/test/java/com/nexarag/model/prompt/PromptTemplateServiceTest.java`
- 删除或迁移：`nexa-rag-model/src/test/resources/prompts/chat/query-rewrite.md`

- [ ] **步骤 1：编写失败测试**

断言 Spring 上下文不再注册 `LocalPromptTemplateRepository`，所有生产渲染均通过数据库版本快照。

- [ ] **步骤 2：删除旧实现**

移除 `classpath*:/prompts/**/*.md` 的运行时 Bean。Markdown 仅可作为初始化导入源或测试夹具，不能成为运行时事实源。

- [ ] **步骤 3：回归、检查与提交**

运行：`mvn -pl nexa-rag-model,nexa-rag-workflow -am test`

执行 `git diff --check` 与模块回归；保留改动，等待用户 Review 后再提交。

## 实施前复核

- 版本正文不可变；编辑、灰度和回滚均新增发布记录。
- 缓存无 TTL；Pub/Sub 快速失效，发布代次对账保证存活实例最终收敛。
- 在线编辑不能改变消息角色、顺序或检索证据安全边界。
- 同一 Workflow 内每个 Prompt 的版本绑定稳定，不要求不同 Prompt 使用相同版本号。
- 每次模型调用可追溯到 Prompt Code、版本、发布记录和发布代次。
