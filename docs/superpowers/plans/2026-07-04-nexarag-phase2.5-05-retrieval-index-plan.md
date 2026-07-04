# Phase 2.5-05 检索索引接口 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** 实现 retrieval 文档索引服务接口、mock 索引实现和索引清理入口。

**Architecture:** 本计划承接上一批已完成能力，只实现当前批次闭环。实现必须遵循 spec 中的模块边界，业务能力沉淀在对应模块，workflow 只编排服务调用。

**Tech Stack:** Java 21、Spring Boot 3.5.x、Maven 多模块、JUnit 5、AssertJ、Mockito。

---

## 环境约定

- MinIO 默认地址为 127.0.0.1。
- MinerU 默认地址为 127.0.0.1。
- MySQL、Redis、Elasticsearch、Milvus 默认地址为 192.168.0.134。
- 默认单元测试不访问外部服务；外部服务只在集成测试中显式开启。

## Scope Check

包含：DocumentIndexService、DocumentIndexResult、mock vectorId、mock keywordIndexId、skipIndex 状态处理、cleanupDocumentIndex。

不包含：真实 Elasticsearch/Milvus 写入、真实 embedding 模型调用、Rerank。

## File Structure

`	ext
E:\Code\Projects\MyProject\AI\nexa-rag
├── pom.xml
├── nexa-rag-infra
├── nexa-rag-document
├── nexa-rag-retrieval
├── nexa-rag-workflow
└── nexa-rag-boot
`

本批涉及模块：$(@{Num=05; File=2026-07-04-nexarag-phase2.5-05-retrieval-index-plan.md; Title=检索索引接口; Goal=实现 retrieval 文档索引服务接口、mock 索引实现和索引清理入口。; Modules=nexa-rag-retrieval, nexa-rag-document; Includes=DocumentIndexService、DocumentIndexResult、mock vectorId、mock keywordIndexId、skipIndex 状态处理、cleanupDocumentIndex。; Excludes=真实 Elasticsearch/Milvus 写入、真实 embedding 模型调用、Rerank。; Test=DocumentIndexServiceImplTest,DocumentIndexCleanupTest; Commit=feat(retrieval): 定义文档索引服务}.Modules)。

## Task 1: 基线验证

- [ ] **Step 1: 检查工作区**

`powershell
git status --short --branch
`

Expected: 工作区只包含本批计划允许的改动，或完全干净。

- [ ] **Step 2: 运行相关模块测试**

`powershell
mvn -pl nexa-rag-retrieval,nexa-rag-document -am test
`

Expected: BUILD SUCCESS。

## Task 2: 按 TDD 新增核心契约

- [ ] **Step 1: 新增失败测试**

测试类建议：$(@{Num=05; File=2026-07-04-nexarag-phase2.5-05-retrieval-index-plan.md; Title=检索索引接口; Goal=实现 retrieval 文档索引服务接口、mock 索引实现和索引清理入口。; Modules=nexa-rag-retrieval, nexa-rag-document; Includes=DocumentIndexService、DocumentIndexResult、mock vectorId、mock keywordIndexId、skipIndex 状态处理、cleanupDocumentIndex。; Excludes=真实 Elasticsearch/Milvus 写入、真实 embedding 模型调用、Rerank。; Test=DocumentIndexServiceImplTest,DocumentIndexCleanupTest; Commit=feat(retrieval): 定义文档索引服务}.Test)。

测试必须覆盖本批包含范围中的核心行为，并优先验证真实业务结果，不只验证方法被调用。

- [ ] **Step 2: 运行测试确认失败**

`powershell
mvn -pl nexa-rag-retrieval -am test -Dtest=DocumentIndexServiceImplTest,DocumentIndexCleanupTest "-Dsurefire.failIfNoSpecifiedTests=false"
`

Expected: 测试因为类或行为尚未实现而失败，不应因为语法错误失败。

- [ ] **Step 3: 实现最小生产代码**

实现要求：

- 新增类必须有简体中文 JavaDoc。
- 关键方法必须有简体中文 JavaDoc。
- 方法关键步骤使用编号注释。
- 日志使用简体中文，并避免输出完整文档内容和敏感配置。
- 不修改当前批次无关文件。

- [ ] **Step 4: 运行最小测试确认通过**

`powershell
mvn -pl nexa-rag-retrieval -am test -Dtest=DocumentIndexServiceImplTest,DocumentIndexCleanupTest "-Dsurefire.failIfNoSpecifiedTests=false"
`

Expected: BUILD SUCCESS。

## Task 3: 扩大验证

- [ ] **Step 1: 运行模块测试**

`powershell
mvn -pl nexa-rag-retrieval,nexa-rag-document -am test
`

Expected: BUILD SUCCESS。

- [ ] **Step 2: 运行架构边界测试**

`powershell
mvn -pl nexa-rag-boot -am test -Dtest=ModuleDependencyTest "-Dsurefire.failIfNoSpecifiedTests=false"
`

Expected: BUILD SUCCESS。

- [ ] **Step 3: 检查空白问题**

`powershell
git diff --check
`

Expected: no output。

## Task 4: 提交

- [ ] **Step 1: 使用 git-commit-workflow 检查并提交**

`powershell
git status --short
git diff --stat
git commit -m "feat(retrieval): 定义文档索引服务"
`

Expected: 提交成功，工作区干净。

## 自审

- 本计划只覆盖当前批次能力。
- 本计划没有要求实现 spec 非目标。
- 本计划保留 Phase 2.5 的模块边界。