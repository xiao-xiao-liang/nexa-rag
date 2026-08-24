# 外部文档请求契约修复 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 使飞书外部文档导入前端按后端 API 契约发送链接和可选标题。

**Architecture:** 保持后端 `ExternalDocumentSubmitDTO` 的 `sourceUrl` 与 `title` 契约不变，仅修正前端 API 适配层的请求类型与 JSON 字段映射。后端已有的默认标题和异步回写飞书原标题逻辑继续生效。

**Tech Stack:** React、TypeScript、Fetch API、Spring Boot REST API。

---

### Task 1: 对齐外部文档导入请求字段

**Files:**
- Modify: `nexa-rag-studio/src/lib/api.ts:288-301`

- [x] **Step 1: 将适配层请求类型改为后端字段名**

```ts
request: { sourceType: string; sourceUrl: string; title?: string }
```

- [x] **Step 2: 保持请求体直接序列化该对象**

```ts
body: JSON.stringify(request),
```

- [x] **Step 3: 不新增或运行自动化测试**

用户明确要求本次不需要测试；该改动仅调整前端字段名，验证范围限于静态检查、差异检查和请求体代码复核。

- [x] **Step 4: 检查差异与空白错误**

Run: `git diff -- nexa-rag-studio/src/lib/api.ts` and `git diff --check`

Expected: 请求类型仅包含 `sourceUrl` 和可选 `title`，且无空白错误。

### Task 2: 从上传弹窗传递契约字段

**Files:**
- Modify: `nexa-rag-studio/src/features/document/components/DocumentUploadModal.tsx:76-80`

- [x] **Step 1: 将飞书表单中的链接映射为 `sourceUrl`**

```ts
sourceUrl: externalUrl.trim(),
```

- [x] **Step 2: 将可选自定义标题映射为 `title`**

```ts
title: title.trim() || undefined,
```

- [x] **Step 3: 检查差异与空白错误**

Run: `git diff -- nexa-rag-studio/src/features/document/components/DocumentUploadModal.tsx` and `git diff --check`

Expected: 不再发送 `documentUrl` 或 `customTitle`，且无空白错误。
