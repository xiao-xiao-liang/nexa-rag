# multipart 请求头修复 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 FormData 上传请求保留浏览器生成的 multipart 请求头，同时保持普通 JSON 请求的默认行为。

**Architecture:** 修改前端通用 `request` 函数的默认请求头决策。仅当请求体不是 FormData、且调用方未指定 Content-Type 时才补充 application/json；上传 API 无需调整。

**Tech Stack:** TypeScript、Fetch API、Vitest、jsdom。

---

### Task 1: 修复通用请求头处理

**Files:**
- Modify: `nexa-rag-front/src/shared/api/client.ts:27-30`
- Modify: `nexa-rag-front/src/features/knowledge-base/api/document-api.test.ts:20-32`

- [ ] **Step 1: 写入失败的回归测试**

在 `document-api.test.ts` 的上传测试中，紧接 `expect(init.method).toBe('POST')` 后增加断言：

```ts
expect(new Headers(init.headers).has('Content-Type')).toBe(false)
```

- [ ] **Step 2: 运行测试确认失败**

Run: `npm test -- src/features/knowledge-base/api/document-api.test.ts`

Expected: FAIL，断言发现上传请求带有 `Content-Type: application/json`。

- [ ] **Step 3: 最小化修改请求头决策**

将 `client.ts` 的请求头处理改为：

```ts
const isFormDataRequest = init?.body instanceof FormData
if (isFormDataRequest) {
  headers.delete('Content-Type')
}

if (init?.body != null && !isFormDataRequest && !headers.has('Content-Type')) {
  headers.set('Content-Type', 'application/json')
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `npm test -- src/features/knowledge-base/api/document-api.test.ts`

Expected: PASS，上传请求不含显式 Content-Type，已有 FormData 内容断言仍通过。

- [ ] **Step 5: 执行前端完整验证**

Run: `npm test && npm run build`

Expected: 测试和 TypeScript/Vite 构建均成功。
