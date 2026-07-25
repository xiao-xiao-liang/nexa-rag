# AI 中台前端与会话查询实施计划

> **执行要求：** 必须按任务顺序执行；每项代码修改均先执行失败测试，再实现最小代码并复跑测试。

**目标：** 新增安全、可分页的会话读取接口，并构建默认 RAG 的 React 对话工作台。

**架构：** 通用页码与游标分页对象归属 common。chat 暴露会话查询 REST 接口，boot 保留已有 ChatController。前端使用 Tailwind CSS、shadcn/ui 和 Lucide，先加载持久化会话/历史，再由现有 SSE 追加内容。

**技术栈：** Java 21、Spring Boot 3、MyBatis-Plus、JUnit 5、Mockito、React、Vite、TypeScript、Tailwind CSS、shadcn/ui、Lucide、Vitest、React Testing Library。

---

## 文件结构

- nexa-rag-common/src/main/java/com/nexarag/common/web/PageVO.java：普通页码分页基类。
- nexa-rag-common/src/main/java/com/nexarag/common/web/CursorPageVO.java：普通游标分页基类。
- nexa-rag-chat/src/main/java/com/nexarag/chat/controller/ConversationController.java：会话读取入口。
- nexa-rag-chat/src/main/java/com/nexarag/chat/controller/vo/：会话与消息安全投影、页码/游标领域响应。
- nexa-rag-web/：独立 React + Vite 工程，不写入 Maven 模块。

### 任务 1：提取 common 分页对象并迁移 document

**文件：**
- 新增：nexa-rag-common/src/main/java/com/nexarag/common/web/PageVO.java
- 新增：nexa-rag-common/src/main/java/com/nexarag/common/web/CursorPageVO.java
- 新增：nexa-rag-common/src/test/java/com/nexarag/common/web/PageVOTest.java
- 删除：nexa-rag-document/src/main/java/com/nexarag/document/vo/PageVO.java
- 修改：nexa-rag-document/pom.xml
- 修改：nexa-rag-document/src/main/java/com/nexarag/document/converter/DocumentConverter.java
- 修改：所有导入 com.nexarag.document.vo.PageVO 的 document 源码与测试

- [ ] **步骤 1：写失败测试。**

    @Test
    void shouldStorePageMetadataAndRecords() {
        PageVO<String> page = new PageVO<>();
        page.setRecords(List.of("a"));
        page.setTotal(1L);
        page.setCurrent(1L);
        page.setSize(20L);
        page.setPages(1L);

        assertThat(page.getRecords()).containsExactly("a");
        assertThat(page.getPages()).isEqualTo(1L);
    }

- [ ] **步骤 2：运行失败测试。**

    mvn -pl nexa-rag-common -Dtest=PageVOTest test

预期：因 common 分页类不存在而失败。

- [ ] **步骤 3：实现普通泛型分页类。**

PageVO<T> 使用 Lombok 的 @Data、@NoArgsConstructor、@AllArgsConstructor，字段固定为 List<T> records、long total、long current、long size、long pages。CursorPageVO<T> 使用相同 Lombok 注解，字段固定为 List<T> records、boolean hasMore、Long nextBeforeSequence。不要使用 record，不让 CursorPageVO 继承 PageVO。

document 显式增加对 nexa-rag-common 的直接依赖，迁移所有 PageVO 导入；DocumentConverter 改为 new PageVO<>(records, total, current, size, pages)。

- [ ] **步骤 4：复跑 common 与 document 分页测试。**

    mvn -pl nexa-rag-common,nexa-rag-document -am -Dtest=PageVOTest,DocumentConverterTest,DocumentServiceImplTest "-Dsurefire.failIfNoSpecifiedTests=false" test

预期：测试全部通过。

- [ ] **步骤 5：提交。**

    git add nexa-rag-common nexa-rag-document
    git commit -m "refactor(common): 提取通用分页响应对象"

### 任务 2：实现 chat 历史游标读取与 REST 接口

**文件：**
- 修改：nexa-rag-chat/pom.xml
- 修改：nexa-rag-chat/src/main/java/com/nexarag/chat/service/ConversationMessageService.java
- 修改：nexa-rag-chat/src/main/java/com/nexarag/chat/service/impl/ConversationMessageServiceImpl.java
- 新增：nexa-rag-chat/src/main/java/com/nexarag/chat/controller/ConversationController.java
- 新增：nexa-rag-chat/src/main/java/com/nexarag/chat/controller/vo/ConversationListItemVO.java
- 新增：nexa-rag-chat/src/main/java/com/nexarag/chat/controller/vo/ConversationPageVO.java
- 新增：nexa-rag-chat/src/main/java/com/nexarag/chat/controller/vo/ConversationMessageItemVO.java
- 新增：nexa-rag-chat/src/main/java/com/nexarag/chat/controller/vo/ConversationHistoryPageVO.java
- 修改：nexa-rag-chat/src/test/java/com/nexarag/chat/service/impl/ConversationMessageServiceImplTest.java
- 新增：nexa-rag-chat/src/test/java/com/nexarag/chat/controller/ConversationControllerTest.java

- [ ] **步骤 1：写历史服务失败测试。**

    @Test
    void shouldReturnOlderMessagesInAscendingSequenceWithCursor() {
        when(conversationService.getOwnedConversation("c1", "u1")).thenReturn(conversation("c1", "u1"));
        when(mapper.selectList(any())).thenReturn(List.of(message("m4", 4L), message("m3", 3L), message("m2", 2L)));

        CursorPageVO<ChatMessageVO> result = messageService.pageHistory("c1", "u1", 5L, 2);

        assertThat(result.getRecords()).extracting(ChatMessageVO::sequence).containsExactly(3L, 4L);
        assertThat(result.isHasMore()).isTrue();
        assertThat(result.getNextBeforeSequence()).isEqualTo(3L);
    }

- [ ] **步骤 2：运行失败测试。**

    mvn -pl nexa-rag-chat -am -Dtest=ConversationMessageServiceImplTest#shouldReturnOlderMessagesInAscendingSequenceWithCursor "-Dsurefire.failIfNoSpecifiedTests=false" test

预期：因 pageHistory 不存在而失败。

- [ ] **步骤 3：实现服务、响应 VO 与 Controller。**

chat 增加 spring-boot-starter-web 和 nexa-rag-common 的直接依赖。ConversationMessageService 新增 pageHistory(String conversationId, String userId, Long beforeSequence, int size)，返回 CursorPageVO<ChatMessageVO>；实现按 sequence 倒序读取 size + 1 条、判定 hasMore、保留 size 条并升序返回。

ConversationPageVO 继承 PageVO<ConversationListItemVO>；ConversationHistoryPageVO 继承 CursorPageVO<ConversationMessageItemVO>。两者及列表项/消息项均位于 chat.controller.vo，并有简体中文 Java doc。

ConversationController 使用 @RequestMapping("/api/conversations")。GET /api/conversations 的默认 current=1、size=20，调用 pageByUser；GET /api/conversations/{conversationId}/messages 的默认 size=50、可空 beforeSequence，调用 pageHistory。两者都从 CurrentUserContext 获取用户。外部消息只返回 messageId、sequence、role、status、content、createdTime、updatedTime；不得泄露 userId、thinkingContent、referencesJson、Token 用量和失败详情。

- [ ] **步骤 4：写并运行 Controller 测试。**

    @Test
    void historyShouldPassCurrentUserAndCursorToService() {
        CurrentUserContext.set(new CurrentUser("u1"));
        when(messageService.pageHistory("c1", "u1", 8L, 50)).thenReturn(new CursorPageVO<>(List.of(), false, null));

        controller.history("c1", 8L, 50);

        verify(messageService).pageHistory("c1", "u1", 8L, 50);
    }

    mvn -pl nexa-rag-chat -am -Dtest=ConversationMessageServiceImplTest,ConversationControllerTest "-Dsurefire.failIfNoSpecifiedTests=false" test

预期：服务游标、用户归属和安全投影测试全部通过。

- [ ] **步骤 5：提交。**

    git add nexa-rag-chat
    git commit -m "feat(chat): 新增会话与历史查询接口"

### 任务 3：同步 Apifox 并回归 boot 聊天接口

**文件：**
- 修改：Apifox 项目 nexa-rag 的接口文档。

- [ ] **步骤 1：录入 GET /api/conversations 与 GET /api/conversations/{conversationId}/messages。**

会话列表示例使用 ConversationPageVO；历史消息示例使用 ConversationHistoryPageVO。两个响应均不包含敏感内部字段。

- [ ] **步骤 2：运行后端回归。**

    mvn -pl nexa-rag-boot -am -Dtest=ChatControllerTest "-Dsurefire.failIfNoSpecifiedTests=false" test
    mvn -pl nexa-rag-chat -am -Dtest=ConversationControllerTest "-Dsurefire.failIfNoSpecifiedTests=false" test

预期：ChatController 仍位于 boot 且原有流式测试通过；新查询接口测试通过。

- [ ] **步骤 3：提交文档同步记录。**

    git commit --allow-empty -m "docs(chat): 同步会话查询接口文档"

### 任务 4：初始化 React、设计令牌与组件基础

**文件：**
- 新增：nexa-rag-web/package.json、vite.config.ts、tailwind.config.ts、src/app/main.tsx、src/app/App.tsx、src/app/globals.css
- 新增：nexa-rag-web/src/components/ui/：shadcn/ui 的 button、textarea、scroll-area、tooltip、dropdown-menu、collapsible、skeleton、sheet、dialog
- 新增：nexa-rag-web/src/api/types.ts、client.ts、conversation-api.ts、conversation-api.test.ts

- [ ] **步骤 1：创建工程、Tailwind、shadcn/ui、Lucide 与测试依赖。**

    npm create vite@latest nexa-rag-web -- --template react-ts
    npm --prefix nexa-rag-web install
    npm --prefix nexa-rag-web install tailwindcss @tailwindcss/vite lucide-react clsx tailwind-merge class-variance-authority
    npm --prefix nexa-rag-web install -D vitest jsdom @testing-library/react @testing-library/jest-dom @testing-library/user-event

初始化 shadcn/ui，并只添加上述九个组件；不引入 Ant Design。

- [ ] **步骤 2：写 API 失败测试并实现读取层。**

    it("读取历史时把游标编码为查询参数", async () => {
      global.fetch = vi.fn().mockResolvedValue(jsonResponse({ code: "0", data: { records: [], hasMore: false, nextBeforeSequence: null } }))

      await getConversationHistory("c1", 8, 50)

      expect(global.fetch).toHaveBeenCalledWith("/api/conversations/c1/messages?beforeSequence=8&size=50", expect.any(Object))
    })

types.ts 定义 ApiResult、ConversationListItem、ConversationMessage、PageResult、CursorPage 与 AgentDefinition。client.ts 在 HTTP 非 2xx 或 code !== "0" 时抛出 ApiError；conversation-api.ts 用 URLSearchParams 调用两个读取接口。

- [ ] **步骤 3：运行前端基础验证并提交。**

    npm --prefix nexa-rag-web run test -- --run src/api/conversation-api.test.ts
    npm --prefix nexa-rag-web run build
    git add nexa-rag-web
    git commit -m "feat(agent-ui): 初始化 RAG 对话前端工程"

### 任务 5：实现 A 风格的双栏对话工作台

**文件：**
- 新增：nexa-rag-web/src/api/chat-stream.ts、chat-stream.test.ts
- 新增：nexa-rag-web/src/features/chat/ChatWorkspace.tsx、ConversationSidebar.tsx、MessageTimeline.tsx、StatusBlock.tsx、Composer.tsx、chat-workspace.test.tsx

- [ ] **步骤 1：写 SSE 失败测试。**

    it("将 META、TOKEN、CANCELLED 标准化", () => {
      expect(parseSseFrames("event: META\ndata: {\"conversationId\":\"c1\",\"generationId\":\"g1\"}\n\nevent: TOKEN\ndata: {\"content\":\"你\"}\n\nevent: CANCELLED\ndata: {}\n\n"))
        .toEqual([{ type: "meta", conversationId: "c1", generationId: "g1" }, { type: "token", content: "你" }, { type: "cancelled" }])
    })

- [ ] **步骤 2：实现最小流式与界面。**

chat-stream.ts 通过 POST /api/chat/stream 读取 SSE 并映射 META、TOKEN、COMPLETE、ERROR、CANCELLED；停止调用 DELETE /api/chat/generations/{generationId}。

工作台使用亮色语义令牌：白色表面、浅灰背景、蓝色焦点边框、12–16 像素圆角、4/8 像素间距节奏和 Lucide 图标。ConversationSidebar 使用 ScrollArea；Composer 使用 Textarea、Button 与 Tooltip，默认 RAG 且不显示 RAG 标签；MessageTimeline 按 sequence 去重并支持“加载更早消息”；状态使用 Collapsible 的 StatusBlock。所有图标按钮添加 aria-label、44×44 点击区和可见 focus-ring。首期不渲染引用块，未来 Agent 显示“暂未开放”且不得发请求。

- [ ] **步骤 3：补齐组件测试、构建与提交。**

增加默认 RAG 发送、上拉历史、停止保留内容、未来 Agent 不发请求、错误重试入口和键盘可达性测试。

    npm --prefix nexa-rag-web run test -- --run
    npm --prefix nexa-rag-web run build
    git add nexa-rag-web/src
    git commit -m "feat(agent-ui): 实现 RAG 对话工作台"

### 任务 6：联调与发布前验证

**文件：**
- 修改：README.md、nexa-rag-web/vite.config.ts

- [ ] **步骤 1：配置代理和说明。**

仅将 /api 代理至 VITE_API_TARGET，默认 http://localhost:8080；README 写明后端启动、前端启动、测试与构建命令。

- [ ] **步骤 2：完成手工主链路。**

验证：新建会话 → META 与流式文本 → 停止并保留部分内容 → 刷新恢复 → 上拉历史 → 切换会话 → 跨用户读取被拒绝。桌面宽度与 375 像素宽度均无水平滚动，键盘可到达所有主操作。

- [ ] **步骤 3：运行完整验证并提交。**

    mvn -pl nexa-rag-boot -am -Dtest=ChatControllerTest "-Dsurefire.failIfNoSpecifiedTests=false" test
    mvn -pl nexa-rag-chat -am -Dtest=ConversationControllerTest "-Dsurefire.failIfNoSpecifiedTests=false" test
    npm --prefix nexa-rag-web run test -- --run
    npm --prefix nexa-rag-web run build
    git add README.md nexa-rag-web/vite.config.ts
    git commit -m "docs(agent-ui): 补充前端联调说明"
