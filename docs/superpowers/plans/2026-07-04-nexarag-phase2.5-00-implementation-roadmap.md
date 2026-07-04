# Phase 2.5 真实文档入库流水线总览实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** 将 Phase 2.5 真实文档入库流水线拆成多个可独立验证、可逐批编码的中文实施计划。

**Architecture:** 按上传与配置、Redis 排队、本地 Worker、Parser、Splitter、Retrieval、Workflow、清理补偿、集成验证拆分。每个计划只实现一组闭合能力，并保持 infra -> document -> retrieval -> workflow -> boot 的依赖边界。

**Tech Stack:** Java 21、Spring Boot 3.5.x、Maven 多模块、MyBatis-Plus、JUnit 5、AssertJ、Redis、MinIO、MinerU、Tika、Spring AI Alibaba Graph。

---

## 环境约定

- MinIO 默认部署在 127.0.0.1，建议默认端口为 9000，实际端口通过配置覆盖。
- MinerU 默认部署在 127.0.0.1，建议默认接口根地址为 http://127.0.0.1:8000，实际端口通过配置覆盖。
- MySQL、Redis、Elasticsearch、Milvus 默认部署在 192.168.0.134，沿用 pplication-integration.yml 中现有默认值。
- 默认单元测试不连接外部中间件，集成测试必须显式开启。

## 实施顺序

1. 2026-07-04-nexarag-phase2.5-01-upload-minio-plan.md：上传 DTO、默认配置、MinIO 保存、上传即入队返回。
2. 2026-07-04-nexarag-phase2.5-02-redis-local-worker-plan.md：Redis 队列态、本地 Worker、租约和排队状态查询。
3. 2026-07-04-nexarag-phase2.5-03-parser-adapters-plan.md：MinerU、Tika、Markdown 透传解析器。
4. 2026-07-04-nexarag-phase2.5-04-document-splitters-plan.md：真实 Markdown、文本、Excel/CSV 切分器。
5. 2026-07-04-nexarag-phase2.5-05-retrieval-index-plan.md：Retrieval 索引接口、mock 索引实现、索引清理接口。
6. 2026-07-04-nexarag-phase2.5-06-workflow-graph-plan.md：文档入库 Graph、Node、StateKeys、Workflow 单元测试。
7. 2026-07-04-nexarag-phase2.5-07-cleanup-compensation-plan.md：重处理清理、删除资源清理和补偿任务。
8. 2026-07-04-nexarag-phase2.5-08-integration-architecture-plan.md：真实中间件集成冒烟、架构边界增强、最终验收。

## 通用执行规则

- 每批先写失败测试，再写最小实现，再扩大验证。
- 代码注释、日志、JavaDoc 使用简体中文。
- 每个新增类必须有说明用途的中文 JavaDoc。
- Workflow 模块不得直接读写 Mapper、MinIO、MinerU、Tika、ES、Milvus。
- Document 模块不得依赖 Workflow。
- Infra 模块不得依赖业务模块。
- 每批完成后用 git-commit-workflow 单独提交。

## 总验收命令

`powershell
mvn -pl nexa-rag-infra,nexa-rag-document,nexa-rag-retrieval,nexa-rag-workflow -am test
mvn -pl nexa-rag-boot -am test -Dtest=ModuleDependencyTest "-Dsurefire.failIfNoSpecifiedTests=false"
git diff --check
`

Expected: BUILD SUCCESS。