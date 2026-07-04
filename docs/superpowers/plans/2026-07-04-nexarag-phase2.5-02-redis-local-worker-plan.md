# Phase 2.5-02 Redis 队列与本地 Worker 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** 实现整条文档流水线只排一次的 Redis 队列态、本地 Worker、租约和排队状态查询。

**Architecture:** 本计划承接上一批已完成能力，只实现当前批次闭环。实现必须遵循 spec 中的模块边界，业务能力沉淀在对应模块，workflow 只编排服务调用。

**Tech Stack:** Java 21、Spring Boot 3.5.x、Maven 多模块、JUnit 5、AssertJ、Mockito。

---

## 环境约定

- MinIO 默认地址为 127.0.0.1。
- MinerU 默认地址为 127.0.0.1。
- MySQL、Redis、Elasticsearch、Milvus 默认地址为 192.168.0.134。
- 默认单元测试不访问外部服务；外部服务只在集成测试中显式开启。

## Scope Check

包含：Redis waiting/running/lease/retry key、DocumentProcessTaskDispatcher Redis 实现、本地线程池 Worker、queuePosition、waitingCount。

不包含：阶段级队列完整实现、MQ 或 Redis Stream、多实例强一致协议。

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

本批涉及模块：$(@{Num=02; File=2026-07-04-nexarag-phase2.5-02-redis-local-worker-plan.md; Title=Redis 队列与本地 Worker; Goal=实现整条文档流水线只排一次的 Redis 队列态、本地 Worker、租约和排队状态查询。; Modules=nexa-rag-infra, nexa-rag-document, nexa-rag-boot; Includes=Redis waiting/running/lease/retry key、DocumentProcessTaskDispatcher Redis 实现、本地线程池 Worker、queuePosition、waitingCount。; Excludes=阶段级队列完整实现、MQ 或 Redis Stream、多实例强一致协议。; Test=DocumentPipelineQueueTest,LocalDocumentPipelineWorkerTest,DocumentQueueStatusTest; Commit=feat(document): 接入Redis排队与本地Worker}.Modules)。

## Task 1: 基线验证

- [ ] **Step 1: 检查工作区**

`powershell
git status --short --branch
`

Expected: 工作区只包含本批计划允许的改动，或完全干净。

- [ ] **Step 2: 运行相关模块测试**

`powershell
mvn -pl nexa-rag-infra,nexa-rag-document,nexa-rag-boot -am test
`

Expected: BUILD SUCCESS。

## Task 2: 按 TDD 新增核心契约

- [ ] **Step 1: 新增失败测试**

测试类建议：$(@{Num=02; File=2026-07-04-nexarag-phase2.5-02-redis-local-worker-plan.md; Title=Redis 队列与本地 Worker; Goal=实现整条文档流水线只排一次的 Redis 队列态、本地 Worker、租约和排队状态查询。; Modules=nexa-rag-infra, nexa-rag-document, nexa-rag-boot; Includes=Redis waiting/running/lease/retry key、DocumentProcessTaskDispatcher Redis 实现、本地线程池 Worker、queuePosition、waitingCount。; Excludes=阶段级队列完整实现、MQ 或 Redis Stream、多实例强一致协议。; Test=DocumentPipelineQueueTest,LocalDocumentPipelineWorkerTest,DocumentQueueStatusTest; Commit=feat(document): 接入Redis排队与本地Worker}.Test)。

测试必须覆盖本批包含范围中的核心行为，并优先验证真实业务结果，不只验证方法被调用。

- [ ] **Step 2: 运行测试确认失败**

`powershell
mvn -pl nexa-rag-infra -am test -Dtest=DocumentPipelineQueueTest,LocalDocumentPipelineWorkerTest,DocumentQueueStatusTest "-Dsurefire.failIfNoSpecifiedTests=false"
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
mvn -pl nexa-rag-infra -am test -Dtest=DocumentPipelineQueueTest,LocalDocumentPipelineWorkerTest,DocumentQueueStatusTest "-Dsurefire.failIfNoSpecifiedTests=false"
`

Expected: BUILD SUCCESS。

## Task 3: 扩大验证

- [ ] **Step 1: 运行模块测试**

`powershell
mvn -pl nexa-rag-infra,nexa-rag-document,nexa-rag-boot -am test
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
git commit -m "feat(document): 接入Redis排队与本地Worker"
`

Expected: 提交成功，工作区干净。

## 自审

- 本计划只覆盖当前批次能力。
- 本计划没有要求实现 spec 非目标。
- 本计划保留 Phase 2.5 的模块边界。