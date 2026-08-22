# NexaRAG

NexaRAG 是一个基于 Spring Boot 的多模块 RAG 单体应用。

## 模块

- `nexa-rag-common`：通用响应、异常、错误码、TraceId。
- `nexa-rag-infra`：基础设施适配能力。
- `nexa-rag-model`：模型治理、路由、熔断、Prompt 模板。
- `nexa-rag-document`：文档和片段业务。
- `nexa-rag-retrieval`：向量召回、关键词召回和排序。
- `nexa-rag-chat`：会话、消息和记忆能力。
- `nexa-rag-workflow`：Graph 工作流编排。
- `nexa-rag-auth`：登录鉴权预留模块。
- `nexa-rag-boot`：启动和总装配模块。
- `nexa-rag-front`：基于 React、Vite 和 TypeScript 的 RAG 对话前端。

## nexa-rag-front 前端开发

前端当前提供 RAG 对话工作台、知识库文档管理、模型配置、路由管理、模型治理与提示词管理。界面采用飞书桌面客户端风格（顶栏 + 图标栏 + 模块面板 + 内容区）。界面中展示置灰的未来 Agent 占位入口；数据分析 Agent、智能差旅 Agent 等尚未接入后端，不会发起请求；RAG 为默认模式。

- 对话入口为 `/chat`，知识库文档入口为 `/knowledge-base`，模型配置为 `/models`，路由管理为 `/models/routes`，模型治理为 `/models/governance`，提示词管理为 `/prompts`，设置为 `/settings`。
- 文档上传一期只提交文件、标题和描述，处理配置使用后端默认值。
- 详情页仅对处理中状态每 5 秒轮询；原文件预览、状态筛选和高级配置见 `TODO.md`。

### 前置条件

1. 先启动 `nexa-rag-boot` 后端服务，默认前端代理目标为 `http://localhost:8009`。
2. 本地已安装 Node.js 20 或更高版本及 npm。

### 安装与启动

```powershell
cd nexa-rag-front
npm install
npm run dev
```

如后端地址不是默认值，可在启动前通过 `VITE_API_TARGET` 指定代理目标：

```powershell
$env:VITE_API_TARGET = "http://localhost:8009"
npm run dev
```

前端会调用以下已实现接口：

- `GET /api/conversations`：查询当前用户的会话列表。
- `GET /api/conversations/{conversationId}/messages`：按序号游标查询指定会话的历史消息，首次默认返回最新 20 条；滚动到顶部时使用 `beforeSequence` 继续读取更早记录。
- `DELETE /api/conversations/{conversationId}`：逻辑删除会话及其消息；存在生成中回答的会话会被拒绝删除。
- `POST /api/chat/stream`：以 SSE 流式发送 RAG 对话。
- `DELETE /api/chat/generations/{generationId}`：取消正在生成的回答。
- `GET /api/documents`：按页查询知识库文档。
- `POST /api/documents/upload`：上传知识库文档。
- `GET /api/documents/{documentId}`：查询文档详情及处理状态。

当前流式消息仅展示文本和生成状态。RAG 检索引用的 SSE 事件与历史引用数据契约尚未补齐，因此前端暂不展示引用来源。


### 前端验证

```powershell
cd nexa-rag-front
npm test
npm run build
```

## 本地验证

```powershell
mvn clean test
```

## 真实环境集成验证

默认测试不会连接外部中间件。需要验证 MySQL、Redis、Elasticsearch、Milvus 时，显式开启集成测试并通过环境变量或 Maven 参数传入密码。

```powershell
mvn -pl nexa-rag-boot -am test "-Dtest=ExternalInfrastructureSmokeTest" "-Dsurefire.failIfNoSpecifiedTests=false" "-Dnexa.integration.enabled=true"
```

集成环境配置在 `nexa-rag-boot/src/main/resources/application-integration.yml` 中，密码均使用占位符读取，不在代码仓库保存明文。
