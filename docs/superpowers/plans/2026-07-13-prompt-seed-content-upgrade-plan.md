# Prompt 种子内容升级实施计划

> **面向执行代理：** 必须逐任务执行并使用复选框跟踪；在 `master` 分支开发，完成后保留全部改动供用户 Review，未经用户确认不得提交。

**目标：** 以新增数据迁移的方式，为六个会话 Prompt 创建结构化新版正文并正式发布。

**架构：** 迁移从当前定义读取变量契约和发布代次，追加不可变版本与发布记录，再原子更新当前发布指针；不改写 V14 或任何历史正文。

**技术栈：** MySQL、Flyway、JUnit 5、AssertJ。

---

### 任务 1：新增结构化 Prompt 数据迁移

**文件：**

- 新建：`nexa-rag-boot/src/main/resources/db/migration/V15__upgrade_prompt_seed_content.sql`
- 修改：`nexa-rag-model/src/test/java/com/nexarag/model/prompt/PromptSchemaMigrationTest.java`

- [ ] **步骤 1：编写失败测试**

在 `PromptSchemaMigrationTest` 中增加 `shouldCreateStructuredPromptVersionsInV15`，读取 V15 并断言包含六个 Prompt 编码、`MAX(version_no) + 1`、`SHA2(content, 256)`、`release_revision + 1`，以及文本标记“角色：”“任务：”“示例：”。

- [ ] **步骤 2：运行失败测试**

运行：`mvn -pl nexa-rag-model -Dtest=PromptSchemaMigrationTest test`

预期：失败，提示找不到 V15 迁移脚本。

- [ ] **步骤 3：编写最小迁移**

新增 V15，使用临时派生表按 `prompt_code` 提供六条结构化正文。插入版本时使用：

```sql
SELECT prompt_id, COALESCE(MAX(version_no), 0) + 1
FROM prompt_version
WHERE prompt_id = ?
```

正文使用 `SHA2(content, 256)` 计算摘要。发布记录以当前正式版本为基线，指向新版本，发布代次为 `current_release_revision + 1`；更新定义的当前发布记录和发布代次。

- [ ] **步骤 4：运行绿色测试**

运行：`mvn -pl nexa-rag-model -Dtest=PromptSchemaMigrationTest test`

预期：通过。

- [ ] **步骤 5：检查差异**

运行：`git diff --check`

预期：无空白错误；改动保留且不提交。
