# 模型治理、Chat 接口与临时管理页 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 补齐模型模块裸 Chat 同步/流式接口、模型治理配置、fallback、权重路由和临时 HTML 管理页。

**Architecture:** `ModelGateway` 继续作为模型模块统一入口，新增同步 Chat Controller 和流式 Chat Controller。模型路由从单个 `ModelRouteDecision` 升级为 `ModelRoutePlan` 候选链，由 `ModelExecutionTemplate` 逐个尝试候选，并通过 `ModelGovernanceExecutor` 执行 Resilience4j RateLimiter、Bulkhead、CircuitBreaker、Retry 保护。治理参数存入 `model_governance_config`，临时 HTML 管理页调用现有 `/api/model/...` 与新增 Chat/Governance 接口。

**Tech Stack:** Java 21、Spring Boot 3.5、Spring WebFlux `Flux`、MyBatis-Plus、Flyway、Resilience4j、Spring AI OpenAI、JUnit 5、Mockito、AssertJ、原生 HTML/CSS/JavaScript。

---

## 当前工作区注意事项

当前 `master` 已存在其他会话留下的未提交改动：

- `nexa-rag-boot/src/main/resources/application.yml`
- `nexa-rag-boot/src/test/java/com/nexarag/boot/NexaRagApplicationConfigurationTest.java`
- `nexa-rag-model/pom.xml`
- `nexa-rag-model/src/main/java/com/nexarag/model/refresh/DefaultModelRegistryChangePublisher.java`
- `nexa-rag-model/src/test/java/com/nexarag/model/refresh/ModelRegistryChangePublisherTest.java`
- `nexa-rag-model/src/main/java/com/nexarag/model/refresh/redis/`
- `nexa-rag-model/src/test/java/com/nexarag/model/refresh/redis/`
- `dashscope_api_bytecode.txt`

执行本计划前先确认是否接管这些改动。若不接管，不要暂存或覆盖它们。每次提交只 stage 当前任务涉及文件。

---

## 文件结构

### Chat 同步/流式接口

- Create: `nexa-rag-model/src/main/java/com/nexarag/model/dto/ModelChatRequest.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/dto/ModelChatStreamResponse.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/controller/ModelChatController.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/gateway/ModelGateway.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/provider/ModelProviderAdapter.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/provider/ModelProviderDispatcher.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/provider/ChatProvider.java`
- Test: `nexa-rag-model/src/test/java/com/nexarag/model/controller/ModelChatControllerTest.java`
- Test: `nexa-rag-model/src/test/java/com/nexarag/model/provider/ChatProviderStreamTest.java`

### 治理配置

- Create: `nexa-rag-boot/src/main/resources/db/migration/V8__add_model_governance_config.sql`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/entity/ModelGovernanceConfig.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/mapper/ModelGovernanceConfigMapper.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/dto/ModelGovernanceConfigRequest.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/dto/ModelGovernanceConfigResponse.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/service/ModelGovernanceConfigService.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/service/impl/ModelGovernanceConfigServiceImpl.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/controller/ModelConfigController.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/dto/ModelConfigResponse.java`
- Test: `nexa-rag-model/src/test/java/com/nexarag/model/service/impl/ModelGovernanceConfigServiceImplTest.java`
- Test: `nexa-rag-model/src/test/java/com/nexarag/model/controller/ModelGovernanceConfigControllerTest.java`

### 路由候选链、fallback、治理执行器

- Create: `nexa-rag-model/src/main/java/com/nexarag/model/route/ModelRoutePlan.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/route/WeightedModelRouteSelector.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/route/ModelRouter.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/route/PrimaryFallbackModelRouter.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/enums/ModelRouteStrategy.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/execution/ModelExecutionTemplate.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/governance/ModelGovernanceSettings.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/governance/ModelGovernanceResolver.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/governance/ModelGovernanceExecutor.java`
- Test: `nexa-rag-model/src/test/java/com/nexarag/model/route/WeightedModelRouteSelectorTest.java`
- Test: `nexa-rag-model/src/test/java/com/nexarag/model/execution/ModelExecutionTemplateFallbackTest.java`
- Test: `nexa-rag-model/src/test/java/com/nexarag/model/governance/ModelGovernanceExecutorTest.java`

### 调用日志增强

- Create: `nexa-rag-boot/src/main/resources/db/migration/V9__extend_model_call_log_fallback.sql`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/entity/ModelCallLog.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/service/ModelCallLogService.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/service/impl/ModelCallLogServiceImpl.java`
- Test: `nexa-rag-model/src/test/java/com/nexarag/model/service/impl/ModelCallLogServiceImplTest.java`

### 临时 HTML 管理页

- Create: `nexa-rag-boot/src/main/resources/static/model-admin.html`
- Test: `nexa-rag-boot/src/test/java/com/nexarag/boot/staticresource/ModelAdminStaticResourceTest.java`

### TODO 与验证

- Modify: `TODO.md`
- Run: `mvn -pl nexa-rag-model -am test`
- Run: `mvn -pl nexa-rag-boot -am test -Dtest=ModuleDependencyTest "-Dsurefire.failIfNoSpecifiedTests=false"`
- Run: `mvn clean test`

---

## Task 1: 裸 Chat 同步接口

**Files:**
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/dto/ModelChatRequest.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/controller/ModelChatController.java`
- Test: `nexa-rag-model/src/test/java/com/nexarag/model/controller/ModelChatControllerTest.java`

- [ ] **Step 1: 写同步 Chat Controller 失败测试**

Create `nexa-rag-model/src/test/java/com/nexarag/model/controller/ModelChatControllerTest.java`:

```java
package com.nexarag.model.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.model.ModelTestApplication;
import com.nexarag.model.gateway.ModelGateway;
import com.nexarag.model.gateway.chat.ChatModelResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 模型 Chat Controller 测试。
 */
@SpringBootTest(classes = ModelTestApplication.class)
@AutoConfigureMockMvc
class ModelChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ModelGateway modelGateway;

    @Test
    void chatShouldCallModelGateway() throws Exception {
        when(modelGateway.chat(argThat(request -> "chat.default".equals(request.routeKey()))))
                .thenReturn(ChatModelResponse.builder()
                        .content("你好，我是 NexaRAG")
                        .modelProfile("chat-primary")
                        .promptTokens(3)
                        .completionTokens(6)
                        .totalTokens(9)
                        .build());

        mockMvc.perform(post("/api/model/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "routeKey", "chat.default",
                                "messages", List.of(Map.of("role", "USER", "content", "你好")),
                                "options", Map.of()
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("你好，我是 NexaRAG"))
                .andExpect(jsonPath("$.data.totalTokens").value(9));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
mvn -pl nexa-rag-model -am test -Dtest=ModelChatControllerTest "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: FAIL，提示 `ModelChatController` 或 `/api/model/chat` 不存在。

- [ ] **Step 3: 新增请求 DTO**

Create `nexa-rag-model/src/main/java/com/nexarag/model/dto/ModelChatRequest.java`:

```java
package com.nexarag.model.dto;

import com.nexarag.model.gateway.chat.ChatModelRequest.ChatMessage;

import java.util.List;
import java.util.Map;

/**
 * 裸 Chat 调用请求，用于模型模块直接验证 Chat 模型能力。
 *
 * @param routeKey 模型路由 Key
 * @param messages 聊天消息列表
 * @param options  调用选项
 */
public record ModelChatRequest(String routeKey, List<ChatMessage> messages, Map<String, Object> options) {
}
```

- [ ] **Step 4: 新增同步 Chat Controller**

Create `nexa-rag-model/src/main/java/com/nexarag/model/controller/ModelChatController.java`:

```java
package com.nexarag.model.controller;

import com.nexarag.common.web.Result;
import com.nexarag.common.web.Results;
import com.nexarag.model.dto.ModelChatRequest;
import com.nexarag.model.enums.ModelBizType;
import com.nexarag.model.gateway.ModelGateway;
import com.nexarag.model.gateway.chat.ChatModelRequest;
import com.nexarag.model.gateway.chat.ChatModelResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * 模型 Chat 控制器，提供模型模块裸 Chat 调用入口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/model/chat")
public class ModelChatController {

    private final ModelGateway modelGateway;

    /**
     * 执行同步 Chat 调用。
     *
     * @param request 裸 Chat 请求
     * @return Chat 响应
     */
    @PostMapping
    public Result<ChatModelResponse> chat(@RequestBody ModelChatRequest request) {
        // 1. 将 Controller 请求转换为统一模型网关请求
        ChatModelRequest gatewayRequest = ChatModelRequest.builder()
                .traceId(UUID.randomUUID().toString())
                .bizType(ModelBizType.CHAT)
                .bizId(request.routeKey())
                .routeKey(request.routeKey())
                .messages(request.messages())
                .options(request.options() == null ? Map.of() : request.options())
                .build();

        // 2. 调用统一模型网关
        return Results.success(modelGateway.chat(gatewayRequest));
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

Run:

```powershell
mvn -pl nexa-rag-model -am test -Dtest=ModelChatControllerTest "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: PASS。

- [ ] **Step 6: 提交**

```powershell
git add nexa-rag-model/src/main/java/com/nexarag/model/dto/ModelChatRequest.java `
        nexa-rag-model/src/main/java/com/nexarag/model/controller/ModelChatController.java `
        nexa-rag-model/src/test/java/com/nexarag/model/controller/ModelChatControllerTest.java
git commit -m "feat(model): 新增裸Chat调用接口"
```

---

## Task 2: 流式 Chat 网关与 Controller

**Files:**
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/dto/ModelChatStreamResponse.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/gateway/ModelGateway.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/provider/ModelProviderAdapter.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/provider/ModelProviderDispatcher.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/provider/ChatProvider.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/controller/ModelChatController.java`
- Test: `nexa-rag-model/src/test/java/com/nexarag/model/provider/ChatProviderStreamTest.java`
- Test: `nexa-rag-model/src/test/java/com/nexarag/model/controller/ModelChatControllerTest.java`

- [ ] **Step 1: 写 ChatProvider 流式失败测试**

Add to `ChatProviderStreamTest.java`:

```java
package com.nexarag.model.provider;

import com.nexarag.model.client.ChatClientFactory;
import com.nexarag.model.config.ModelProfileProperties;
import com.nexarag.model.dto.ModelChatStreamResponse;
import com.nexarag.model.enums.ModelBizType;
import com.nexarag.model.gateway.chat.ChatModelRequest;
import com.nexarag.model.route.ModelRouteDecision;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.openai.OpenAiChatModel;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Chat Provider 流式调用测试。
 */
class ChatProviderStreamTest {

    @Test
    void streamChatShouldReturnContentChunks() {
        ChatClientFactory factory = mock(ChatClientFactory.class);
        OpenAiChatModel chatModel = mock(OpenAiChatModel.class);
        when(factory.getChatClient(any())).thenReturn(chatModel);
        when(chatModel.stream(any())).thenReturn(Flux.just(
                new ChatResponse(List.of(new Generation(new AssistantMessage("你")))),
                new ChatResponse(List.of(new Generation(new AssistantMessage("好"))))
        ));

        ChatProvider provider = new ChatProvider(factory);

        StepVerifier.create(provider.streamChat(decision(), request()))
                .expectNextMatches(chunk -> "你".equals(chunk.content()))
                .expectNextMatches(chunk -> "好".equals(chunk.content()))
                .verifyComplete();
    }

    private ChatModelRequest request() {
        return ChatModelRequest.builder()
                .traceId("trace-1")
                .bizType(ModelBizType.CHAT)
                .bizId("chat")
                .routeKey("chat.default")
                .messages(List.of(new ChatModelRequest.ChatMessage("USER", "你好")))
                .options(Map.of())
                .build();
    }

    private ModelRouteDecision decision() {
        ModelProfileProperties profile = ModelProfileProperties.builder()
                .provider("OPENAI")
                .baseUrl("http://localhost:11434/v1")
                .endpointPath("/chat/completions")
                .modelName("qwen2.5:7b")
                .build();
        return new ModelRouteDecision("chat-primary", profile, false);
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
mvn -pl nexa-rag-model -am test -Dtest=ChatProviderStreamTest "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: FAIL，提示缺少 `ModelChatStreamResponse` 或 `streamChat` 方法。

- [ ] **Step 3: 新增流式响应 DTO**

Create `nexa-rag-model/src/main/java/com/nexarag/model/dto/ModelChatStreamResponse.java`:

```java
package com.nexarag.model.dto;

import lombok.Builder;

/**
 * Chat 流式响应分片。
 *
 * @param content       当前文本分片
 * @param finishReason  结束原因
 * @param errorCode     错误码
 * @param errorMessage  错误信息
 */
@Builder
public record ModelChatStreamResponse(String content, String finishReason, String errorCode, String errorMessage) {

    public static ModelChatStreamResponse message(String content) {
        return ModelChatStreamResponse.builder().content(content).build();
    }

    public static ModelChatStreamResponse done(String finishReason) {
        return ModelChatStreamResponse.builder().finishReason(finishReason).build();
    }

    public static ModelChatStreamResponse error(String errorCode, String errorMessage) {
        return ModelChatStreamResponse.builder().errorCode(errorCode).errorMessage(errorMessage).build();
    }
}
```

- [ ] **Step 4: 给 Provider 增加流式接口**

Modify `ModelProviderAdapter`:

```java
default Flux<ModelChatStreamResponse> streamChat(ModelRouteDecision decision, ChatModelRequest request) {
    throw new ServiceException("当前模型厂商暂未支持流式 Chat 调用", BaseErrorCode.SERVICE_ERROR);
}
```

Modify imports:

```java
import com.nexarag.model.dto.ModelChatStreamResponse;
import reactor.core.publisher.Flux;
```

Modify `ModelProviderDispatcher`:

```java
public Flux<ModelChatStreamResponse> streamChat(ModelRouteDecision decision, ChatModelRequest request) {
    // 1. 按路由决策中的厂商选择 Chat 流式适配器
    return select(decision, ModelType.CHAT).streamChat(decision, request);
}
```

- [ ] **Step 5: 实现 ChatProvider 流式调用**

Modify `ChatProvider`:

```java
public Flux<ModelChatStreamResponse> streamChat(ModelRouteDecision decision, ChatModelRequest request) {
    // 1. 将统一网关消息转换为 Spring AI Prompt
    Prompt prompt = new Prompt(messages(request.messages()));

    // 2. 将 Spring AI 流式响应转换为统一分片
    return chatClientFactory.getChatClient(decision)
            .stream(prompt)
            .map(this::streamChunk)
            .filter(chunk -> StringUtils.hasText(chunk.content()));
}

private ModelChatStreamResponse streamChunk(ChatResponse response) {
    return ModelChatStreamResponse.message(content(response));
}
```

Add imports:

```java
import com.nexarag.model.dto.ModelChatStreamResponse;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
```

- [ ] **Step 6: 给 ModelGateway 增加 streamChat**

Modify `ModelGateway`:

```java
public Flux<ModelChatStreamResponse> streamChat(ChatModelRequest request) {
    // 1. 交给执行模板统一处理路由、日志和后续治理能力
    return executionTemplate.executeStream(ModelExecutionCommand.ofChat(request,
            decision -> providerDispatcher.chat(decision, request)),
            decision -> providerDispatcher.streamChat(decision, request));
}
```

This step depends on Task 4 `executeStream` if not yet implemented. If implementing Task 2 before Task 4, temporarily wire:

```java
ModelRouteDecision decision = modelRouter.route(new ModelRouteContext(request.routeKey(), false));
return providerDispatcher.streamChat(decision, request);
```

Then replace it in Task 4. Prefer implementing Task 4 before completing this method.

- [ ] **Step 7: 新增 SSE Controller 方法**

Modify `ModelChatController`:

```java
@PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<ModelChatStreamResponse>> streamChat(@RequestBody ModelChatRequest request) {
    // 1. 将 Controller 请求转换为统一模型网关请求
    ChatModelRequest gatewayRequest = toGatewayRequest(request);

    // 2. 将模型流式分片包装为 SSE
    return modelGateway.streamChat(gatewayRequest)
            .map(chunk -> ServerSentEvent.builder(chunk).event(eventName(chunk)).build())
            .concatWithValues(ServerSentEvent.builder(ModelChatStreamResponse.done("STOP")).event("done").build())
            .onErrorResume(exception -> Flux.just(ServerSentEvent
                    .builder(ModelChatStreamResponse.error("MODEL_STREAM_ERROR", exception.getMessage()))
                    .event("error")
                    .build()));
}

private String eventName(ModelChatStreamResponse chunk) {
    if (chunk.errorCode() != null) {
        return "error";
    }
    if (chunk.finishReason() != null) {
        return "done";
    }
    return "message";
}
```

Refactor sync method to call `toGatewayRequest(request)`.

- [ ] **Step 8: 运行流式相关测试**

Run:

```powershell
mvn -pl nexa-rag-model -am test -Dtest=ChatProviderStreamTest,ModelChatControllerTest "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: PASS。

- [ ] **Step 9: 提交**

```powershell
git add nexa-rag-model/src/main/java/com/nexarag/model/dto/ModelChatStreamResponse.java `
        nexa-rag-model/src/main/java/com/nexarag/model/controller/ModelChatController.java `
        nexa-rag-model/src/main/java/com/nexarag/model/gateway/ModelGateway.java `
        nexa-rag-model/src/main/java/com/nexarag/model/provider/ModelProviderAdapter.java `
        nexa-rag-model/src/main/java/com/nexarag/model/provider/ModelProviderDispatcher.java `
        nexa-rag-model/src/main/java/com/nexarag/model/provider/ChatProvider.java `
        nexa-rag-model/src/test/java/com/nexarag/model/provider/ChatProviderStreamTest.java `
        nexa-rag-model/src/test/java/com/nexarag/model/controller/ModelChatControllerTest.java
git commit -m "feat(model): 新增Chat流式调用接口"
```

---

## Task 3: 治理配置表、实体、服务与 REST

**Files:**
- Create: `nexa-rag-boot/src/main/resources/db/migration/V8__add_model_governance_config.sql`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/entity/ModelGovernanceConfig.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/mapper/ModelGovernanceConfigMapper.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/dto/ModelGovernanceConfigRequest.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/dto/ModelGovernanceConfigResponse.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/service/ModelGovernanceConfigService.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/service/impl/ModelGovernanceConfigServiceImpl.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/controller/ModelConfigController.java`
- Test: `nexa-rag-model/src/test/java/com/nexarag/model/service/impl/ModelGovernanceConfigServiceImplTest.java`

- [ ] **Step 1: 写治理配置服务失败测试**

Create `ModelGovernanceConfigServiceImplTest` with these cases:

```java
@Test
void saveShouldCreateDefaultWhenMissing() {
    ModelGovernanceConfigServiceImpl service = new ModelGovernanceConfigServiceImpl();
    ModelGovernanceConfigRequest request = new ModelGovernanceConfigRequest(
            30000, true, 3, 200,
            true, 50, 100, 3000, 5, 20, 30000,
            true, 60, 60000, 0,
            true, 10, 0,
            true, "默认治理配置"
    );

    ModelGovernanceConfig entity = service.toEntity(1L, request);

    assertThat(entity.getConfigId()).isEqualTo(1L);
    assertThat(entity.getRetryEnabled()).isTrue();
    assertThat(entity.getMaxAttempts()).isEqualTo(3);
    assertThat(entity.getCircuitEnabled()).isTrue();
    assertThat(entity.getLimitForPeriod()).isEqualTo(60);
    assertThat(entity.getMaxConcurrentCalls()).isEqualTo(10);
}
```

Expose `toEntity` as `protected` if needed for unit testing, or test via subclass override of persistence methods.

- [ ] **Step 2: 运行测试确认失败**

```powershell
mvn -pl nexa-rag-model -am test -Dtest=ModelGovernanceConfigServiceImplTest "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: FAIL，缺少类。

- [ ] **Step 3: 新增 Flyway 脚本**

Create `V8__add_model_governance_config.sql`:

```sql
CREATE TABLE IF NOT EXISTS model_governance_config (
    governance_id BIGINT NOT NULL COMMENT '治理配置ID',
    config_id BIGINT NOT NULL COMMENT '模型配置ID',
    timeout_ms INT NULL COMMENT 'HTTP客户端超时时间毫秒',
    retry_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用重试',
    max_attempts INT NOT NULL DEFAULT 1 COMMENT '最大尝试次数',
    retry_wait_ms INT NOT NULL DEFAULT 0 COMMENT '重试间隔毫秒',
    circuit_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用熔断',
    failure_rate_threshold INT NOT NULL DEFAULT 50 COMMENT '失败率阈值',
    slow_call_rate_threshold INT NOT NULL DEFAULT 100 COMMENT '慢调用比例阈值',
    slow_call_duration_ms INT NOT NULL DEFAULT 3000 COMMENT '慢调用耗时阈值毫秒',
    minimum_number_of_calls INT NOT NULL DEFAULT 5 COMMENT '熔断最小调用数',
    sliding_window_size INT NOT NULL DEFAULT 20 COMMENT '熔断滑动窗口大小',
    wait_duration_in_open_state_ms INT NOT NULL DEFAULT 30000 COMMENT '熔断打开等待时间毫秒',
    rate_limit_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用限流',
    limit_for_period INT NOT NULL DEFAULT 60 COMMENT '单周期允许请求数',
    limit_refresh_period_ms INT NOT NULL DEFAULT 60000 COMMENT '限流刷新周期毫秒',
    timeout_duration_ms INT NOT NULL DEFAULT 0 COMMENT '限流等待时间毫秒',
    bulkhead_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用并发隔离',
    max_concurrent_calls INT NOT NULL DEFAULT 10 COMMENT '最大并发调用数',
    max_wait_duration_ms INT NOT NULL DEFAULT 0 COMMENT '并发隔离等待时间毫秒',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用治理配置',
    remark VARCHAR(255) NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    del_flag TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记：0未删除，1已删除',
    delete_time DATETIME NULL COMMENT '删除时间',
    version INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    PRIMARY KEY (governance_id),
    UNIQUE KEY uk_model_governance_config_config_id (config_id),
    KEY idx_model_governance_config_del_flag (del_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型治理配置表';
```

- [ ] **Step 4: 新增实体、Mapper、DTO、Service**

Entity must include Chinese JavaDoc and comments for every field. `ModelGovernanceConfigService` extends `IService<ModelGovernanceConfig>` and exposes:

```java
ModelGovernanceConfigResponse getByConfigId(Long configId);

ModelGovernanceConfig saveByConfigId(Long configId, ModelGovernanceConfigRequest request);

ModelGovernanceConfig findEnabledByConfigId(Long configId);
```

Implementation rules:

- Use `lambdaQuery()` to fetch by configId.
- Use `save()` for missing config.
- Use `lambdaUpdate()` for existing config.
- Trigger registry refresh after save.

- [ ] **Step 5: 增加 REST 接口**

Modify `ModelConfigController`:

```java
@GetMapping("/{configId}/governance")
public Result<ModelGovernanceConfigResponse> getGovernance(@PathVariable Long configId) {
    return Results.success(modelGovernanceConfigService.getByConfigId(configId));
}

@PutMapping("/{configId}/governance")
public Result<ModelGovernanceConfigResponse> saveGovernance(@PathVariable Long configId,
                                                            @RequestBody ModelGovernanceConfigRequest request) {
    ModelGovernanceConfig config = modelGovernanceConfigService.saveByConfigId(configId, request);
    return Results.success(modelGovernanceConfigService.toResponse(config));
}
```

- [ ] **Step 6: 运行测试**

```powershell
mvn -pl nexa-rag-model -am test -Dtest=ModelGovernanceConfigServiceImplTest,ModelConfigControllerTest "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: PASS。

- [ ] **Step 7: 提交**

```powershell
git add nexa-rag-boot/src/main/resources/db/migration/V8__add_model_governance_config.sql `
        nexa-rag-model/src/main/java/com/nexarag/model/entity/ModelGovernanceConfig.java `
        nexa-rag-model/src/main/java/com/nexarag/model/mapper/ModelGovernanceConfigMapper.java `
        nexa-rag-model/src/main/java/com/nexarag/model/dto/ModelGovernanceConfigRequest.java `
        nexa-rag-model/src/main/java/com/nexarag/model/dto/ModelGovernanceConfigResponse.java `
        nexa-rag-model/src/main/java/com/nexarag/model/service/ModelGovernanceConfigService.java `
        nexa-rag-model/src/main/java/com/nexarag/model/service/impl/ModelGovernanceConfigServiceImpl.java `
        nexa-rag-model/src/main/java/com/nexarag/model/controller/ModelConfigController.java `
        nexa-rag-model/src/test/java/com/nexarag/model/service/impl/ModelGovernanceConfigServiceImplTest.java
git commit -m "feat(model): 新增模型治理配置"
```

---

## Task 4: 路由计划、主备 fallback 与权重路由

**Files:**
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/route/ModelRoutePlan.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/route/WeightedModelRouteSelector.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/enums/ModelRouteStrategy.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/route/ModelRouter.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/route/PrimaryFallbackModelRouter.java`
- Test: `nexa-rag-model/src/test/java/com/nexarag/model/route/WeightedModelRouteSelectorTest.java`
- Test: `nexa-rag-model/src/test/java/com/nexarag/model/route/PrimaryFallbackModelRouterTest.java`

- [ ] **Step 1: 写权重选择测试**

Create `WeightedModelRouteSelectorTest`:

```java
@Test
void shouldPreferOnlyPositiveWeightCandidates() {
    WeightedModelRouteSelector selector = new WeightedModelRouteSelector(new Random(1));
    List<ModelRouteDecision> selected = selector.orderCandidates(List.of(
            decision("a", 0, 1),
            decision("b", 10, 2),
            decision("c", 20, 3)
    ));

    assertThat(selected).extracting(ModelRouteDecision::profileName)
            .containsExactlyInAnyOrder("b", "c");
}
```

Use a test helper that stores `weight` and `priority` in `ModelProfileProperties` only if current decision lacks metadata. If current `ModelRouteDecision` cannot hold weight/priority, add these fields in Step 3.

- [ ] **Step 2: 运行测试确认失败**

```powershell
mvn -pl nexa-rag-model -am test -Dtest=WeightedModelRouteSelectorTest "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: FAIL，缺少 selector 或 metadata。

- [ ] **Step 3: 新增 ModelRoutePlan 和扩展决策元数据**

Create:

```java
public record ModelRoutePlan(String routeKey, ModelRouteStrategy strategy, List<ModelRouteDecision> candidates) {
}
```

Extend `ModelRouteDecision` with:

```java
Integer priority
Integer weight
Long routeConfigId
Long configId
```

Keep existing constructor by overloading to avoid breaking tests:

```java
public ModelRouteDecision(String profileName, ModelProfileProperties profile, boolean fallback) {
    this(profileName, profile, fallback, null, null, null, null);
}
```

- [ ] **Step 4: 扩展策略枚举**

Modify `ModelRouteStrategy`:

```java
PRIMARY_BACKUP,
WEIGHT,
RULE
```

- [ ] **Step 5: 实现权重候选排序器**

`WeightedModelRouteSelector.orderCandidates(List<ModelRouteDecision>)`:

1. 过滤 `weight > 0`。
2. 使用静态权重随机选第一个。
3. 从剩余正权重候选继续随机排列。
4. 若正权重候选为空，按 priority 升序返回。

- [ ] **Step 6: 修改 ModelRouter 返回 plan**

Add method:

```java
ModelRoutePlan plan(ModelRouteContext context);
```

Keep existing `route(...)` default method for compatibility:

```java
default ModelRouteDecision route(ModelRouteContext context) {
    ModelRoutePlan plan = plan(context);
    if (plan.candidates().isEmpty()) {
        throw new ServiceException("模型路由没有可用候选: " + context.routeKey());
    }
    return plan.candidates().getFirst();
}
```

- [ ] **Step 7: 更新 PrimaryFallbackModelRouter**

For yml-backed legacy router:

- `PRIMARY_BACKUP` returns primary then fallback.
- `WEIGHT` and `RULE` throw clear `ServiceException` until registry router handles DB routes.

Message:

```text
当前本地配置路由暂不支持权重或规则策略
```

- [ ] **Step 8: 运行路由测试**

```powershell
mvn -pl nexa-rag-model -am test -Dtest=WeightedModelRouteSelectorTest,PrimaryFallbackModelRouterTest "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: PASS。

- [ ] **Step 9: 提交**

```powershell
git add nexa-rag-model/src/main/java/com/nexarag/model/route/ModelRoutePlan.java `
        nexa-rag-model/src/main/java/com/nexarag/model/route/WeightedModelRouteSelector.java `
        nexa-rag-model/src/main/java/com/nexarag/model/enums/ModelRouteStrategy.java `
        nexa-rag-model/src/main/java/com/nexarag/model/route/ModelRouter.java `
        nexa-rag-model/src/main/java/com/nexarag/model/route/PrimaryFallbackModelRouter.java `
        nexa-rag-model/src/test/java/com/nexarag/model/route/WeightedModelRouteSelectorTest.java `
        nexa-rag-model/src/test/java/com/nexarag/model/route/PrimaryFallbackModelRouterTest.java
git commit -m "feat(model): 支持模型路由候选计划"
```

---

## Task 5: 治理执行器与 fallback 执行模板

**Files:**
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/governance/ModelGovernanceSettings.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/governance/ModelGovernanceResolver.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/governance/ModelGovernanceExecutor.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/execution/ModelExecutionTemplate.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/config/ModelConfiguration.java`
- Test: `nexa-rag-model/src/test/java/com/nexarag/model/governance/ModelGovernanceExecutorTest.java`
- Test: `nexa-rag-model/src/test/java/com/nexarag/model/execution/ModelExecutionTemplateFallbackTest.java`

- [ ] **Step 1: 写治理执行器测试**

Create tests:

```java
@Test
void retryShouldCallProviderAgainWhenEnabled() {
    AtomicInteger calls = new AtomicInteger();
    ModelGovernanceExecutor executor = new ModelGovernanceExecutor();
    ModelGovernanceSettings settings = ModelGovernanceSettings.builder()
            .retryEnabled(true)
            .maxAttempts(2)
            .retryWaitMs(0)
            .build();

    String result = executor.execute("config-1", settings, () -> {
        if (calls.incrementAndGet() == 1) {
            throw new IllegalStateException("第一次失败");
        }
        return "ok";
    });

    assertThat(result).isEqualTo("ok");
    assertThat(calls).hasValue(2);
}
```

- [ ] **Step 2: 运行测试确认失败**

```powershell
mvn -pl nexa-rag-model -am test -Dtest=ModelGovernanceExecutorTest "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: FAIL，缺少治理执行器。

- [ ] **Step 3: 实现治理设置与解析器**

`ModelGovernanceSettings` fields:

```java
Integer timeoutMs;
Boolean retryEnabled;
Integer maxAttempts;
Integer retryWaitMs;
Boolean circuitEnabled;
Integer failureRateThreshold;
Integer slowCallRateThreshold;
Integer slowCallDurationMs;
Integer minimumNumberOfCalls;
Integer slidingWindowSize;
Integer waitDurationInOpenStateMs;
Boolean rateLimitEnabled;
Integer limitForPeriod;
Integer limitRefreshPeriodMs;
Integer timeoutDurationMs;
Boolean bulkheadEnabled;
Integer maxConcurrentCalls;
Integer maxWaitDurationMs;
```

`ModelGovernanceResolver.resolve(ModelRouteDecision)`:

1. If governance config exists and enabled, use it.
2. Else use `ModelConfig.timeoutMs/maxRetries`.
3. Else use yml defaults.

- [ ] **Step 4: 实现 ModelGovernanceExecutor**

Expose:

```java
public <T> T execute(String configKey, ModelGovernanceSettings settings, Supplier<T> supplier)
```

Decorator order:

```text
RateLimiter -> Bulkhead -> CircuitBreaker -> Retry -> supplier
```

Use config names:

```java
String backendName = "model-" + configKey;
```

Use Resilience4j core APIs directly. If current dependency only has spring-boot starter, add `resilience4j-retry`, `resilience4j-ratelimiter`, `resilience4j-bulkhead`, `resilience4j-circuitbreaker` if compile requires them.

- [ ] **Step 5: 修改执行模板支持候选链 fallback**

`ModelExecutionTemplate.execute(command)`:

1. Ask `modelRouter.plan(...)`.
2. Iterate candidates.
3. Create one log per attempt.
4. Execute through `ModelGovernanceExecutor`.
5. On fallbackable exception, mark failed and try next.
6. If later candidate succeeds, mark `FALLBACK_SUCCESS`.
7. If all fail, throw last exception or `ServiceException("模型路由全部候选调用失败")`.

Fallbackable exceptions:

- Provider exception.
- `CallNotPermittedException`
- `RequestNotPermitted`
- `BulkheadFullException`

- [ ] **Step 6: 运行执行模板测试**

```powershell
mvn -pl nexa-rag-model -am test -Dtest=ModelGovernanceExecutorTest,ModelExecutionTemplateFallbackTest "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: PASS。

- [ ] **Step 7: 提交**

```powershell
git add nexa-rag-model/src/main/java/com/nexarag/model/governance `
        nexa-rag-model/src/main/java/com/nexarag/model/execution/ModelExecutionTemplate.java `
        nexa-rag-model/src/main/java/com/nexarag/model/config/ModelConfiguration.java `
        nexa-rag-model/src/test/java/com/nexarag/model/governance/ModelGovernanceExecutorTest.java `
        nexa-rag-model/src/test/java/com/nexarag/model/execution/ModelExecutionTemplateFallbackTest.java
git commit -m "feat(model): 接入模型治理执行器"
```

---

## Task 6: 调用日志 fallback 字段

**Files:**
- Create: `nexa-rag-boot/src/main/resources/db/migration/V9__extend_model_call_log_fallback.sql`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/entity/ModelCallLog.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/service/ModelCallLogService.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/service/impl/ModelCallLogServiceImpl.java`
- Test: `nexa-rag-model/src/test/java/com/nexarag/model/service/impl/ModelCallLogServiceImplTest.java`

- [ ] **Step 1: 新增失败测试**

Add test:

```java
@Test
void createRunningLogShouldRecordAttemptAndFallbackInfo() {
    ModelCallLog log = modelCallLogService.createRunningLog(
            "trace", ModelBizType.CHAT, "biz", "chat-backup",
            "OLLAMA", "http://localhost:11434/v1", "qwen2.5:7b",
            ModelRequestType.CHAT, 2, "call-primary", "PRIMARY_FAILED");

    assertThat(log.getAttemptNo()).isEqualTo(2);
    assertThat(log.getFallbackFromCallId()).isEqualTo("call-primary");
    assertThat(log.getFallbackReason()).isEqualTo("PRIMARY_FAILED");
}
```

- [ ] **Step 2: 运行测试确认失败**

```powershell
mvn -pl nexa-rag-model -am test -Dtest=ModelCallLogServiceImplTest "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: FAIL，接口签名或字段缺失。

- [ ] **Step 3: 新增 migration**

```sql
ALTER TABLE model_call_log
    ADD COLUMN attempt_no INT NOT NULL DEFAULT 1 COMMENT '第几次尝试' AFTER duration_ms,
    ADD COLUMN fallback_from_call_id VARCHAR(64) NULL COMMENT '降级来源调用ID' AFTER attempt_no,
    ADD COLUMN fallback_reason VARCHAR(128) NULL COMMENT '降级原因' AFTER fallback_from_call_id;
```

- [ ] **Step 4: 更新实体和服务**

Add fields to `ModelCallLog`:

```java
private Integer attemptNo;
private String fallbackFromCallId;
private String fallbackReason;
```

Overload `createRunningLog`:

```java
ModelCallLog createRunningLog(..., ModelRequestType requestType,
                              Integer attemptNo, String fallbackFromCallId, String fallbackReason);
```

Keep old method as default call with attempt 1 and null fallback fields.

- [ ] **Step 5: 更新执行模板调用日志**

When iterating route candidates:

```java
attemptNo = index + 1;
fallbackFromCallId = previousFailedCallId;
fallbackReason = previousFailureReason;
```

- [ ] **Step 6: 运行日志与执行模板测试**

```powershell
mvn -pl nexa-rag-model -am test -Dtest=ModelCallLogServiceImplTest,ModelExecutionTemplateFallbackTest "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: PASS。

- [ ] **Step 7: 提交**

```powershell
git add nexa-rag-boot/src/main/resources/db/migration/V9__extend_model_call_log_fallback.sql `
        nexa-rag-model/src/main/java/com/nexarag/model/entity/ModelCallLog.java `
        nexa-rag-model/src/main/java/com/nexarag/model/service/ModelCallLogService.java `
        nexa-rag-model/src/main/java/com/nexarag/model/service/impl/ModelCallLogServiceImpl.java `
        nexa-rag-model/src/test/java/com/nexarag/model/service/impl/ModelCallLogServiceImplTest.java
git commit -m "feat(model): 增强模型调用降级日志"
```

---

## Task 7: 临时 HTML 管理页

**Files:**
- Create: `nexa-rag-boot/src/main/resources/static/model-admin.html`
- Create: `nexa-rag-boot/src/test/java/com/nexarag/boot/staticresource/ModelAdminStaticResourceTest.java`

- [ ] **Step 1: 写静态资源测试**

Create:

```java
package com.nexarag.boot.staticresource;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模型临时管理页静态资源测试。
 */
class ModelAdminStaticResourceTest {

    @Test
    void modelAdminHtmlShouldExist() {
        ClassPathResource resource = new ClassPathResource("static/model-admin.html");

        assertThat(resource.exists()).isTrue();
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```powershell
mvn -pl nexa-rag-boot -am test -Dtest=ModelAdminStaticResourceTest "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: FAIL，静态资源不存在。

- [ ] **Step 3: 新增 HTML 页面**

Create `model-admin.html` with these sections:

- Provider 推荐值：`GET /api/model/providers`
- Config 列表和保存：`GET/POST/PUT/DELETE /api/model/configs`
- Governance 保存：`GET/PUT /api/model/configs/{configId}/governance`
- Route 列表和保存：`GET/POST/PUT/DELETE /api/model/routes`
- Route Config 绑定：`POST/PUT/DELETE /api/model/routes/{routeId}/configs`
- Config/Route 测试：`POST /api/model/configs/{configId}/test`、`POST /api/model/routes/{routeId}/test`
- Registry 刷新：`POST /api/model/registry/refresh`
- 同步 Chat：`POST /api/model/chat`
- 流式 Chat：`POST /api/model/chat/stream`

Use plain JS helpers:

```javascript
async function api(path, options = {}) {
  const response = await fetch(path, {
    headers: {'Content-Type': 'application/json'},
    ...options
  });
  return response.json();
}

async function streamChat() {
  const response = await fetch('/api/model/chat/stream', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify(readChatRequest())
  });
  const reader = response.body.getReader();
  const decoder = new TextDecoder('utf-8');
  while (true) {
    const {done, value} = await reader.read();
    if (done) {
      break;
    }
    appendStreamText(decoder.decode(value, {stream: true}));
  }
}
```

Keep layout simple: header, two-column forms, tables, response panel.

- [ ] **Step 4: 运行静态资源测试**

```powershell
mvn -pl nexa-rag-boot -am test -Dtest=ModelAdminStaticResourceTest "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: PASS。

- [ ] **Step 5: 提交**

```powershell
git add nexa-rag-boot/src/main/resources/static/model-admin.html `
        nexa-rag-boot/src/test/java/com/nexarag/boot/staticresource/ModelAdminStaticResourceTest.java
git commit -m "feat(model): 新增模型临时管理页"
```

---

## Task 8: TODO 与全量验证

**Files:**
- Modify: `TODO.md`

- [ ] **Step 1: 更新 TODO**

Update `TODO.md`:

- Mark completed:
  - `实现 OpenAI-compatible 聊天模型真实调用`
  - `实现 Chat 模型连接测试`
  - `接入 Resilience4j 熔断、限流、重试、并发隔离和超时控制`
  - `实现权重路由`
  - `实现主模型失败或熔断后的备用模型 fallback 执行`
- Add or keep pending:
  - `接入 Resilience4j TimeLimiter`
  - `实现规则路由`
  - `实现动态权重`
  - `实现流式 Chat Token 精确统计`
  - `实现正式 Vue/React 模型管理页面`

- [ ] **Step 2: 运行模型模块测试**

```powershell
mvn -pl nexa-rag-model -am test
```

Expected: BUILD SUCCESS。

- [ ] **Step 3: 运行架构测试**

```powershell
mvn -pl nexa-rag-boot -am test -Dtest=ModuleDependencyTest "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: BUILD SUCCESS。

- [ ] **Step 4: 运行全量测试**

```powershell
mvn clean test
```

Expected: BUILD SUCCESS。

- [ ] **Step 5: 提交 TODO**

```powershell
git add TODO.md
git commit -m "docs(model): 更新模型治理待办状态"
```

- [ ] **Step 6: 最终状态检查**

```powershell
git status --short --branch
git log --oneline -8
```

Expected:

- 当前任务涉及文件均已提交。
- 如果仍有其他会话遗留改动，保持未提交并在最终汇报中列出。

---

## 自审结果

### 规格覆盖

- Chat 同步 Controller：Task 1。
- Chat 流式 Controller 和 Flux/SSE：Task 2。
- 治理配置落 DB：Task 3。
- RateLimiter、Bulkhead、CircuitBreaker、Retry：Task 5。
- 不接 TimeLimiter：Task 8 写入 TODO。
- 路由候选链、fallback：Task 4、Task 5。
- 权重路由：Task 4。
- 规则路由预留并 TODO：Task 4、Task 8。
- 调用日志 fallback 字段：Task 6。
- 临时 HTML 管理页：Task 7。
- document/retrieval 不接入：本计划无相关实现任务。

### 占位扫描

本计划没有待定实现项。后续不做的能力均明确写入 TODO。

### 类型一致性

计划中新增类型统一使用：

- `ModelChatRequest`
- `ModelChatStreamResponse`
- `ModelGovernanceConfig`
- `ModelGovernanceSettings`
- `ModelRoutePlan`
- `WeightedModelRouteSelector`
- `ModelGovernanceExecutor`

