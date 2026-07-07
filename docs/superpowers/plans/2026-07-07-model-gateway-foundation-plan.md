# Model Gateway Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 `nexa-rag-model` 完善为稳定、可插拔、可观测的统一模型访问层，暂不接入 document/retrieval 业务链路。

**Architecture:** 以 `ModelGateway` 为业务唯一入口，运行时通过 `ModelRouter -> ModelExecutionTemplate -> ModelGovernanceResolver/Executor -> ModelProviderDispatcher` 完成路由、治理、调用和观测。模型运行时配置统一进入 `ModelRegistrySnapshot`，配置变更通过 LOCAL 或 REDIS_PUB_SUB 刷新 JVM 快照，客户端缓存按 `configId + registryVersion` 隔离。

**Tech Stack:** Java 21、Spring Boot 3、MyBatis-Plus、Flyway、Resilience4j、Project Reactor、Redis Pub/Sub、JUnit 5、Mockito、Testcontainers Redis。

---

## Current Constraints

- 当前工作区已有其他会话未提交改动：
  - `nexa-rag-boot/src/main/resources/application.yml`
  - `nexa-rag-boot/src/test/java/com/nexarag/boot/NexaRagApplicationConfigurationTest.java`
- 执行本计划时必须先确认这些改动是否仍存在，不能擅自回滚。
- 代码注释和日志必须使用简体中文。
- 每个 Java 类必须有说明作用的 JavaDoc。
- 关键方法步骤使用中文编号注释。
- 业务查询和更新优先使用 MyBatis-Plus `lambdaQuery`、`lambdaUpdate`。
- 实体、DTO、VO、Service、Controller 命名继续沿用当前项目风格。

## Files And Responsibilities

### Configuration

- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/config/ModelGovernanceProperties.java`
  - 增加 `governance.bindingMode`、`governance.autoCreateDefault`、stream timeout 基础配置。
- Modify: `nexa-rag-boot/src/main/resources/application.yml`
  - 添加中文注释，说明 `CONFIG/ROUTE` 可选值和自动创建默认治理配置的作用。

### Schema

- Create: `nexa-rag-boot/src/main/resources/db/migration/V10__enhance_model_governance_runtime.sql`
  - 增加治理绑定字段、TimeLimiter/stream timeout 字段、调用日志观测字段。
- Create or Modify: `nexa-rag-boot/src/main/resources/db/schema/nexa_rag_schema.sql`
  - 给出完整初始化 SQL，覆盖当前所有基础表和模型表最终结构。

### Governance

- Create: `nexa-rag-model/src/main/java/com/nexarag/model/enums/ModelGovernanceBindingMode.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/enums/TokenUsageSource.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/enums/ModelCallStatus.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/entity/ModelGovernanceConfig.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/entity/ModelCallLog.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/governance/ModelGovernanceSettings.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/governance/ModelGovernanceResolver.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/governance/ModelGovernanceExecutor.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/governance/DefaultModelGovernancePolicyFactory.java`

### Registry And Refresh

- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/registry/ModelRegistrySnapshot.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/registry/ModelRegistry.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/registry/ModelRegistryRefresher.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/route/ModelRouteDecision.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/route/RegistryFirstModelRouter.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/refresh/DefaultModelRegistryChangePublisher.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/refresh/DefaultModelRegistryChangeListener.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/refresh/redis/RedisModelRegistryRefreshSubscriber.java`

### Client Cache

- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/client/ChatClientFactory.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/client/EmbeddingClientFactory.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/client/RerankClientFactory.java`

### Execution And Observability

- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/execution/ModelExecutionCommand.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/execution/ModelExecutionTemplate.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/service/ModelCallLogService.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/service/impl/ModelCallLogServiceImpl.java`

### Token Usage

- Create: `nexa-rag-model/src/main/java/com/nexarag/model/usage/TokenUsage.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/usage/TokenUsageStatistics.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/usage/TokenUsageStatisticsDispatcher.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/usage/DashScopeTokenUsageStatistics.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/usage/DefaultUnknownTokenUsageStatistics.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/provider/RerankProvider.java`

### Management API

- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/controller/ModelConfigController.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/controller/ModelRouteController.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/controller/ModelRouteConfigController.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/controller/ModelGovernanceConfigController.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/controller/ModelRegistryController.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/controller/ModelProviderController.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/service/impl/ModelConfigServiceImpl.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/service/impl/ModelRouteServiceImpl.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/service/impl/ModelRouteConfigServiceImpl.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/service/impl/ModelGovernanceConfigServiceImpl.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/service/impl/ModelProviderCatalogServiceImpl.java`

---

## Task 1: Schema And Enum Foundation

**Files:**
- Create: `nexa-rag-boot/src/main/resources/db/migration/V10__enhance_model_governance_runtime.sql`
- Create or Modify: `nexa-rag-boot/src/main/resources/db/schema/nexa_rag_schema.sql`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/enums/ModelGovernanceBindingMode.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/enums/TokenUsageSource.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/enums/ModelCallStatus.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/entity/ModelGovernanceConfig.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/entity/ModelCallLog.java`
- Test: `nexa-rag-model/src/test/java/com/nexarag/model/entity/ModelRegistryEntityTest.java`

- [ ] **Step 1: Write failing enum and entity tests**

Add these assertions to `ModelRegistryEntityTest`:

```java
@Test
void governanceConfigShouldSupportConfigAndRouteBinding() {
    ModelGovernanceConfig configBinding = ModelGovernanceConfig.builder()
            .bindingMode(ModelGovernanceBindingMode.CONFIG)
            .configId(1001L)
            .enabled(Boolean.TRUE)
            .build();

    ModelGovernanceConfig routeBinding = ModelGovernanceConfig.builder()
            .bindingMode(ModelGovernanceBindingMode.ROUTE)
            .routeKey("chat")
            .enabled(Boolean.TRUE)
            .build();

    assertThat(configBinding.getBindingMode()).isEqualTo(ModelGovernanceBindingMode.CONFIG);
    assertThat(configBinding.getConfigId()).isEqualTo(1001L);
    assertThat(routeBinding.getBindingMode()).isEqualTo(ModelGovernanceBindingMode.ROUTE);
    assertThat(routeBinding.getRouteKey()).isEqualTo("chat");
}

@Test
void modelCallLogShouldSupportTimeoutCancelAndTokenUsageSource() {
    ModelCallLog timeoutLog = ModelCallLog.builder()
            .status(ModelCallStatus.TIMEOUT)
            .tokenUsageSource(TokenUsageSource.PROVIDER_USAGE)
            .firstTokenLatencyMs(1200L)
            .chunkCount(3)
            .outputCharCount(128)
            .estimatedOutputTokens(32)
            .build();

    assertThat(timeoutLog.getStatus()).isEqualTo(ModelCallStatus.TIMEOUT);
    assertThat(ModelCallStatus.valueOf("CANCELED")).isEqualTo(ModelCallStatus.CANCELED);
    assertThat(timeoutLog.getTokenUsageSource()).isEqualTo(TokenUsageSource.PROVIDER_USAGE);
    assertThat(timeoutLog.getFirstTokenLatencyMs()).isEqualTo(1200L);
    assertThat(timeoutLog.getChunkCount()).isEqualTo(3);
}
```

- [ ] **Step 2: Run the failing test**

Run:

```powershell
mvn -pl nexa-rag-model -Dtest=ModelRegistryEntityTest test
```

Expected: compilation fails because `ModelGovernanceBindingMode`、`TokenUsageSource`、`TIMEOUT`、`CANCELED` and new entity fields do not exist.

- [ ] **Step 3: Add enums**

Create `ModelGovernanceBindingMode.java`:

```java
package com.nexarag.model.enums;

/**
 * 模型治理配置绑定模式，用于决定治理配置按模型配置还是按业务路由生效。
 */
public enum ModelGovernanceBindingMode {

    /**
     * 按模型配置ID绑定治理策略。
     */
    CONFIG,

    /**
     * 按模型路由 routeKey 绑定治理策略。
     */
    ROUTE
}
```

Create `TokenUsageSource.java`:

```java
package com.nexarag.model.enums;

/**
 * Token 用量统计来源。
 */
public enum TokenUsageSource {

    /**
     * 厂商响应中的 usage 字段。
     */
    PROVIDER_USAGE,

    /**
     * 厂商官方规则计算。
     */
    PROVIDER_RULE,

    /**
     * 本地 tokenizer 计算。
     */
    LOCAL_TOKENIZER,

    /**
     * 近似估算。
     */
    ESTIMATED,

    /**
     * 暂无法统计。
     */
    UNKNOWN
}
```

Modify `ModelCallStatus` to include:

```java
/**
 * 调用超时。
 */
TIMEOUT,

/**
 * 调用被取消。
 */
CANCELED
```

Do not use `FALLBACK_SUCCESS` in new logic. If the enum already contains it, leave it for compatibility and stop writing it.

- [ ] **Step 4: Extend entities**

Add these fields to `ModelGovernanceConfig`:

```java
/**
 * 治理配置绑定模式。
 */
private ModelGovernanceBindingMode bindingMode;

/**
 * 模型路由 key，ROUTE 绑定模式使用。
 */
private String routeKey;

/**
 * 是否启用同步调用超时保护。
 */
private Boolean timeLimiterEnabled;

/**
 * 同步调用超时时间，单位毫秒。
 */
private Integer timeLimiterTimeoutMs;

/**
 * 流式调用首个分片超时时间，单位毫秒。
 */
private Integer streamFirstChunkTimeoutMs;

/**
 * 流式调用最大持续时间，单位毫秒。
 */
private Integer streamMaxDurationMs;
```

Add these fields to `ModelCallLog`:

```java
/**
 * Token 用量统计来源。
 */
private TokenUsageSource tokenUsageSource;

/**
 * 首个 Token 或首个分片耗时，单位毫秒。
 */
private Long firstTokenLatencyMs;

/**
 * 流式响应分片数量。
 */
private Integer chunkCount;

/**
 * 输出字符数。
 */
private Integer outputCharCount;

/**
 * 估算输出 Token 数。
 */
private Integer estimatedOutputTokens;
```

Use explicit imports, not wildcard Lombok imports, if touching the class.

- [ ] **Step 5: Add migration SQL**

Create `V10__enhance_model_governance_runtime.sql`:

```sql
ALTER TABLE model_governance_config
    ADD COLUMN binding_mode VARCHAR(32) NOT NULL DEFAULT 'CONFIG' COMMENT '治理配置绑定模式：CONFIG按模型配置绑定，ROUTE按路由key绑定' AFTER governance_id,
    ADD COLUMN route_key VARCHAR(128) NULL COMMENT '模型路由key，ROUTE绑定模式使用' AFTER config_id,
    ADD COLUMN time_limiter_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用同步调用超时保护' AFTER bulkhead_enabled,
    ADD COLUMN time_limiter_timeout_ms INT NOT NULL DEFAULT 60000 COMMENT '同步调用超时时间，单位毫秒' AFTER time_limiter_enabled,
    ADD COLUMN stream_first_chunk_timeout_ms INT NOT NULL DEFAULT 30000 COMMENT '流式调用首个分片超时时间，单位毫秒' AFTER time_limiter_timeout_ms,
    ADD COLUMN stream_max_duration_ms INT NOT NULL DEFAULT 300000 COMMENT '流式调用最大持续时间，单位毫秒' AFTER stream_first_chunk_timeout_ms;

ALTER TABLE model_call_log
    ADD COLUMN token_usage_source VARCHAR(32) NULL COMMENT 'Token用量统计来源' AFTER total_tokens,
    ADD COLUMN first_token_latency_ms BIGINT NULL COMMENT '首个Token或首个分片耗时，单位毫秒' AFTER duration_ms,
    ADD COLUMN chunk_count INT NULL COMMENT '流式响应分片数量' AFTER first_token_latency_ms,
    ADD COLUMN output_char_count INT NULL COMMENT '输出字符数' AFTER chunk_count,
    ADD COLUMN estimated_output_tokens INT NULL COMMENT '估算输出Token数量' AFTER output_char_count;

CREATE INDEX idx_model_governance_config_config
    ON model_governance_config (binding_mode, config_id, del_flag);

CREATE INDEX idx_model_governance_config_route
    ON model_governance_config (binding_mode, route_key, del_flag);
```

If existing schema already has one of these columns because another session added it, convert the migration to only add missing fields and document the conflict in the final report.

- [ ] **Step 6: Update complete schema SQL**

Create or update `nexa-rag-boot/src/main/resources/db/schema/nexa_rag_schema.sql` with full table definitions from `V1` through `V10`. At minimum, ensure `model_governance_config` and `model_call_log` include every field from Step 5.

- [ ] **Step 7: Run entity test**

Run:

```powershell
mvn -pl nexa-rag-model -Dtest=ModelRegistryEntityTest test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 8: Commit**

```powershell
git add nexa-rag-boot/src/main/resources/db/migration/V10__enhance_model_governance_runtime.sql `
        nexa-rag-boot/src/main/resources/db/schema/nexa_rag_schema.sql `
        nexa-rag-model/src/main/java/com/nexarag/model/enums/ModelGovernanceBindingMode.java `
        nexa-rag-model/src/main/java/com/nexarag/model/enums/TokenUsageSource.java `
        nexa-rag-model/src/main/java/com/nexarag/model/enums/ModelCallStatus.java `
        nexa-rag-model/src/main/java/com/nexarag/model/entity/ModelGovernanceConfig.java `
        nexa-rag-model/src/main/java/com/nexarag/model/entity/ModelCallLog.java `
        nexa-rag-model/src/test/java/com/nexarag/model/entity/ModelRegistryEntityTest.java
git commit -m "feat(model): 扩展模型治理与调用日志结构"
```

---

## Task 2: Governance Binding And Default Policy

**Files:**
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/config/ModelGovernanceProperties.java`
- Modify: `nexa-rag-boot/src/main/resources/application.yml`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/governance/DefaultModelGovernancePolicyFactory.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/governance/ModelGovernanceSettings.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/governance/ModelGovernanceResolver.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/registry/ModelRegistrySnapshot.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/registry/ModelRegistry.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/registry/ModelRegistryRefresher.java`
- Test: `nexa-rag-model/src/test/java/com/nexarag/model/governance/ModelGovernanceResolverTest.java`
- Test: `nexa-rag-model/src/test/java/com/nexarag/model/governance/DefaultModelGovernancePolicyFactoryTest.java`

- [ ] **Step 1: Write resolver tests**

Create `ModelGovernanceResolverTest`:

```java
package com.nexarag.model.governance;

import com.nexarag.model.config.ModelGovernanceProperties;
import com.nexarag.model.entity.ModelConfig;
import com.nexarag.model.entity.ModelGovernanceConfig;
import com.nexarag.model.enums.ModelGovernanceBindingMode;
import com.nexarag.model.enums.ModelProvider;
import com.nexarag.model.enums.ModelType;
import com.nexarag.model.registry.ModelRegistry;
import com.nexarag.model.registry.ModelRegistrySnapshot;
import com.nexarag.model.route.ModelRouteDecision;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模型治理配置解析器测试。
 */
class ModelGovernanceResolverTest {

    @Test
    void configModeShouldResolveByConfigId() {
        ModelRegistry registry = new ModelRegistry();
        ModelConfig config = ModelConfig.builder()
                .configId(1001L)
                .configKey("chat-primary")
                .modelType(ModelType.CHAT)
                .provider(ModelProvider.CUSTOM_OPENAI)
                .enabled(Boolean.TRUE)
                .build();
        ModelGovernanceConfig governance = ModelGovernanceConfig.builder()
                .bindingMode(ModelGovernanceBindingMode.CONFIG)
                .configId(1001L)
                .enabled(Boolean.TRUE)
                .rateLimitEnabled(Boolean.TRUE)
                .limitForPeriod(10)
                .build();
        registry.replace(ModelRegistrySnapshot.builder()
                .versionNo(7L)
                .configMap(Map.of(1001L, config))
                .governanceConfigMap(Map.of("CONFIG:1001", governance))
                .build());

        ModelGovernanceProperties properties = new ModelGovernanceProperties();
        properties.getGovernance().setBindingMode(ModelGovernanceBindingMode.CONFIG);
        ModelGovernanceResolver resolver = new ModelGovernanceResolver(registry, properties);

        ModelGovernanceSettings settings = resolver.resolve(command("chat"),
                decision(1001L, 7L, config));

        assertThat(settings.getRateLimitEnabled()).isTrue();
        assertThat(settings.getLimitForPeriod()).isEqualTo(10);
    }

    @Test
    void routeModeShouldResolveByRouteKey() {
        ModelRegistry registry = new ModelRegistry();
        ModelGovernanceConfig governance = ModelGovernanceConfig.builder()
                .bindingMode(ModelGovernanceBindingMode.ROUTE)
                .routeKey("chat")
                .enabled(Boolean.TRUE)
                .bulkheadEnabled(Boolean.TRUE)
                .maxConcurrentCalls(3)
                .build();
        registry.replace(ModelRegistrySnapshot.builder()
                .versionNo(8L)
                .governanceConfigMap(Map.of("ROUTE:chat", governance))
                .build());

        ModelGovernanceProperties properties = new ModelGovernanceProperties();
        properties.getGovernance().setBindingMode(ModelGovernanceBindingMode.ROUTE);
        ModelGovernanceResolver resolver = new ModelGovernanceResolver(registry, properties);

        ModelGovernanceSettings settings = resolver.resolve(command("chat"),
                decision(1001L, 8L, null));

        assertThat(settings.getBulkheadEnabled()).isTrue();
        assertThat(settings.getMaxConcurrentCalls()).isEqualTo(3);
    }

    private ModelExecutionCommandStub command(String routeKey) {
        return new ModelExecutionCommandStub(routeKey);
    }

    private ModelRouteDecision decision(Long configId, Long registryVersion, ModelConfig config) {
        return ModelRouteDecision.builder()
                .profileName("chat-primary")
                .configId(configId)
                .registryVersion(registryVersion)
                .build();
    }

    private record ModelExecutionCommandStub(String routeKey) {
    }
}
```

If `ModelExecutionCommand` cannot be conveniently constructed, add a resolver overload `resolve(String routeKey, ModelRouteDecision decision)` and make the command-based method delegate to it.

- [ ] **Step 2: Write default policy tests**

Create `DefaultModelGovernancePolicyFactoryTest`:

```java
package com.nexarag.model.governance;

import com.nexarag.model.entity.ModelGovernanceConfig;
import com.nexarag.model.enums.ModelGovernanceBindingMode;
import com.nexarag.model.enums.ModelType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 默认模型治理策略工厂测试。
 */
class DefaultModelGovernancePolicyFactoryTest {

    @Test
    void chatDefaultShouldUseConservativeConcurrencyAndStreamTimeout() {
        DefaultModelGovernancePolicyFactory factory = new DefaultModelGovernancePolicyFactory();

        ModelGovernanceConfig config = factory.createForConfig(1001L, ModelType.CHAT);

        assertThat(config.getBindingMode()).isEqualTo(ModelGovernanceBindingMode.CONFIG);
        assertThat(config.getConfigId()).isEqualTo(1001L);
        assertThat(config.getCircuitEnabled()).isTrue();
        assertThat(config.getRateLimitEnabled()).isTrue();
        assertThat(config.getBulkheadEnabled()).isTrue();
        assertThat(config.getTimeLimiterEnabled()).isTrue();
        assertThat(config.getStreamFirstChunkTimeoutMs()).isGreaterThan(0);
        assertThat(config.getStreamMaxDurationMs()).isGreaterThan(config.getStreamFirstChunkTimeoutMs());
    }

    @Test
    void embeddingDefaultShouldAllowMoreConcurrencyThanChat() {
        DefaultModelGovernancePolicyFactory factory = new DefaultModelGovernancePolicyFactory();

        ModelGovernanceConfig chat = factory.createForConfig(1001L, ModelType.CHAT);
        ModelGovernanceConfig embedding = factory.createForConfig(1002L, ModelType.EMBEDDING);

        assertThat(embedding.getMaxConcurrentCalls()).isGreaterThan(chat.getMaxConcurrentCalls());
        assertThat(embedding.getRetryEnabled()).isTrue();
    }
}
```

- [ ] **Step 3: Run failing tests**

Run:

```powershell
mvn -pl nexa-rag-model -Dtest=ModelGovernanceResolverTest,DefaultModelGovernancePolicyFactoryTest test
```

Expected: compilation fails because resolver overload, properties, builder fields, snapshot map, and policy factory are missing.

- [ ] **Step 4: Extend properties**

Modify `ModelGovernanceProperties`:

```java
/**
 * 模型治理运行时配置。
 */
private Governance governance = new Governance();

/**
 * 模型治理运行时配置项。
 */
@Getter
@Setter
public static class Governance {

    /**
     * 治理配置绑定模式。
     */
    private ModelGovernanceBindingMode bindingMode = ModelGovernanceBindingMode.CONFIG;

    /**
     * 是否自动创建默认治理配置。
     */
    private Boolean autoCreateDefault = Boolean.TRUE;
}
```

Add imports for `ModelGovernanceBindingMode`.

Modify `application.yml` under `nexa.model`:

```yaml
    governance:
      # 治理配置绑定模式，用于决定运行时按什么维度查找 DB 治理配置。
      # 可选值：
      # CONFIG：按模型配置 config_id 绑定，同一个模型配置在所有路由下共用一套治理策略。
      # ROUTE：按模型路由 route_key 绑定，同一个模型在不同业务路由下可使用不同治理策略。
      binding-mode: CONFIG
      # 创建模型配置或模型路由时是否自动创建默认治理配置。
      # true：自动创建默认治理配置，已存在时不覆盖。
      # false：不自动创建，需要用户在模型治理页面手动维护。
      auto-create-default: true
```

Preserve any existing user changes in `application.yml`.

- [ ] **Step 5: Implement default policy factory**

Create `DefaultModelGovernancePolicyFactory` with JavaDoc and Chinese comments:

```java
package com.nexarag.model.governance;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.nexarag.model.entity.ModelGovernanceConfig;
import com.nexarag.model.enums.ModelGovernanceBindingMode;
import com.nexarag.model.enums.ModelType;
import org.springframework.stereotype.Component;

/**
 * 默认模型治理策略工厂，负责按模型类型生成可编辑的治理配置。
 */
@Component
public class DefaultModelGovernancePolicyFactory {

    /**
     * 创建模型配置级默认治理策略。
     *
     * @param configId  模型配置ID
     * @param modelType 模型类型
     * @return 默认治理配置
     */
    public ModelGovernanceConfig createForConfig(Long configId, ModelType modelType) {
        ModelGovernanceConfig config = createBase(modelType);
        config.setGovernanceId(IdWorker.getId());
        config.setBindingMode(ModelGovernanceBindingMode.CONFIG);
        config.setConfigId(configId);
        return config;
    }

    /**
     * 创建路由级默认治理策略。
     *
     * @param routeKey  模型路由 key
     * @param modelType 模型类型
     * @return 默认治理配置
     */
    public ModelGovernanceConfig createForRoute(String routeKey, ModelType modelType) {
        ModelGovernanceConfig config = createBase(modelType);
        config.setGovernanceId(IdWorker.getId());
        config.setBindingMode(ModelGovernanceBindingMode.ROUTE);
        config.setRouteKey(routeKey);
        return config;
    }

    private ModelGovernanceConfig createBase(ModelType modelType) {
        // 1. 先构建通用保护策略，再按模型类型调整并发、超时和重试
        ModelGovernanceConfig config = ModelGovernanceConfig.builder()
                .enabled(Boolean.TRUE)
                .retryEnabled(Boolean.FALSE)
                .maxAttempts(1)
                .retryWaitMs(0)
                .circuitEnabled(Boolean.TRUE)
                .failureRateThreshold(50)
                .slowCallRateThreshold(100)
                .slowCallDurationMs(3000)
                .minimumNumberOfCalls(10)
                .slidingWindowSize(20)
                .waitDurationInOpenStateMs(30000)
                .rateLimitEnabled(Boolean.TRUE)
                .limitForPeriod(60)
                .limitRefreshPeriodMs(1000)
                .timeoutDurationMs(0)
                .bulkheadEnabled(Boolean.TRUE)
                .maxConcurrentCalls(10)
                .maxWaitDurationMs(0)
                .timeLimiterEnabled(Boolean.TRUE)
                .timeLimiterTimeoutMs(60000)
                .streamFirstChunkTimeoutMs(30000)
                .streamMaxDurationMs(300000)
                .build();
        if (modelType == ModelType.EMBEDDING) {
            config.setRetryEnabled(Boolean.TRUE);
            config.setMaxAttempts(2);
            config.setLimitForPeriod(200);
            config.setMaxConcurrentCalls(30);
            config.setTimeLimiterTimeoutMs(60000);
        }
        if (modelType == ModelType.RERANK) {
            config.setLimitForPeriod(120);
            config.setMaxConcurrentCalls(20);
            config.setTimeLimiterTimeoutMs(30000);
        }
        return config;
    }
}
```

- [ ] **Step 6: Extend settings and resolver**

Add fields to `ModelGovernanceSettings` matching the new entity fields:

```java
private Boolean timeLimiterEnabled;
private Integer timeLimiterTimeoutMs;
private Integer streamFirstChunkTimeoutMs;
private Integer streamMaxDurationMs;
```

Modify `ModelRegistrySnapshot` to include:

```java
Map<String, ModelGovernanceConfig> governanceConfigMap
```

Use key helpers in `ModelRegistry`:

```java
public ModelGovernanceConfig getGovernanceConfig(ModelGovernanceBindingMode mode, Long configId, String routeKey) {
    String key = mode == ModelGovernanceBindingMode.CONFIG ? "CONFIG:" + configId : "ROUTE:" + routeKey;
    return current().governanceConfigMap().get(key);
}
```

Modify `ModelGovernanceResolver`:

```java
/**
 * 解析模型治理配置。
 *
 * @param routeKey 路由 key
 * @param decision 路由决策
 * @return 模型治理执行参数
 */
public ModelGovernanceSettings resolve(String routeKey, ModelRouteDecision decision) {
    ModelGovernanceBindingMode bindingMode = properties.getGovernance().getBindingMode();
    ModelGovernanceConfig config = modelRegistry.getGovernanceConfig(bindingMode, decision.configId(), routeKey);
    if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
        return ModelGovernanceSettings.disabled();
    }
    return toSettings(config);
}
```

Keep the command-based `resolve` used by `ModelExecutionTemplate`, and delegate to this overload.

- [ ] **Step 7: Load governance configs into registry snapshot**

Modify `ModelRegistryRefresher` to inject `ModelGovernanceConfigService` and load enabled governance configs:

```java
List<ModelGovernanceConfig> governanceConfigs = modelGovernanceConfigService.list().stream()
        .filter(config -> Boolean.TRUE.equals(config.getEnabled()))
        .toList();
Map<String, ModelGovernanceConfig> governanceMap = governanceConfigs.stream()
        .collect(Collectors.toMap(this::governanceKey, config -> config, (left, right) -> left));
```

Add helper:

```java
private String governanceKey(ModelGovernanceConfig config) {
    if (config.getBindingMode() == ModelGovernanceBindingMode.ROUTE) {
        return "ROUTE:" + config.getRouteKey();
    }
    return "CONFIG:" + config.getConfigId();
}
```

- [ ] **Step 8: Run tests**

Run:

```powershell
mvn -pl nexa-rag-model -Dtest=ModelGovernanceResolverTest,DefaultModelGovernancePolicyFactoryTest test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 9: Commit**

```powershell
git add nexa-rag-model/src/main/java/com/nexarag/model/config/ModelGovernanceProperties.java `
        nexa-rag-boot/src/main/resources/application.yml `
        nexa-rag-model/src/main/java/com/nexarag/model/governance `
        nexa-rag-model/src/main/java/com/nexarag/model/registry `
        nexa-rag-model/src/test/java/com/nexarag/model/governance
git commit -m "feat(model): 接入模型治理绑定配置"
```

---

## Task 3: Auto-Created Governance And Registry Version Events

**Files:**
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/service/impl/ModelConfigServiceImpl.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/service/impl/ModelRouteServiceImpl.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/service/impl/ModelGovernanceConfigServiceImpl.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/service/ModelGovernanceConfigService.java`
- Test: `nexa-rag-model/src/test/java/com/nexarag/model/service/impl/ModelConfigServiceImplTest.java`
- Test: `nexa-rag-model/src/test/java/com/nexarag/model/service/impl/ModelRouteServiceImplTest.java`
- Test: `nexa-rag-model/src/test/java/com/nexarag/model/service/impl/ModelGovernanceConfigServiceImplTest.java`

- [ ] **Step 1: Write auto-create tests**

Add to `ModelConfigServiceImplTest`:

```java
@Test
void createConfigShouldAutoCreateDefaultGovernanceWhenEnabled() {
    DefaultModelGovernancePolicyFactory policyFactory = mock(DefaultModelGovernancePolicyFactory.class);
    ModelGovernanceConfigService governanceConfigService = mock(ModelGovernanceConfigService.class);
    ModelGovernanceProperties properties = new ModelGovernanceProperties();
    properties.getGovernance().setAutoCreateDefault(Boolean.TRUE);

    ModelGovernanceConfig defaultConfig = ModelGovernanceConfig.builder()
            .bindingMode(ModelGovernanceBindingMode.CONFIG)
            .configId(1001L)
            .enabled(Boolean.TRUE)
            .build();
    when(policyFactory.createForConfig(anyLong(), eq(ModelType.CHAT))).thenReturn(defaultConfig);
    when(governanceConfigService.existsConfigBinding(anyLong())).thenReturn(false);

    ModelConfigServiceImpl service = serviceWith(policyFactory, governanceConfigService, properties);
    ModelConfig created = service.createConfig(createChatRequest());

    verify(governanceConfigService).saveDefaultIfAbsent(argThat(config ->
            config.getBindingMode() == ModelGovernanceBindingMode.CONFIG
                    && created.getConfigId().equals(config.getConfigId())));
}
```

Add to `ModelRouteServiceImplTest`:

```java
@Test
void createRouteShouldAutoCreateRouteGovernanceWhenEnabled() {
    DefaultModelGovernancePolicyFactory policyFactory = mock(DefaultModelGovernancePolicyFactory.class);
    ModelGovernanceConfigService governanceConfigService = mock(ModelGovernanceConfigService.class);
    ModelGovernanceProperties properties = new ModelGovernanceProperties();
    properties.getGovernance().setAutoCreateDefault(Boolean.TRUE);

    when(policyFactory.createForRoute(eq("chat"), eq(ModelType.CHAT)))
            .thenReturn(ModelGovernanceConfig.builder()
                    .bindingMode(ModelGovernanceBindingMode.ROUTE)
                    .routeKey("chat")
                    .enabled(Boolean.TRUE)
                    .build());

    ModelRouteServiceImpl service = serviceWith(policyFactory, governanceConfigService, properties);
    service.createRoute(new ModelRouteCreateRequest("chat", "聊天路由", ModelType.CHAT,
            ModelRouteStrategy.PRIMARY_BACKUP, Boolean.TRUE, "默认聊天路由"));

    verify(governanceConfigService).saveDefaultIfAbsent(argThat(config ->
            config.getBindingMode() == ModelGovernanceBindingMode.ROUTE
                    && "chat".equals(config.getRouteKey())));
}
```

If helper constructors do not exist, instantiate services with Mockito and `ReflectionTestUtils.setField` following existing tests.

- [ ] **Step 2: Write reset-default test**

Add to `ModelGovernanceConfigServiceImplTest`:

```java
@Test
void resetDefaultShouldOverwriteExplicitlyAndPublishRefresh() {
    DefaultModelGovernancePolicyFactory policyFactory = mock(DefaultModelGovernancePolicyFactory.class);
    ModelRegistryChangePublisher publisher = mock(ModelRegistryChangePublisher.class);
    ModelGovernanceConfig existing = ModelGovernanceConfig.builder()
            .governanceId(1L)
            .bindingMode(ModelGovernanceBindingMode.CONFIG)
            .configId(1001L)
            .maxConcurrentCalls(2)
            .build();
    ModelGovernanceConfig defaults = ModelGovernanceConfig.builder()
            .bindingMode(ModelGovernanceBindingMode.CONFIG)
            .configId(1001L)
            .maxConcurrentCalls(10)
            .build();

    ModelGovernanceConfigServiceImpl service = serviceWith(policyFactory, publisher);
    service.resetDefault(existing, defaults);

    verify(publisher).publish(anyLong());
}
```

- [ ] **Step 3: Run failing tests**

Run:

```powershell
mvn -pl nexa-rag-model -Dtest=ModelConfigServiceImplTest,ModelRouteServiceImplTest,ModelGovernanceConfigServiceImplTest test
```

Expected: compilation fails because methods such as `saveDefaultIfAbsent` and `resetDefault` do not exist.

- [ ] **Step 4: Add governance service methods**

Modify `ModelGovernanceConfigService`:

```java
/**
 * 判断模型配置级治理配置是否存在。
 *
 * @param configId 模型配置ID
 * @return true 表示存在
 */
boolean existsConfigBinding(Long configId);

/**
 * 判断路由级治理配置是否存在。
 *
 * @param routeKey 路由 key
 * @return true 表示存在
 */
boolean existsRouteBinding(String routeKey);

/**
 * 保存默认治理配置，已存在时不覆盖。
 *
 * @param config 默认治理配置
 */
void saveDefaultIfAbsent(ModelGovernanceConfig config);

/**
 * 显式恢复默认治理策略。
 *
 * @param governanceId 治理配置ID
 */
void resetDefault(Long governanceId);
```

Implement with `lambdaQuery` and `lambdaUpdate`.

- [ ] **Step 5: Integrate auto-create into config and route services**

In `ModelConfigServiceImpl.createConfig`, after `saveConfig(config)` and before bumping version:

```java
// 4. 自动创建模型级默认治理配置，已存在时不覆盖
if (Boolean.TRUE.equals(modelGovernanceProperties.getGovernance().getAutoCreateDefault())) {
    ModelGovernanceConfig governanceConfig =
            defaultModelGovernancePolicyFactory.createForConfig(config.getConfigId(), config.getModelType());
    modelGovernanceConfigService.saveDefaultIfAbsent(governanceConfig);
}

// 5. 触发模型注册表刷新
bumpRegistryVersionAndPublish();
```

In `ModelRouteServiceImpl.createRoute`, after saving route:

```java
// 1. 自动创建路由级默认治理配置，便于切换 ROUTE 模式后直接生效
if (Boolean.TRUE.equals(modelGovernanceProperties.getGovernance().getAutoCreateDefault())) {
    ModelGovernanceConfig governanceConfig =
            defaultModelGovernancePolicyFactory.createForRoute(route.getRouteKey(), route.getModelType());
    modelGovernanceConfigService.saveDefaultIfAbsent(governanceConfig);
}
```

If `ModelRoute` currently has no `modelType`, add it only if schema already supports it. If not, derive model type from request DTO or use `ModelType.CHAT` only for existing chat route tests and add a schema follow-up in the same task.

- [ ] **Step 6: Ensure governance changes bump registry version**

In `ModelGovernanceConfigServiceImpl`, call the existing registry version bump + publish flow after create, update, enable, delete, and reset default. If bump logic is duplicated in `ModelConfigServiceImpl`, extract a small `ModelRegistryVersionService` only if duplication becomes more than two service classes.

- [ ] **Step 7: Run tests**

```powershell
mvn -pl nexa-rag-model -Dtest=ModelConfigServiceImplTest,ModelRouteServiceImplTest,ModelGovernanceConfigServiceImplTest test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 8: Commit**

```powershell
git add nexa-rag-model/src/main/java/com/nexarag/model/service `
        nexa-rag-model/src/test/java/com/nexarag/model/service/impl
git commit -m "feat(model): 自动创建默认模型治理配置"
```

---

## Task 4: Registry Refresh Channels And Client Cache Versioning

**Files:**
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/enums/ModelRefreshChannel.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/config/ModelRegistryRefreshProperties.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/refresh/DefaultModelRegistryChangePublisher.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/refresh/DefaultModelRegistryChangeListener.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/refresh/redis/RedisModelRegistryRefreshMessageClient.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/refresh/redis/RedisModelRegistryRefreshSubscriber.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/route/ModelRouteDecision.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/route/RegistryFirstModelRouter.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/client/ChatClientFactory.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/client/EmbeddingClientFactory.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/client/RerankClientFactory.java`
- Modify: `nexa-rag-model/pom.xml`
- Test: `nexa-rag-model/src/test/java/com/nexarag/model/refresh/ModelRegistryLocalRefreshTest.java`
- Test: `nexa-rag-model/src/test/java/com/nexarag/model/refresh/redis/RedisModelRegistryRefreshIntegrationTest.java`
- Test: `nexa-rag-model/src/test/java/com/nexarag/model/client/ClientFactoryTest.java`

- [ ] **Step 1: Add Testcontainers Redis dependency**

Add to `nexa-rag-model/pom.xml`:

```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <scope>test</scope>
</dependency>
```

If root dependency management already defines Testcontainers modules, do not add versions here.

- [ ] **Step 2: Write LOCAL refresh test**

Create `ModelRegistryLocalRefreshTest`:

```java
package com.nexarag.model.refresh;

import com.nexarag.model.config.ModelRegistryRefreshProperties;
import com.nexarag.model.enums.ModelRefreshChannel;
import com.nexarag.model.registry.ModelRegistryRefresher;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 本地模型注册表刷新测试。
 */
class ModelRegistryLocalRefreshTest {

    @Test
    void localChannelShouldRefreshCurrentJvmDirectly() {
        ModelRegistryRefresher refresher = mock(ModelRegistryRefresher.class);
        ModelRegistryRefreshProperties properties = new ModelRegistryRefreshProperties();
        properties.setRefreshChannel(ModelRefreshChannel.LOCAL);
        DefaultModelRegistryChangePublisher publisher =
                new DefaultModelRegistryChangePublisher(properties, null, refresher);

        publisher.publish(9L);

        verify(refresher).refreshIfNewer(9L);
    }
}
```

- [ ] **Step 3: Write REDIS_PUB_SUB integration test**

Create `RedisModelRegistryRefreshIntegrationTest`:

```java
package com.nexarag.model.refresh.redis;

import com.nexarag.model.refresh.ModelRegistryChangedMessage;
import com.nexarag.model.registry.ModelRegistryRefresher;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Redis Pub/Sub 模型注册表刷新集成测试。
 */
@Testcontainers
class RedisModelRegistryRefreshIntegrationTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Test
    void redisPubSubShouldNotifyAnotherInstanceToRefresh() throws Exception {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(redisConfig());
        factory.afterPropertiesSet();
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);

        CountDownLatch latch = new CountDownLatch(1);
        ModelRegistryRefresher refresher = mock(ModelRegistryRefresher.class);
        RedisModelRegistryRefreshSubscriber subscriber =
                new RedisModelRegistryRefreshSubscriber(refresher, message -> latch.countDown());
        container.addMessageListener(subscriber, new ChannelTopic("nexa.model.registry.changed"));
        container.afterPropertiesSet();
        container.start();

        RedisModelRegistryRefreshMessageClient publisher = new RedisModelRegistryRefreshMessageClient(factory);
        publisher.publish("nexa.model.registry.changed", new ModelRegistryChangedMessage(10L));

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        verify(refresher).refreshIfNewer(10L);

        container.stop();
        factory.destroy();
    }

    private RedisStandaloneConfiguration redisConfig() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration();
        configuration.setHostName(REDIS.getHost());
        configuration.setPort(REDIS.getMappedPort(6379));
        return configuration;
    }
}
```

If the current subscriber constructor differs, adapt it so production still uses Spring wiring and tests can inject a latch callback without changing runtime behavior.

- [ ] **Step 4: Extend refresh channel enum**

Ensure `ModelRefreshChannel` contains:

```java
LOCAL,
REDIS_PUB_SUB,
INFRA_MQ
```

`INFRA_MQ` should throw a clear `ServiceException` or log unsupported status if selected before infra messaging is implemented.

- [ ] **Step 5: Implement LOCAL publisher behavior**

In `DefaultModelRegistryChangePublisher.publish`:

```java
// 1. 本地模式直接刷新当前 JVM 快照
if (properties.getRefreshChannel() == ModelRefreshChannel.LOCAL) {
    modelRegistryRefresher.refreshIfNewer(version);
    return;
}

// 2. Redis Pub/Sub 模式发布轻量刷新消息
if (properties.getRefreshChannel() == ModelRefreshChannel.REDIS_PUB_SUB) {
    messageClient.publish(properties.getRefreshTopic(), new ModelRegistryChangedMessage(version));
    return;
}

// 3. INFRA_MQ 当前阶段仅预留
log.warn("模型注册表刷新通道暂未接入 INFRA_MQ，version={}", version);
```

- [ ] **Step 6: Add registry version to route decisions**

Modify `ModelRouteDecision` to include `registryVersion` and provide builder or canonical constructor support.

In `RegistryFirstModelRouter.toDecision`, set:

```java
return new ModelRouteDecision(config.getConfigKey(), profile, fallback,
        routeConfig.getPriority(), routeConfig.getWeight(), routeConfig.getRouteConfigId(),
        config.getConfigId(), modelRegistry.current().versionNo());
```

For local fallback route decisions, set `registryVersion` to `0L`.

- [ ] **Step 7: Version client cache keys**

In each client factory, replace profile-derived cache key with:

```java
private String cacheKey(ModelRouteDecision decision) {
    if (decision.configId() != null) {
        return decision.configId() + ":" + decision.registryVersion();
    }
    return "local:" + decision.profileName() + ":" + Integer.toHexString(decision.profile().hashCode());
}
```

Add a test to `ClientFactoryTest`:

```java
@Test
void clientCacheShouldSeparateSameConfigAcrossRegistryVersions() {
    ChatClientFactory factory = new ChatClientFactory();

    Object versionOne = factory.getChatClient(decision(1001L, 1L));
    Object versionTwo = factory.getChatClient(decision(1001L, 2L));

    assertThat(versionOne).isNotSameAs(versionTwo);
}
```

- [ ] **Step 8: Run tests**

Run:

```powershell
mvn -pl nexa-rag-model -Dtest=ModelRegistryLocalRefreshTest,RedisModelRegistryRefreshIntegrationTest,ClientFactoryTest test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 9: Commit**

```powershell
git add nexa-rag-model/pom.xml `
        nexa-rag-model/src/main/java/com/nexarag/model/enums/ModelRefreshChannel.java `
        nexa-rag-model/src/main/java/com/nexarag/model/config/ModelRegistryRefreshProperties.java `
        nexa-rag-model/src/main/java/com/nexarag/model/refresh `
        nexa-rag-model/src/main/java/com/nexarag/model/route `
        nexa-rag-model/src/main/java/com/nexarag/model/client `
        nexa-rag-model/src/test/java/com/nexarag/model/refresh `
        nexa-rag-model/src/test/java/com/nexarag/model/client/ClientFactoryTest.java
git commit -m "feat(model): 完善注册表刷新与客户端缓存"
```

---

## Task 5: TimeLimiter And Stream Fallback Observability

**Files:**
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/governance/ModelGovernanceExecutor.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/execution/ModelExecutionTemplate.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/service/ModelCallLogService.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/service/impl/ModelCallLogServiceImpl.java`
- Test: `nexa-rag-model/src/test/java/com/nexarag/model/governance/ModelGovernanceExecutorTest.java`
- Test: `nexa-rag-model/src/test/java/com/nexarag/model/execution/ModelExecutionTemplateStreamTest.java`

- [ ] **Step 1: Write TimeLimiter test**

Add to `ModelGovernanceExecutorTest`:

```java
@Test
void timeLimiterShouldTimeoutSlowSynchronousCall() {
    ModelGovernanceExecutor executor = new ModelGovernanceExecutor();
    ModelGovernanceSettings settings = ModelGovernanceSettings.builder()
            .timeLimiterEnabled(Boolean.TRUE)
            .timeLimiterTimeoutMs(50)
            .build();

    assertThatThrownBy(() -> executor.execute("slow-chat", settings, () -> {
        Thread.sleep(200);
        return "ok";
    })).isInstanceOf(Exception.class);
}
```

- [ ] **Step 2: Write stream fallback tests**

Create `ModelExecutionTemplateStreamTest`:

```java
package com.nexarag.model.execution;

import com.nexarag.model.entity.ModelCallLog;
import com.nexarag.model.enums.ModelCallStatus;
import com.nexarag.model.gateway.chat.ChatModelStreamResponse;
import com.nexarag.model.route.ModelRoutePlan;
import com.nexarag.model.route.ModelRouter;
import com.nexarag.model.service.ModelCallLogService;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 模型执行模板流式调用测试。
 */
class ModelExecutionTemplateStreamTest {

    @Test
    void streamShouldFallbackBeforeFirstChunk() {
        ModelRouter router = mock(ModelRouter.class);
        ModelCallLogService logService = mock(ModelCallLogService.class);
        when(router.plan(any())).thenReturn(new ModelRoutePlan("chat", null, List.of(primary(), backup())));
        when(logService.createRunningLog(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(ModelCallLog.builder().callId("call-1").build())
                .thenReturn(ModelCallLog.builder().callId("call-2").build());

        ModelExecutionTemplate template = new ModelExecutionTemplate(router, logService);

        ModelExecutionCommand<Flux<ChatModelStreamResponse>> command = streamCommand(decision -> {
            if ("primary".equals(decision.profileName())) {
                return Flux.error(new RuntimeException("主模型连接失败"));
            }
            return Flux.just(new ChatModelStreamResponse("hello", false));
        });

        StepVerifier.create(template.executeStream(command))
                .expectNextMatches(chunk -> "hello".equals(chunk.content()))
                .verifyComplete();

        verify(logService).markFailed(eq("call-1"), any(), contains("主模型连接失败"), anyLong());
        verify(logService).markSuccess(eq("call-2"), anyInt(), anyInt(), anyInt(), anyLong());
    }

    @Test
    void streamShouldNotFallbackAfterFirstChunk() {
        ModelRouter router = mock(ModelRouter.class);
        ModelCallLogService logService = mock(ModelCallLogService.class);
        when(router.plan(any())).thenReturn(new ModelRoutePlan("chat", null, List.of(primary(), backup())));
        when(logService.createRunningLog(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(ModelCallLog.builder().callId("call-1").build());

        ModelExecutionTemplate template = new ModelExecutionTemplate(router, logService);
        ModelExecutionCommand<Flux<ChatModelStreamResponse>> command = streamCommand(decision ->
                Flux.just(new ChatModelStreamResponse("partial", false))
                        .concatWith(Flux.error(new RuntimeException("输出后失败"))));

        StepVerifier.create(template.executeStream(command))
                .expectNextMatches(chunk -> "partial".equals(chunk.content()))
                .expectErrorMessage("输出后失败")
                .verify();

        verify(logService, never()).createRunningLog(any(), any(), any(), eq("backup"), any(), any(), any(), any(), any(), any(), any());
        verify(logService).markFailed(eq("call-1"), any(), contains("输出后失败"), anyLong());
    }
}
```

Fill helper methods `primary()`、`backup()`、`streamCommand(...)` using existing `ModelExecutionTemplateFallbackTest` patterns.

- [ ] **Step 3: Run failing tests**

```powershell
mvn -pl nexa-rag-model -Dtest=ModelGovernanceExecutorTest,ModelExecutionTemplateStreamTest test
```

Expected: TimeLimiter and stream fallback tests fail.

- [ ] **Step 4: Add TimeLimiter support**

In `ModelGovernanceExecutor`, add Resilience4j TimeLimiter with executor service:

```java
private <T> Supplier<T> decorateTimeLimiter(String configKey, ModelGovernanceSettings settings,
                                            Supplier<T> supplier) {
    if (settings == null || !Boolean.TRUE.equals(settings.getTimeLimiterEnabled())) {
        return supplier;
    }

    // 1. 使用 TimeLimiter 限制同步模型调用最长执行时间
    TimeLimiterConfig config = TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofMillis(defaultIfInvalid(settings.getTimeLimiterTimeoutMs(), 60000)))
            .cancelRunningFuture(true)
            .build();
    TimeLimiter timeLimiter = TimeLimiter.of("model-" + configKey, config);
    return () -> {
        try {
            return timeLimiter.executeFutureSupplier(() -> CompletableFuture.supplyAsync(supplier));
        } catch (Exception exception) {
            throw new ServiceException("模型同步调用超时或被中断: " + configKey,
                    exception, BaseErrorCode.SERVICE_ERROR);
        }
    };
}
```

Call `decorateTimeLimiter` after rate limiter and before executing supplier. Import `TimeLimiter`、`TimeLimiterConfig`、`CompletableFuture` and existing exception classes.

- [ ] **Step 5: Add stream execution state handling**

In `ModelExecutionTemplate.executeStream`:

1. Iterate candidates from `ModelRoutePlan`.
2. For each candidate, create log.
3. Create Flux.
4. Wait for first chunk with configured first chunk timeout.
5. If first chunk fails before emission, mark failed or timeout and try next candidate.
6. Once first chunk is emitted, concatenate it with remaining stream and do not fallback.
7. Track chunk count, output chars, first token latency, estimated tokens.
8. Mark `SUCCESS`、`FAILED`、`TIMEOUT`、`CANCELED`.

Use Reactor operators `materialize()` or `switchOnFirst` to detect first signal. Keep the implementation in focused private methods:

```java
private <T> Flux<T> executeStreamPlan(ModelExecutionCommand<Flux<T>> command, ModelRoutePlan plan)
private <T> Flux<T> attemptStreamBeforeFirstChunk(...)
private <T> Flux<T> observeLockedStream(...)
```

- [ ] **Step 6: Extend log service methods**

Add methods:

```java
void markTimeout(String callId, String errorCode, String errorMessage, long durationMs);

void markCanceled(String callId, long durationMs);

void markStreamSuccess(String callId, Integer promptTokens, Integer completionTokens,
                       Integer totalTokens, TokenUsageSource tokenUsageSource,
                       Long firstTokenLatencyMs, Integer chunkCount,
                       Integer outputCharCount, Integer estimatedOutputTokens,
                       long durationMs);
```

Implement with `lambdaUpdate` and numbered Chinese comments.

- [ ] **Step 7: Run tests**

```powershell
mvn -pl nexa-rag-model -Dtest=ModelGovernanceExecutorTest,ModelExecutionTemplateStreamTest,ModelExecutionTemplateFallbackTest test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 8: Commit**

```powershell
git add nexa-rag-model/src/main/java/com/nexarag/model/governance `
        nexa-rag-model/src/main/java/com/nexarag/model/execution `
        nexa-rag-model/src/main/java/com/nexarag/model/service `
        nexa-rag-model/src/test/java/com/nexarag/model/governance `
        nexa-rag-model/src/test/java/com/nexarag/model/execution
git commit -m "feat(model): 增强模型执行超时与流式观测"
```

---

## Task 6: Management CRUD, Reference Protection, And Provider Catalog

**Files:**
- Modify/Create controllers and DTOs listed in the file map.
- Modify service implementations for config, route, route config, governance config, provider catalog.
- Test: controller and service tests under `nexa-rag-model/src/test/java/com/nexarag/model/controller` and `service/impl`.

- [ ] **Step 1: Write reference protection tests**

Add to `ModelConfigServiceImplTest`:

```java
@Test
void deleteConfigShouldFailWhenReferencedByRouteConfig() {
    ModelRouteConfigService routeConfigService = mock(ModelRouteConfigService.class);
    when(routeConfigService.existsByConfigId(1001L)).thenReturn(true);
    ModelConfigServiceImpl service = serviceWith(routeConfigService);

    assertThatThrownBy(() -> service.deleteConfig(1001L))
            .isInstanceOf(ClientException.class)
            .hasMessageContaining("请先从路由中移除该模型配置");
}
```

Add to `ModelRouteServiceImplTest`:

```java
@Test
void deleteRouteShouldFailWhenRouteHasCandidates() {
    ModelRouteConfigService routeConfigService = mock(ModelRouteConfigService.class);
    when(routeConfigService.existsByRouteId(2001L)).thenReturn(true);
    ModelRouteServiceImpl service = serviceWith(routeConfigService);

    assertThatThrownBy(() -> service.deleteRoute(2001L))
            .isInstanceOf(ClientException.class)
            .hasMessageContaining("请先移除路由下的模型配置");
}
```

- [ ] **Step 2: Write REST controller tests**

Add controller tests for:

```text
GET    /api/model/configs
POST   /api/model/configs
PATCH  /api/model/configs/{configId}/enabled
DELETE /api/model/configs/{configId}
POST   /api/model/configs/{configId}/connection-tests
GET    /api/model/routes
POST   /api/model/routes
POST   /api/model/routes/{routeId}/connection-tests
POST   /api/model/governance-configs/{governanceId}/reset-default
POST   /api/model/registry/refresh
GET    /api/model/registry/snapshot
GET    /api/model/providers/catalog
```

Use existing `ModelConfigControllerTest` and `ModelProviderControllerTest` style. Expected JSON should use the project `Results` wrapper.

- [ ] **Step 3: Run failing tests**

```powershell
mvn -pl nexa-rag-model -Dtest=ModelConfigServiceImplTest,ModelRouteServiceImplTest,ModelConfigControllerTest,ModelRouteControllerTest,ModelProviderControllerTest test
```

Expected: missing endpoint or missing reference protection failures.

- [ ] **Step 4: Implement service CRUD and reference protection**

Use MyBatis-Plus `lambdaQuery` and `lambdaUpdate`.

For config deletion:

```java
// 1. 禁止删除仍被路由候选引用的模型配置
if (modelRouteConfigService.existsByConfigId(configId)) {
    throw new ClientException("模型配置仍被路由引用，请先从路由中移除该模型配置", BaseErrorCode.PARAM_ERROR);
}
```

For route deletion:

```java
// 1. 禁止删除仍包含候选模型的路由
if (modelRouteConfigService.existsByRouteId(routeId)) {
    throw new ClientException("模型路由仍存在候选配置，请先移除路由下的模型配置", BaseErrorCode.PARAM_ERROR);
}
```

Every runtime-affecting mutation must call version bump + refresh publish.

- [ ] **Step 5: Implement controllers**

Controller class JavaDoc example:

```java
/**
 * 模型治理配置管理接口，负责治理配置查询、修改、启停和恢复默认策略。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/model/governance-configs")
public class ModelGovernanceConfigController {
}
```

Use REST paths from the design. Keep action endpoints as child resources.

- [ ] **Step 6: Enhance provider catalog**

`ModelProviderCatalogServiceImpl` response must include:

- provider
- displayName
- openAiCompatible
- supportedModelTypes
- defaultEndpointPath
- recommendedModels
- defaultGovernanceDescription

Add a test:

```java
@Test
void catalogShouldExposeDefaultGovernanceDescription() {
    List<ModelProviderCatalogResponse> catalog = service.listCatalog();

    assertThat(catalog).anySatisfy(provider ->
            assertThat(provider.defaultGovernanceDescription()).contains("默认治理"));
}
```

- [ ] **Step 7: Run tests**

```powershell
mvn -pl nexa-rag-model -Dtest=*ControllerTest,*ServiceImplTest,ModelProviderCatalogServiceImplTest test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 8: Commit**

```powershell
git add nexa-rag-model/src/main/java/com/nexarag/model/controller `
        nexa-rag-model/src/main/java/com/nexarag/model/dto `
        nexa-rag-model/src/main/java/com/nexarag/model/service `
        nexa-rag-model/src/test/java/com/nexarag/model/controller `
        nexa-rag-model/src/test/java/com/nexarag/model/service/impl
git commit -m "feat(model): 补齐模型管理接口"
```

---

## Task 7: DashScope TokenUsageStatistics

**Files:**
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/usage/TokenUsage.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/usage/TokenUsageStatistics.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/usage/TokenUsageStatisticsDispatcher.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/usage/DashScopeTokenUsageStatistics.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/usage/DefaultUnknownTokenUsageStatistics.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/provider/RerankProvider.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/provider/ChatProvider.java`
- Test: `nexa-rag-model/src/test/java/com/nexarag/model/usage/DashScopeTokenUsageStatisticsTest.java`
- Test: existing provider tests.

- [ ] **Step 1: Write DashScope token usage tests**

Create `DashScopeTokenUsageStatisticsTest`:

```java
package com.nexarag.model.usage;

import com.nexarag.model.enums.ModelProvider;
import com.nexarag.model.enums.ModelType;
import com.nexarag.model.enums.TokenUsageSource;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DashScope Token 用量统计测试。
 */
class DashScopeTokenUsageStatisticsTest {

    @Test
    void shouldReadDashScopeUsageTotalTokens() {
        DashScopeTokenUsageStatistics statistics = new DashScopeTokenUsageStatistics();
        Map<String, Object> rawResponse = Map.of(
                "usage", Map.of(
                        "input_tokens", 12,
                        "output_tokens", 8,
                        "total_tokens", 20
                )
        );

        TokenUsage usage = statistics.calculate(null, rawResponse, null);

        assertThat(usage.promptTokens()).isEqualTo(12);
        assertThat(usage.completionTokens()).isEqualTo(8);
        assertThat(usage.totalTokens()).isEqualTo(20);
        assertThat(usage.source()).isEqualTo(TokenUsageSource.PROVIDER_USAGE);
    }

    @Test
    void shouldSupportDashScopeRerank() {
        DashScopeTokenUsageStatistics statistics = new DashScopeTokenUsageStatistics();

        assertThat(statistics.supports(ModelProvider.DASHSCOPE, ModelType.RERANK)).isTrue();
    }
}
```

- [ ] **Step 2: Run failing tests**

```powershell
mvn -pl nexa-rag-model -Dtest=DashScopeTokenUsageStatisticsTest test
```

Expected: classes do not exist.

- [ ] **Step 3: Implement TokenUsage record**

```java
package com.nexarag.model.usage;

import com.nexarag.model.enums.TokenUsageSource;

/**
 * Token 用量统计结果。
 *
 * @param promptTokens     输入 Token 数
 * @param completionTokens 输出 Token 数
 * @param totalTokens      总 Token 数
 * @param source           统计来源
 * @param estimated        是否为估算值
 */
public record TokenUsage(Integer promptTokens, Integer completionTokens, Integer totalTokens,
                         TokenUsageSource source, boolean estimated) {

    /**
     * 未知 Token 用量。
     */
    public static TokenUsage unknown() {
        return new TokenUsage(0, 0, 0, TokenUsageSource.UNKNOWN, true);
    }
}
```

- [ ] **Step 4: Implement TokenUsageStatistics interface**

```java
package com.nexarag.model.usage;

import com.nexarag.model.enums.ModelProvider;
import com.nexarag.model.enums.ModelType;

/**
 * Token 用量统计适配器。
 */
public interface TokenUsageStatistics {

    /**
     * 判断是否支持指定厂商和模型类型。
     */
    boolean supports(ModelProvider provider, ModelType modelType);

    /**
     * 计算 Token 用量。
     */
    TokenUsage calculate(Object request, Object rawResponse, Object normalizedResponse);
}
```

- [ ] **Step 5: Implement DashScope usage statistics**

Implement map extraction for `usage.input_tokens`、`usage.output_tokens`、`usage.total_tokens` and compatible keys `prompt_tokens`、`completion_tokens`:

```java
private Integer intValue(Map<String, Object> usage, String key) {
    Object value = usage.get(key);
    if (value instanceof Number number) {
        return number.intValue();
    }
    if (value instanceof String text && text.matches("\\d+")) {
        return Integer.parseInt(text);
    }
    return 0;
}
```

If usage is absent, return `TokenUsage.unknown()`.

- [ ] **Step 6: Wire dispatcher into providers**

Inject `TokenUsageStatisticsDispatcher` into `RerankProvider` and use it when raw DashScope response is available. If current provider only creates normalized response without retaining raw response, introduce a private method that extracts token usage before mapping to `RerankModelResponse`.

Set `tokenUsageSource` in model response if response types are extended. If not extending response types in this task, keep token usage inside execution command extractors and add response extension in Task 5 before this task.

- [ ] **Step 7: Run tests**

```powershell
mvn -pl nexa-rag-model -Dtest=DashScopeTokenUsageStatisticsTest,RerankProviderTest,ModelGatewayTest test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 8: Commit**

```powershell
git add nexa-rag-model/src/main/java/com/nexarag/model/usage `
        nexa-rag-model/src/main/java/com/nexarag/model/provider `
        nexa-rag-model/src/test/java/com/nexarag/model/usage `
        nexa-rag-model/src/test/java/com/nexarag/model/provider
git commit -m "feat(model): 新增DashScope Token用量统计"
```

---

## Task 8: Final Verification And Documentation Cleanup

**Files:**
- Modify: `TODO.md`
- Verify: all files touched in previous tasks.

- [ ] **Step 1: Update TODO**

Mark implemented phase three items as complete or narrow them:

```markdown
- [x] 将 `ModelClientFactory` 缓存 Key 从 Profile 维度切换为 `config_id + version`。
- [x] 将 `ModelGovernanceResolver` 接入 `model_governance_config` 表。
- [x] 补齐模型配置、模型路由、路由配置的完整 REST CRUD 接口。
- [x] 接入模型注册表刷新消息的 Redis Pub/Sub 客户端并验证多实例刷新。
- [x] 接入 Resilience4j TimeLimiter。
- [x] 实现流式 Chat Token 基础统计和观测字段。
- [ ] 接入模型注册表刷新消息的真实 INFRA_MQ 客户端。
```

Keep these as pending:

```markdown
- [ ] 设计 `model_call_trace` 聚合表，用于表达一次业务模型调用的整体结果，包括 fallback 成功、最终状态、attempt_count、final_call_id、总耗时等。
- [ ] 实现模型注册表刷新失败自动重试和告警。
- [ ] 实现多实例模型注册表刷新状态观测。
- [ ] 调研并实现 OpenAI、DeepSeek、智谱、火山、百度、腾讯等厂商的 Token 用量统计适配器。
```

- [ ] **Step 2: Run targeted model tests**

```powershell
mvn -pl nexa-rag-model -am test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Run boot architecture and startup tests**

```powershell
mvn -pl nexa-rag-boot -am test -Dtest=ModuleDependencyTest,NexaRagApplicationTest "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Run full test suite**

```powershell
mvn clean test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Check Git status**

```powershell
git status --short --branch
```

Expected: only intentional changes are present before final commit; no generated `target` files are staged.

- [ ] **Step 6: Commit final docs cleanup**

```powershell
git add TODO.md
git commit -m "docs(model): 更新模型底座实施状态"
```

If `TODO.md` has no changes, skip this commit.

---

## Self-Review Checklist

- [ ] Spec coverage: every confirmed requirement maps to a task above.
- [ ] Placeholder scan: plan contains no `TBD` or vague implementation placeholders.
- [ ] Type consistency: enum names, field names and method names are consistent across tasks.
- [ ] Testing coverage: LOCAL and REDIS_PUB_SUB refresh are both tested; INFRA_MQ is reserved only.
- [ ] SQL coverage: Flyway migration and full schema SQL are both included.
- [ ] User constraints: Chinese comments/logs, JavaDoc for classes, scoped edits, MyBatis-Plus lambda operations.
