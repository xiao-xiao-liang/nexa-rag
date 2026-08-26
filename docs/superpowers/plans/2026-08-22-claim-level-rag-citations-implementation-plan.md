# 结论级 RAG 引用 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让知识库回答的每个可核验结论以可点击 `[n]` 关联到已授权的文档分块预览，并在流式、断线恢复和历史消息中一致工作。

**Architecture:** 工作流仅把 `acceptedEvidenceResults` 映射为不可变引用清单，先推送 `CITATIONS` SSE 再开始同一次 Markdown 模型流。聊天领域将引用清单以版本化 JSON 存入既有 `chat_message.references_json`；历史与流式接口只公开编号，点击时由专用接口按消息、编号重新鉴权并查询当前文档。前端仅把当前消息清单中存在的 `[n]` 渲染为引用按钮。

**Tech Stack:** Java 21、Spring Boot、Spring WebFlux SSE、MyBatis-Plus、Jackson、MySQL/Flyway、Redis、React、TypeScript、Vitest、React Testing Library。

**提交说明：** 当前工作区已有未提交修改，且尚未获得提交授权；本计划不包含 `git commit` 步骤。

---

## 文件结构

| 路径 | 职责 |
| --- | --- |
| `nexa-rag-chat/.../domain/ChatCitationDTO.java` | 持久化 JSON 与工作流之间的单条引用定位数据。 |
| `nexa-rag-chat/.../domain/ChatCitationSetDTO.java` | 带版本号的引用清单 JSON 根对象。 |
| `nexa-rag-chat/.../domain/ChatCitationSummaryVO.java` | 历史与 SSE 可公开的最小编号投影。 |
| `nexa-rag-chat/.../domain/ChatCitationDetailVO.java` | 引用详情接口的可访问、失效、无权状态及预览数据。 |
| `nexa-rag-chat/.../service/ConversationCitationService.java` | 清单序列化、反序列化、消息归属校验和编号查询。 |
| `nexa-rag-chat/.../service/impl/ConversationCitationServiceImpl.java` | 上述服务实现；不查询文档正文。 |
| `nexa-rag-workflow/.../CitationSetFactory.java` | 从已接纳 `RetrievalChunk` 生成稳定编号。 |
| `nexa-rag-workflow/.../ChatStreamEvent*.java` | 新增 `CITATIONS` 类型及 `citations` 事件字段。 |
| `nexa-rag-workflow/.../AnswerGenerationNode.java` | 生成清单、编号化证据、正文前发布 `CITATIONS`。 |
| `nexa-rag-workflow/.../AssistantMessagePersistenceNode.java` | 所有终态持久化引用 JSON 并重带公开投影。 |
| `nexa-rag-chat/.../ConversationController.java` | 历史消息投影加入 `citations`。 |
| `nexa-rag-boot/.../ChatCitationController.java` | `messageId + citationId` 的重新鉴权详情接口。 |
| `nexa-rag-boot/.../V23__add_claim_level_citation_prompt.sql` | 以不可变 Prompt 版本和发布记录升级回答系统提示词。 |
| `nexa-rag-studio/src/types/index.ts` | 引用 DTO、事件和历史消息类型。 |
| `nexa-rag-studio/src/lib/api.ts` | 引用详情请求。 |
| `nexa-rag-studio/src/components/chat/markdown/FeishuMarkdown.tsx` | 安全渲染有效引用按钮与未引用提示。 |
| `nexa-rag-studio/src/components/chat/ChatCitationPopover.tsx` | 按需加载详情并显示弹层状态。 |
| `nexa-rag-studio/src/components/chat/ChatMessageItem.tsx` | 绑定当前消息引用清单与弹层。 |
| `nexa-rag-studio/src/features/chat/ChatPage.tsx` | 接收、缓存和恢复 `CITATIONS`。 |

### Task 1: 建立引用领域契约和聊天清单服务

**Files:**

- Create: `nexa-rag-chat/src/main/java/com/nexarag/chat/domain/ChatCitationDTO.java`
- Create: `nexa-rag-chat/src/main/java/com/nexarag/chat/domain/ChatCitationSetDTO.java`
- Create: `nexa-rag-chat/src/main/java/com/nexarag/chat/domain/ChatCitationSummaryVO.java`
- Create: `nexa-rag-chat/src/main/java/com/nexarag/chat/service/ConversationCitationService.java`
- Create: `nexa-rag-chat/src/main/java/com/nexarag/chat/service/impl/ConversationCitationServiceImpl.java`
- Test: `nexa-rag-chat/src/test/java/com/nexarag/chat/service/impl/ConversationCitationServiceImplTest.java`

- [ ] **Step 1: 写入清单版本、空值兼容和消息归属的失败测试。**

```java
@Test
void shouldDeserializeVersionOneCitationSetAndExposeOnlyCitationId() {
    String json = "{\"version\":1,\"citations\":[{\"citationId\":1,\"documentId\":20,\"chunkId\":\"c1\"}]}";
    when(messageService.getOwnedAssistantMessage("m1", "u1")).thenReturn(assistant("m1", json));

    assertThat(service.listSummaries("m1", "u1"))
            .containsExactly(new ChatCitationSummaryVO(1));
}

@Test
void shouldTreatBlankOrUnknownCitationSetAsEmpty() {
    when(messageService.getOwnedAssistantMessage("m1", "u1")).thenReturn(assistant("m1", ""));
    assertThat(service.listSummaries("m1", "u1")).isEmpty();
    when(messageService.getOwnedAssistantMessage("m1", "u1"))
            .thenReturn(assistant("m1", "{\"version\":2,\"citations\":[]}"));
    assertThat(service.listSummaries("m1", "u1")).isEmpty();
}

@Test
void shouldPropagateOwnershipFailureBeforeReadingCitationJson() {
    ClientException denied = new ClientException("消息不存在或无权访问");
    when(messageService.getOwnedAssistantMessage("m1", "u2")).thenThrow(denied);
    assertThatThrownBy(() -> service.getOwnedCitation("m1", "u2", 1)).isSameAs(denied);
}
```

- [ ] **Step 2: 运行失败测试。**

Run: `mvn -pl nexa-rag-chat -Dtest=ConversationCitationServiceImplTest test`

Expected: FAIL，提示 `ConversationCitationServiceImpl` 或引用数据对象不存在。

- [ ] **Step 3: 添加最小的显式分层对象和服务实现。**

`ChatCitationDTO` 使用 Java `record`，字段固定为 `citationId`、`documentId`、`chunkId`、`chunkOrder`、`title`、`sectionId`、`rank`、`score`、`channel`；`ChatCitationSetDTO` 固定为 `int version, List<ChatCitationDTO> citations`；`ChatCitationSummaryVO` 只包含 `int citationId`。`ConversationCitationService` 必须提供 `serialize(List<ChatCitationDTO>)`、`listSummaries(messageId, userId)` 与 `getOwnedCitation(messageId, userId, citationId)`，其中后两者先通过消息服务确认助手消息归属和角色，再由 Jackson 解析 `referencesJson`。

```java
public ChatCitationDTO getOwnedCitation(String messageId, String userId, int citationId) {
    return citationSet(messageId, userId).citations().stream()
            .filter(citation -> citation.citationId() == citationId)
            .findFirst()
            .orElseThrow(() -> new ClientException("引用不存在或已失效"));
}
```

未知版本、重复或小于 1 的编号必须按不可用处理，绝不从 JSON 向前端回传内部标识。

- [ ] **Step 4: 运行通过测试。**

Run: `mvn -pl nexa-rag-chat -Dtest=ConversationCitationServiceImplTest test`

Expected: PASS。

### Task 2: 将已接纳证据变为引用清单并先于正文发布 SSE

**Files:**

- Create: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/citation/CitationSetFactory.java`
- Modify: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/constants/ChatWorkflowStateKeys.java`
- Modify: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/stream/ChatStreamEventType.java`
- Modify: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/stream/ChatStreamEvent.java`
- Modify: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/node/chat/AnswerGenerationNode.java`
- Modify: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/node/chat/AssistantMessagePersistenceNode.java`
- Modify: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/stream/ChatWorkflowStreamingUtil.java`
- Test: `nexa-rag-workflow/src/test/java/com/nexarag/workflow/citation/CitationSetFactoryTest.java`
- Test: `nexa-rag-workflow/src/test/java/com/nexarag/workflow/stream/ChatWorkflowStreamingUtilTest.java`
- Test: `nexa-rag-workflow/src/test/java/com/nexarag/workflow/node/chat/AnswerGenerationNodeTest.java`

- [ ] **Step 1: 写入引用编号稳定性与事件时序的失败测试。**

```java
@Test
void shouldNumberAcceptedChunksInInputOrderWithoutContent() {
    List<ChatCitationDTO> citations = factory.create(List.of(chunk("c2", 20L), chunk("c1", 10L)));
    assertThat(citations).extracting(ChatCitationDTO::citationId).containsExactly(1, 2);
    assertThat(citations).allSatisfy(citation -> assertThat(citation).hasNoNullFieldsOrPropertiesExcept("sectionId"));
}

@Test
void shouldPublishCitationsBeforeFirstAnswerDelta() {
    StepVerifier.create(answerNode.apply(stateWithAcceptedChunks()).get(MODEL_STREAM_RESULT))
            .assertNext(event -> assertThat(event.type()).isEqualTo(ChatStreamEventType.CITATIONS))
            .assertNext(event -> assertThat(event.type()).isEqualTo(ChatStreamEventType.ANSWER_DELTA))
            .verifyComplete();
}
```

- [ ] **Step 2: 运行失败测试。**

Run: `mvn -pl nexa-rag-workflow -Dtest=CitationSetFactoryTest,AnswerGenerationNodeTest,ChatWorkflowStreamingUtilTest test`

Expected: FAIL，当前没有 `CITATIONS` 类型、引用状态键或工厂。

- [ ] **Step 3: 实现不可变清单、状态键和事件字段。**

在状态键中新增 `CITATION_SET`；`CitationSetFactory` 只接收 `ACCEPTED_EVIDENCE_RESULTS`，按列表顺序映射为 `ChatCitationDTO`，不复制 `RetrievalChunk.content()`。`ChatStreamEvent` 增加不可变 `List<ChatCitationSummaryVO> citations`，所有已有构造器默认 `List.of()`，并让 `withEventVersion`、`ChatController.enrichLegacyEvent`、Redis 反序列化和终态事件完整保留该字段。枚举新增 `CITATIONS`。

`AnswerGenerationNode` 的顺序必须是：创建清单并写入状态 → 发布 `CITATIONS` → 调用 `modelGateway.streamChat`。证据字符串改为 `【证据 n】` 加正文，工具失败摘要保持原有后缀。`AssistantMessagePersistenceNode` 从状态读取清单，调用 `ConversationCitationService.serialize` 并传入成功、取消、失败的消息最终化方法；终态事件重带 `citationId` 投影。

- [ ] **Step 4: 扩展 Redis/SSE 重放回归测试。**

在 `RedisChatStreamEventBufferTest` 断言 `CITATIONS` 序列化后仍带相同编号；在 `ChatControllerTest` 断言 SSE 的 `event:CITATIONS` 在第一条 `ANSWER_DELTA` 前，并且恢复接口按 `eventVersion` 不重复发送。

- [ ] **Step 5: 运行后端工作流与控制器测试。**

Run: `mvn -pl nexa-rag-workflow,nexa-rag-boot -am -Dtest=CitationSetFactoryTest,AnswerGenerationNodeTest,ChatWorkflowStreamingUtilTest,RedisChatStreamEventBufferTest,ChatControllerTest test`

Expected: PASS。

### Task 3: 投影历史引用并实现重新鉴权的详情接口

**Files:**

- Modify: `nexa-rag-chat/src/main/java/com/nexarag/chat/domain/ConversationMessageItemVO.java`
- Modify: `nexa-rag-chat/src/main/java/com/nexarag/chat/controller/ConversationController.java`
- Modify: `nexa-rag-chat/src/main/java/com/nexarag/chat/service/ConversationMessageService.java`
- Modify: `nexa-rag-chat/src/main/java/com/nexarag/chat/service/impl/ConversationMessageServiceImpl.java`
- Create: `nexa-rag-boot/src/main/java/com/nexarag/boot/controller/ChatCitationController.java`
- Create: `nexa-rag-boot/src/main/java/com/nexarag/boot/controller/vo/ChatCitationDetailVO.java`
- Test: `nexa-rag-chat/src/test/java/com/nexarag/chat/controller/ConversationControllerWebTest.java`
- Test: `nexa-rag-boot/src/test/java/com/nexarag/boot/controller/ChatCitationControllerTest.java`

- [ ] **Step 1: 写入历史投影与详情状态的失败测试。**

```java
@Test
void shouldReturnOnlyCitationIdsInConversationHistory() {
    webTestClient.get().uri("/api/conversations/c1/messages")
            .exchange().expectStatus().isOk()
            .expectBody().jsonPath("$.data.records[1].citations[0].citationId").isEqualTo(1)
            .jsonPath("$.data.records[1].referencesJson").doesNotExist()
            .jsonPath("$.data.records[1].citations[0].documentId").doesNotExist();
}

@Test
void shouldReturnExpiredWhenReferencedChunkNoLongerExists() {
    when(documentChunkService.getById("c1")).thenReturn(null);
    webTestClient.get().uri("/api/chat/messages/m1/citations/1")
            .exchange().expectStatus().isOk()
            .expectBody().jsonPath("$.data.status").isEqualTo("EXPIRED")
            .jsonPath("$.data.excerpt").doesNotExist();
}

@Test
void shouldNotRevealCitationWhenCurrentKnowledgeBasePermissionIsRevoked() {
    when(knowledgeBaseService.getRequiredDocument(10L, 20L))
            .thenThrow(new ClientException("无权访问知识库"));
    webTestClient.get().uri("/api/chat/messages/m1/citations/1")
            .exchange().expectStatus().isOk()
            .expectBody().jsonPath("$.data.status").isEqualTo("FORBIDDEN")
            .jsonPath("$.data.excerpt").doesNotExist()
            .jsonPath("$.data.openDocument").doesNotExist();
}
```

- [ ] **Step 2: 运行失败测试。**

Run: `mvn -pl nexa-rag-chat,nexa-rag-boot -am -Dtest=ConversationControllerWebTest,ChatCitationControllerTest test`

Expected: FAIL，历史没有 `citations`，详情路由不存在。

- [ ] **Step 3: 实现最小公开历史投影。**

`ConversationMessageItemVO` 新增 `List<ChatCitationSummaryVO> citations`；`ConversationController.toMessageItem` 调用 `ConversationCitationService.listSummaries(messageId, userId)`。保留数据库领域对象的 `referencesJson`，但不得复制到 `ConversationMessageItemVO`。

- [ ] **Step 4: 实现详情控制器的权限顺序。**

`ChatCitationController` 路由固定为 `GET /api/chat/messages/{messageId}/citations/{citationId}`。控制器只读取 `UserContext` 并委托服务；服务依次执行：确认消息归属和助手角色 → 从该消息清单找编号 → 由 `documentId` 查询当前文档并取得知识库 ID → `knowledgeBaseService.getRequiredDocument(knowledgeBaseId, documentId)` → `documentChunkService.getById(chunkId)` 且验证 `chunk.documentId` 一致 → 组装 VO。

```java
if (chunk == null || !citation.documentId().equals(chunk.getDocumentId())) {
    return ChatCitationDetailVO.expired();
}
return ChatCitationDetailVO.available(document.getTitle(), sectionTitle, chunk.getText(), openDocument);
```

将当前无权限的文档访问转换为 `FORBIDDEN` 业务状态，其他非业务异常仍抛出；`AVAILABLE` 不含 `knowledgeBaseId`、`documentId`、`chunkId`、对象名或原始 URL。站内文档返回 `IN_APP_PREVIEW` 与服务端路由目标；外部文档仅返回服务端校验过的受控跳转地址。

- [ ] **Step 5: 运行聊天与 Boot 相关测试。**

Run: `mvn -pl nexa-rag-chat,nexa-rag-boot -am -Dtest=ConversationControllerTest,ConversationControllerWebTest,ConversationMessageServiceImplTest,ChatCitationControllerTest test`

Expected: PASS。

### Task 4: 升级回答提示词并锁定引用输出约束

**Files:**

- Create: `nexa-rag-boot/src/main/resources/db/migration/V23__add_claim_level_citation_prompt.sql`
- Modify: `nexa-rag-boot/src/main/resources/db/schema/nexa_rag_schema.sql`
- Test: `nexa-rag-model/src/test/java/com/nexarag/model/toolkits/prompt/PromptBuilderTest.java`
- Test: `nexa-rag-boot/src/test/java/com/nexarag/boot/migration/PromptCitationMigrationTest.java`

- [ ] **Step 1: 写入 Prompt 渲染和迁移的失败测试。**

```java
@Test
void shouldKeepEvidenceNumberAndCitationInstructionInAnswerPrompt() {
    List<ChatModelMessage> messages = promptBuilder.buildAnswerMessages(snapshot, "问题", "", List.of(), "【证据 1】文本");
    assertThat(messages.getFirst().content()).contains("[n]", "【未提供引用】");
    assertThat(messages).anySatisfy(message -> assertThat(message.content()).contains("【证据 1】文本"));
}
```

- [ ] **Step 2: 运行失败测试。**

Run: `mvn -pl nexa-rag-model,nexa-rag-boot -am -Dtest=PromptBuilderTest,PromptCitationMigrationTest test`

Expected: FAIL，活动回答 Prompt 尚不含引用输出约束。

- [ ] **Step 3: 新建只升级 `chat.answer.system-instruction` 的 Flyway 迁移。**

迁移复用 V15 的“锁定义 → 计算下一 `version_no` → 插入 `prompt_version` → 插入 `prompt_release` → 切换 `prompt_definition.current_release_id`”流程，但临时表只包含 `chat.answer.system-instruction`。新正文完整保留现有四项执行要求和 Few-shot，再追加：可核验结论紧随已提供范围内的 `[n]`；无编号结论紧随 `【未提供引用】`；禁止编造编号、标题、URL；不得输出参考列表、代码块或普通方括号中的伪引用。同步把该新默认正文更新到初始化 schema 的 Prompt 种子。

- [ ] **Step 4: 运行 Prompt 与迁移测试。**

Run: `mvn -pl nexa-rag-model,nexa-rag-boot -am -Dtest=PromptBuilderTest,PromptCitationMigrationTest test`

Expected: PASS。

### Task 5: 前端接收引用清单并安全渲染弹层

**Files:**

- Modify: `nexa-rag-studio/src/types/index.ts`
- Modify: `nexa-rag-studio/src/lib/chat-stream-event.ts`
- Modify: `nexa-rag-studio/src/lib/api.ts`
- Modify: `nexa-rag-studio/src/features/chat/ChatPage.tsx`
- Modify: `nexa-rag-studio/src/components/chat/ChatMessageItem.tsx`
- Modify: `nexa-rag-studio/src/components/chat/markdown/FeishuMarkdown.tsx`
- Create: `nexa-rag-studio/src/components/chat/ChatCitationPopover.tsx`
- Create: `nexa-rag-studio/src/components/chat/citation-markers.ts`
- Test: `nexa-rag-studio/src/lib/chat-stream-event.test.ts`
- Test: `nexa-rag-studio/src/components/chat/citation-markers.test.ts`
- Test: `nexa-rag-studio/src/components/chat/ChatCitationPopover.test.tsx`

- [ ] **Step 1: 写入合法、非法、代码块和未引用标记的失败测试。**

```ts
it("only turns a citation in the current message set into an action", () => {
  expect(parseCitationMarkers("结论[1]，普通[9]", new Set([1]))).toEqual([
    { type: "text", value: "结论" },
    { type: "citation", citationId: 1 },
    { type: "text", value: "，普通[9]" },
  ]);
});

it("does not parse markdown code fences and preserves unattributed markers", () => {
  expect(renderedText("`[1]`\n【未提供引用】", new Set([1]))).toContain("【未提供引用】");
});
```

- [ ] **Step 2: 运行失败测试。**

Run: `npm --prefix nexa-rag-studio test -- citation-markers chat-stream-event`

Expected: FAIL，尚无引用标记解析器和 `CITATIONS` 类型。

- [ ] **Step 3: 扩展 TypeScript 事件和页面状态。**

新增 `ChatCitationSummaryVO`、`ChatCitationDetailVO`、`ChatCitationDetailStatus`，将 `CITATIONS` 加入 `ChatStreamEventType`，并为事件和 `ChatMessageVO` 增加 `citations?: ChatCitationSummaryVO[]`。`ChatPage` 在收到 `CITATIONS` 时只更新目标助手消息和当前会话的内存历史缓存；终态事件用同一字段对账；历史加载从 `message.citations` 恢复。不得使用或解析 `referencesJson`。

- [ ] **Step 4: 实现 Markdown 安全节点与弹层。**

`citation-markers.ts` 仅在 Markdown 渲染产生的普通文本节点中识别 `/\[(\d+)]/`；`citationId` 必须存在于当前消息集合。代码块、链接、图片 URL 和普通未知方括号不处理。`【未提供引用】` 渲染为不可点击提示；未知数字编号同样显示为“未提供引用”，不发请求。

`ChatCitationPopover` 在按钮点击时调用 `chatApi.getCitationDetail(messageId, citationId)`，并以 `AVAILABLE`、`EXPIRED`、`FORBIDDEN`、请求失败分别显示弹层；只有 `AVAILABLE` 显示摘录和服务端下发的打开动作。再次点击不同编号取消前一请求并替换内容，Esc 与焦点移出关闭。

- [ ] **Step 5: 运行前端相关测试与构建。**

Run: `npm --prefix nexa-rag-studio test -- citation-markers chat-stream-event ChatCitationPopover`

Expected: PASS。

Run: `npm --prefix nexa-rag-studio run build`

Expected: PASS，TypeScript 无未使用字段或事件联合类型错误。

### Task 6: 端到端契约回归、文档同步与最终验证

**Files:**

- Modify: `README.md`
- Modify: `TODO.md`
- Modify: `docs/glossary/chat-generation.md`
- Modify: `docs/adr/2026-08-22-server-validated-claim-level-citations.md`
- Modify: `docs/superpowers/specs/2026-08-22-claim-level-rag-citations-design.md`
- Test: `nexa-rag-boot/src/test/java/com/nexarag/boot/controller/ChatControllerTest.java`
- Test: `nexa-rag-chat/src/test/java/com/nexarag/chat/controller/ConversationControllerWebTest.java`

- [ ] **Step 1: 写入跨层回归场景。**

覆盖“`CITATIONS` 早于首正文 → `[1]` 可解析 → 终态清单一致 → 历史只返回编号 → 详情重新鉴权”的成功路径，以及“空证据、无效编号、取消后的部分回答、分块删除、权限撤回、断线按版本重放”的失败与降级路径。每个场景断言不含 `chunkId`、`documentId`、原始 URL 或分块正文的未授权泄露。

- [ ] **Step 2: 更新产品文档。**

从 README 删除“引用 SSE 与历史契约尚未补齐”的现状说明；将 TODO 对应项标为完成；保持 ADR、术语表和设计文档与实现字段、事件名、详情路由一致。不得修改与引用无关的未提交文档内容。

- [ ] **Step 3: 运行最小回归集。**

Run: `mvn -pl nexa-rag-chat,nexa-rag-workflow,nexa-rag-boot -am test`

Expected: PASS。

Run: `npm --prefix nexa-rag-studio test && npm --prefix nexa-rag-studio run build`

Expected: PASS。

- [ ] **Step 4: 执行收尾检查。**

Run: `git diff --check`

Expected: 本任务新增或修改文件无空白错误；若工作区原有文件仍报错，记录其路径并不修改。

Run: `git status --short`

Expected: 仅本任务相关文件进入待交付清单，既有用户修改保持原样。

## 自检

- 证据来源、单次 Markdown 流、`CITATIONS` 时序、终态与重放、JSON 存储、历史投影、重新鉴权详情、弹层交互、失效与无权状态、提示词、测试和文档均有对应任务。
- 新对象统一使用 `DTO`、`VO` 后缀；Controller 仅接收请求与返回投影，清单解析和文档查询编排留在 Service。
- 计划不引入新表、正文快照、第二次模型调用或启发式句子切分。
