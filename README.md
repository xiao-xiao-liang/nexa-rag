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

## 真实环境集成验证

默认测试不会连接外部中间件。需要验证 MySQL、Redis、Elasticsearch、Milvus 时，显式开启集成测试并通过环境变量或 Maven 参数传入密码。

```powershell
mvn -pl nexa-rag-boot -am test "-Dtest=ExternalInfrastructureSmokeTest" "-Dsurefire.failIfNoSpecifiedTests=false" "-Dnexa.integration.enabled=true"
```

集成环境配置在 `nexa-rag-boot/src/main/resources/application-integration.yml` 中，密码均使用占位符读取，不在代码仓库保存明文。
