# Langfuse 子 Span 注解化设计

## 目标与边界

将不需要向下游传递子 Span 上下文、且全部观测属性可由方法入参或返回值准确导出的工作流节点，从手写的 Span 生命周期收敛为声明式 LangfuseSpan 注解。首批迁移证据筛选和父子上下文扩展节点，减少业务代码中的遥测侵入，同时保持 Langfuse 的父子关系、类型、属性和失败语义不变。

本设计不更换 Langfuse 的 OpenTelemetry 导出方案。当前 Java 路线继续使用原生 OpenTelemetry；LangfuseTelemetry 门面继续作为业务模块与 OTel 实现之间的边界。

## 非目标

- 不引入非官方或不稳定的 Java Langfuse tracing SDK。
- 不改造根 Trace 的 LangfuseTrace 注解。
- 不迁移 RerankNode：它必须将 span.context 传给模型网关，保证 Rerank Generation 位于 rerank Span 之下。
- 不修改 Prompt、Token 分段、导出器配置、CORS 或公开接口。

## 方案选择

保留手写 Scope 没有基础设施成本，但会持续重复生命周期模板。Micrometer Observed 不能表达 Langfuse Observation Type、受限属性键空间、OTel carrier 父上下文和返回值属性规则。

采用新增 LangfuseSpan 注解的方案：在现有 AOP、表达式解析器和 LangfuseTelemetry 门面上增加最小声明式扩展，切面统一处理生命周期，节点只保留业务结果和日志。

## 注解契约

LangfuseSpan 是方法级注解，包含以下成员：

- name：Span 名称；
- type：LangfuseObservationType，默认 SPAN；
- parentContextCarrier：返回字符串 carrier 的 SpEL；
- attributes：调用前解析的 键=SpEL；
- resultAttributes：正常返回后解析的 键=SpEL。

parentContextCarrier 由切面经 LangfuseOtelContextCodec.decode 还原 OTel Context。空值或非法 carrier 降级为 root Context，不影响业务。属性键继续限制为 nexa.、gen_ai.、langfuse. 前缀，且禁止 input、output、prompt 正文。resultAttributes 的表达式根对象为 #result，值仅接受 String、Boolean 和数值。

## 生命周期与异常语义

LangfuseSpanAspect 仅拦截同步方法：

1. 解析入参属性和父 carrier，调用 telemetry.startSpan。
2. 执行业务方法。
3. 正常返回时从 #result 解析 resultAttributes 并补充 Span 属性。
4. 抛出 RuntimeException 时调用 span.fail，并原样抛出。
5. finally 中始终调用 span.close。

不支持 Mono 或 Flux；响应式子 Span 需要以后另行设计订阅时生命周期，不能复用同步切面。

## 工作流迁移

### EvidenceQualityNode

apply 标记为 LangfuseSpan。入参属性包括 generationId 与候选数；父 carrier 来自状态中的 LANGFUSE_OTEL_CONTEXT_CARRIER；返回属性从 EVIDENCE_QUALITY 对应的 #result Map 项读取接纳数、估算 Token 和充分性。

节点保留 EvidenceQuality 局部变量用于业务日志和状态返回，删除显式 startSpan、addAttributes、fail、close。

### ParentContextExpansionNode

apply 标记为 LangfuseSpan，作为 RAG 证据扩展阶段的子观察。入参属性记录重排序输入候选数，返回属性记录扩展后的候选数；异常不在节点内吞掉，因此切面可以正确标记 ERROR。该节点不调用模型，不需要暴露子 Span Context。

### 暂不迁移的节点

| 节点 | 原因 |
| --- | --- |
| RerankNode | 必须将 span.context 传给模型网关，使 Rerank Generation 成为其子观察。 |
| RetrievalNode | topK 和阈值会在节点内按运行配置补全，但未必写入 workflow state；同步注解只能读取方法入参，迁移会错误上报默认配置。 |
| QuestionRewriteNode、IntentRecognitionNode、AnswerGenerationNode | 都包含模型调用；若没有受控的当前子 Span Context 暴露机制，注解无法保证 Generation 的正确父节点。 |
| ConversationValidationNode | 标题生成运行在独立虚拟线程，不能由同步方法切面覆盖。 |
| SectionExpansionNode | 节点捕获异常并返回初始候选集，直接注解化会将一次真实失败记录为成功；需先定义显式降级状态。 |
| RetrievalFusionNode | 仅执行内存内 RRF 排序，耗时低且其候选数据已由检索与扩展 Span 记录，单独采集会制造噪声。 |
| ConversationContextNode、AssistantMessagePersistenceNode | 属于会话读写而非 RAG 推理关键路径，本次不扩大观测范围。 |

## DataAgent 对比结论

DataAgent 同样使用 OTel OTLP HTTP，而不是官方 Java Langfuse tracing SDK。其 OpenTelemetryConfig 直接创建 exporter 和 tracer；GraphServiceImpl 在流开始时创建一个 graph-stream Span，并在完成或错误时结束。LangfuseService 将 token 按 threadId 放进静态 Map，再在结束时累计到该单一 Span。

该方式适合单一 Graph 流的最低成本观测，但它没有将 Span 设为 current scope，也没有在工作流阶段和模型调用之间传递显式 OTel carrier。因此无法稳定形成 Graph 阶段、Rerank、模型 Generation 的精确父子树。它使用通用 input.value、output.value 属性和单 Span 累计 Token，缺少 NexaRAG 所需的 RAG 语义分段、模型路由、失败/取消终态与 Token 分解指标。

NexaRAG 保留当前 OTel exporter、LangfuseTelemetry 门面和状态 carrier；通过 LangfuseSpan 减少无下游上下文节点的样板代码。该方案的代码量略大，但适合 Reactor、Graph、虚拟线程和多模型调用组成的 RAG 链路。

## 数据流

Chat 根 Trace carrier → 切面解码 → startSpan（类型与入参属性）→ 节点返回状态增量 → SpEL 从 #result 补充属性 → 异常时 fail → 始终 close。

## 测试与验收

1. LangfuseSpanAspectTest 覆盖入参属性、OTel 父上下文、返回属性、异常 fail/close、非法 carrier 和非法 SpEL 的无侵入降级。
2. EvidenceQualityNode 和 ParentContextExpansionNode 的注解声明测试验证观察类型与属性表达式。
3. 既有 EvidenceQualityNode、RetrievalNodeCandidateConfigTest 和 Langfuse OTel 测试继续通过。
4. 人工验证一条聊天 Trace：retrieval、rerank、evidence-selection、parent-context-expansion 都位于 rag.chat 根 Trace 下；Rerank 的模型 Generation 仍在 rerank Span 下。

## 风险与回滚

Spring AOP 只能拦截经 Spring 代理调用的方法。实施时必须通过 Spring 容器集成测试验证 Graph 装配后的 NodeAction.apply 被代理拦截；若不成立，保留显式 Scope，并在 Graph 装配层进行包装。

迁移仅删除 EvidenceQualityNode 的 Scope 样板代码，并为 ParentContextExpansionNode 添加观察，遥测门面和 exporter 不变。若 Trace 层级或属性异常，可仅回滚节点注解迁移。
