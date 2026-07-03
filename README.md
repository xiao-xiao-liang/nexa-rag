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

## 本地验证

```powershell
mvn clean test
```
