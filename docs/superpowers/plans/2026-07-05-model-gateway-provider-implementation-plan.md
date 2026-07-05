# 模型网关真实 Provider 接入 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `nexa-rag-model` 内完成模型配置落库、统一 `ModelGateway`、Embedding/Rerank 真实 Provider、模型连接测试与注册表刷新闭环。

**Architecture:** `ModelGateway` 作为业务唯一门面，内部通过 `ModelExecutionTemplate`、`ModelRouter`、`ModelProviderDispatcher` 分发到 Provider Adapter。模型配置从 DB 加载为不可变 `ModelRegistrySnapshot`，刷新消息通过 MQ 或 Redis PubSub 通知各实例重载快照并清理动态客户端缓存。

**Tech Stack:** Java 21、Spring Boot 3.5、MyBatis-Plus、Flyway、Spring AI OpenAI、Spring AI Alibaba DashScope、AES-GCM、JUnit 5、Mockito、AssertJ。

---

## 文件结构

### 数据库与实体

- Create: `nexa-rag-boot/src/main/resources/db/migration/V4__add_model_registry_schema.sql`
  - 新增 `model_config`、`model_route`、`model_route_config`、`model_registry_version`。
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/entity/ModelConfig.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/entity/ModelRoute.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/entity/ModelRouteConfig.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/entity/ModelRegistryVersion.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/mapper/ModelConfigMapper.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/mapper/ModelRouteMapper.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/mapper/ModelRouteConfigMapper.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/mapper/ModelRegistryVersionMapper.java`

### 枚举与 DTO

- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/enums/ModelProvider.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/enums/ModelBizType.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/enums/ModelRequestType.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/enums/ModelType.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/enums/ModelRouteStrategy.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/enums/ModelRouteRole.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/dto/ModelConfigCreateRequest.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/dto/ModelConfigUpdateRequest.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/dto/ModelConfigResponse.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/dto/ModelRouteCreateRequest.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/dto/ModelRouteUpdateRequest.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/dto/ModelRouteResponse.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/dto/ModelRouteConfigCreateRequest.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/dto/ModelRouteConfigUpdateRequest.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/dto/ModelRouteConfigResponse.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/dto/ModelConnectionTestRequest.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/dto/ModelConnectionTestResponse.java`

### 安全、注册表与刷新

- Create: `nexa-rag-model/src/main/java/com/nexarag/model/security/ModelSecretEncryptor.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/registry/ModelRegistry.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/registry/ModelRegistrySnapshot.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/registry/ModelRegistryRefresher.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/refresh/ModelRegistryChangePublisher.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/refresh/ModelRegistryChangeListener.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/refresh/ModelRegistryChangedMessage.java`

### Gateway 与 Provider

- Delete: `nexa-rag-model/src/main/java/com/nexarag/model/gateway/chat/ChatModelGateway.java`
- Delete: `nexa-rag-model/src/main/java/com/nexarag/model/gateway/embedding/EmbeddingModelGateway.java`
- Delete: `nexa-rag-model/src/main/java/com/nexarag/model/gateway/rerank/RerankModelGateway.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/gateway/ModelGateway.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/provider/ModelProviderAdapter.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/provider/ModelProviderDispatcher.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/provider/openai/OpenAiEmbeddingProvider.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/provider/dashscope/DashScopeRerankProvider.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/client/ModelClientFactory.java`

### Service 与 Controller

- Create: `nexa-rag-model/src/main/java/com/nexarag/model/service/ModelConfigService.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/service/ModelRouteService.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/service/ModelRouteConfigService.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/service/ModelProviderCatalogService.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/service/ModelConnectionTestService.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/controller/ModelConfigController.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/controller/ModelRouteController.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/controller/ModelProviderController.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/controller/ModelRegistryController.java`

### 配置与 TODO

- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/config/ModelConfiguration.java`
- Modify: `nexa-rag-model/pom.xml`
- Modify: `TODO.md`

---

## Task 1: 数据库 schema、实体、枚举

**Files:**
- Create: `nexa-rag-boot/src/main/resources/db/migration/V4__add_model_registry_schema.sql`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/entity/ModelConfig.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/entity/ModelRoute.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/entity/ModelRouteConfig.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/entity/ModelRegistryVersion.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/enums/ModelType.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/enums/ModelRouteStrategy.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/enums/ModelRouteRole.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/enums/ModelProvider.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/enums/ModelBizType.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/enums/ModelRequestType.java`
- Test: `nexa-rag-model/src/test/java/com/nexarag/model/entity/ModelRegistryEntityTest.java`

- [ ] **Step 1: Write failing entity enum test**

Create `nexa-rag-model/src/test/java/com/nexarag/model/entity/ModelRegistryEntityTest.java`:

```java
package com.nexarag.model.entity;

import com.nexarag.model.enums.ModelBizType;
import com.nexarag.model.enums.ModelProvider;
import com.nexarag.model.enums.ModelRequestType;
import com.nexarag.model.enums.ModelRouteRole;
import com.nexarag.model.enums.ModelRouteStrategy;
import com.nexarag.model.enums.ModelType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模型注册表实体与枚举测试。
 */
class ModelRegistryEntityTest {

    @Test
    void enumsShouldContainInitialModelRegistryValues() {
        assertThat(ModelType.values()).extracting(Enum::name)
                .contains("CHAT", "EMBEDDING", "RERANK");
        assertThat(ModelProvider.values()).extracting(Enum::name)
                .contains("OPENAI", "OLLAMA", "DASHSCOPE", "DEEPSEEK",
                        "SILICONFLOW", "ZHIPU", "MOONSHOT", "CUSTOM_OPENAI");
        assertThat(ModelRouteStrategy.values()).extracting(Enum::name)
                .contains("PRIMARY_BACKUP");
        assertThat(ModelRouteRole.values()).extracting(Enum::name)
                .contains("PRIMARY", "BACKUP", "CANDIDATE");
        assertThat(ModelBizType.values()).extracting(Enum::name)
                .contains("MODEL_TEST");
        assertThat(ModelRequestType.values()).extracting(Enum::name)
                .contains("EMBEDDING_TEST", "RERANK_TEST", "CHAT_TEST");
    }

    @Test
    void modelConfigShouldRepresentCallableModel() {
        ModelConfig config = new ModelConfig();
        config.setConfigId(1L);
        config.setConfigKey("embedding.ollama");
        config.setModelType(ModelType.EMBEDDING);
        config.setProvider(ModelProvider.OLLAMA);
        config.setBaseUrl("http://localhost:11434/v1");
        config.setModelName("nomic-embed-text");
        config.setEnabled(Boolean.TRUE);
        config.setVersion(1L);
        config.setCreateTime(LocalDateTime.now());

        assertThat(config.getConfigId()).isEqualTo(1L);
        assertThat(config.getProvider()).isEqualTo(ModelProvider.OLLAMA);
        assertThat(config.getModelType()).isEqualTo(ModelType.EMBEDDING);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -pl nexa-rag-model -am -Dtest=ModelRegistryEntityTest "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: compile failure because `ModelConfig`、`ModelType`、`ModelRouteStrategy`、`ModelRouteRole` do not exist and enum values are missing.

- [ ] **Step 3: Add enums**

Create `nexa-rag-model/src/main/java/com/nexarag/model/enums/ModelType.java`:

```java
package com.nexarag.model.enums;

/**
 * 模型类型枚举，用于区分聊天、向量化和重排序模型。
 */
public enum ModelType {

    /**
     * 聊天模型。
     */
    CHAT,

    /**
     * 向量化模型。
     */
    EMBEDDING,

    /**
     * 重排序模型。
     */
    RERANK
}
```

Create `nexa-rag-model/src/main/java/com/nexarag/model/enums/ModelRouteStrategy.java`:

```java
package com.nexarag.model.enums;

/**
 * 模型路由策略枚举。
 */
public enum ModelRouteStrategy {

    /**
     * 主备路由策略。
     */
    PRIMARY_BACKUP
}
```

Create `nexa-rag-model/src/main/java/com/nexarag/model/enums/ModelRouteRole.java`:

```java
package com.nexarag.model.enums;

/**
 * 路由下模型配置角色枚举。
 */
public enum ModelRouteRole {

    /**
     * 主模型配置。
     */
    PRIMARY,

    /**
     * 备用模型配置。
     */
    BACKUP,

    /**
     * 候选模型配置，后续用于权重或规则路由。
     */
    CANDIDATE
}
```

Modify `ModelProvider` to contain:

```java
package com.nexarag.model.enums;

/**
 * 模型厂商枚举。
 */
public enum ModelProvider {

    /**
     * OpenAI 官方服务。
     */
    OPENAI,

    /**
     * Ollama 本地或远程服务。
     */
    OLLAMA,

    /**
     * 阿里云 DashScope 服务。
     */
    DASHSCOPE,

    /**
     * DeepSeek 服务。
     */
    DEEPSEEK,

    /**
     * SiliconFlow 服务。
     */
    SILICONFLOW,

    /**
     * 智谱 AI 服务。
     */
    ZHIPU,

    /**
     * Moonshot 服务。
     */
    MOONSHOT,

    /**
     * 自定义 OpenAI 兼容服务。
     */
    CUSTOM_OPENAI
}
```

Modify `ModelBizType` to include `MODEL_TEST`; modify `ModelRequestType` to include `EMBEDDING_TEST`、`RERANK_TEST`、`CHAT_TEST`.

- [ ] **Step 4: Add entities and mappers**

Create entity classes with MyBatis-Plus annotations, Lombok `@Getter`/`@Setter`, class JavaDoc, field comments, and `@TableName`:

```java
@Getter
@Setter
@TableName("model_config")
public class ModelConfig {
    @TableId("config_id")
    private Long configId;
    private String configKey;
    private ModelType modelType;
    private ModelProvider provider;
    private String baseUrl;
    private String apiKeyCipher;
    private String apiKeyMask;
    private String modelName;
    private Boolean enabled;
    private Integer timeoutMs;
    private Integer maxRetries;
    private Long version;
    private String extraConfig;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Boolean delFlag;
    private LocalDateTime deleteTime;
}
```

Create analogous classes for `ModelRoute`、`ModelRouteConfig`、`ModelRegistryVersion`.

Create mapper interfaces:

```java
public interface ModelConfigMapper extends BaseMapper<ModelConfig> {
}
```

Repeat for the other three entities.

- [ ] **Step 5: Add Flyway migration**

Create `V4__add_model_registry_schema.sql` with four tables and indexes:

```sql
CREATE TABLE IF NOT EXISTS model_config (
    config_id BIGINT NOT NULL COMMENT '模型配置ID',
    config_key VARCHAR(128) NOT NULL COMMENT '模型配置唯一key',
    model_type VARCHAR(32) NOT NULL COMMENT '模型类型',
    provider VARCHAR(64) NOT NULL COMMENT '模型厂商',
    base_url VARCHAR(512) NOT NULL COMMENT '模型服务地址',
    api_key_cipher VARCHAR(1024) NULL COMMENT '加密后的API Key',
    api_key_mask VARCHAR(128) NULL COMMENT 'API Key脱敏值',
    model_name VARCHAR(128) NOT NULL COMMENT '模型名称',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    timeout_ms INT NOT NULL DEFAULT 30000 COMMENT '超时时间毫秒',
    max_retries INT NOT NULL DEFAULT 0 COMMENT '最大重试次数',
    version BIGINT NOT NULL DEFAULT 1 COMMENT '单条配置版本',
    extra_config TEXT NULL COMMENT '扩展配置JSON',
    remark VARCHAR(512) NULL COMMENT '备注',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NULL COMMENT '更新时间',
    del_flag TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    delete_time DATETIME NULL COMMENT '删除时间',
    PRIMARY KEY (config_id),
    UNIQUE KEY uk_model_config_key (config_key),
    KEY idx_model_config_type_provider (model_type, provider)
) COMMENT='模型配置表';
```

Add `model_route`、`model_route_config`、`model_registry_version` in the same migration.

- [ ] **Step 6: Run entity test**

Run:

```bash
mvn -pl nexa-rag-model -am -Dtest=ModelRegistryEntityTest "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add nexa-rag-boot/src/main/resources/db/migration/V4__add_model_registry_schema.sql nexa-rag-model/src/main/java/com/nexarag/model/entity nexa-rag-model/src/main/java/com/nexarag/model/enums nexa-rag-model/src/main/java/com/nexarag/model/mapper nexa-rag-model/src/test/java/com/nexarag/model/entity/ModelRegistryEntityTest.java
git commit -m "feat(model): 新增模型注册表数据结构"
```

## Task 2: API Key 加密与脱敏

**Files:**
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/security/ModelSecretEncryptor.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/config/ModelSecretProperties.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/config/ModelConfiguration.java`
- Test: `nexa-rag-model/src/test/java/com/nexarag/model/security/ModelSecretEncryptorTest.java`

- [ ] **Step 1: Write failing encryptor test**

Create `ModelSecretEncryptorTest`:

```java
package com.nexarag.model.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 模型密钥加密器测试。
 */
class ModelSecretEncryptorTest {

    @Test
    void shouldEncryptDecryptAndMaskApiKey() {
        ModelSecretEncryptor encryptor = new ModelSecretEncryptor("0123456789abcdef0123456789abcdef");

        String cipher = encryptor.encrypt("sk-test-abcdef");
        String raw = encryptor.decrypt(cipher);
        String mask = encryptor.mask("sk-test-abcdef");

        assertThat(cipher).isNotEqualTo("sk-test-abcdef");
        assertThat(raw).isEqualTo("sk-test-abcdef");
        assertThat(mask).isEqualTo("sk-****cdef");
    }

    @Test
    void shouldRejectBlankMasterKey() {
        assertThatThrownBy(() -> new ModelSecretEncryptor(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("模型密钥主密钥不能为空");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn -pl nexa-rag-model -am -Dtest=ModelSecretEncryptorTest "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: compile failure because encryptor does not exist.

- [ ] **Step 3: Implement AES-GCM encryptor**

Create `ModelSecretEncryptor`:

```java
package com.nexarag.model.security;

import com.nexarag.common.exception.ServiceException;
import com.nexarag.common.result.BaseErrorCode;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 模型密钥加密器，负责 API Key 的 AES-GCM 加密、解密和脱敏。
 */
public class ModelSecretEncryptor {

    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128;
    private final SecretKeySpec secretKeySpec;
    private final SecureRandom secureRandom = new SecureRandom();

    public ModelSecretEncryptor(String masterKey) {
        if (masterKey == null || masterKey.isBlank()) {
            throw new IllegalArgumentException("模型密钥主密钥不能为空");
        }
        this.secretKeySpec = new SecretKeySpec(masterKey.getBytes(StandardCharsets.UTF_8), "AES");
    }

    public String encrypt(String rawSecret) {
        if (rawSecret == null || rawSecret.isBlank()) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, new GCMParameterSpec(TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(rawSecret.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(payload);
        } catch (Exception exception) {
            throw new ServiceException("模型密钥加密失败", exception, BaseErrorCode.SERVICE_ERROR);
        }
    }

    public String decrypt(String cipherSecret) {
        if (cipherSecret == null || cipherSecret.isBlank()) {
            return null;
        }
        try {
            byte[] payload = Base64.getDecoder().decode(cipherSecret);
            byte[] iv = new byte[IV_LENGTH];
            byte[] encrypted = new byte[payload.length - IV_LENGTH];
            System.arraycopy(payload, 0, iv, 0, IV_LENGTH);
            System.arraycopy(payload, IV_LENGTH, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, new GCMParameterSpec(TAG_LENGTH, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new ServiceException("模型密钥解密失败", exception, BaseErrorCode.SERVICE_ERROR);
        }
    }

    public String mask(String rawSecret) {
        if (rawSecret == null || rawSecret.isBlank()) {
            return null;
        }
        if (rawSecret.length() <= 4) {
            return "****";
        }
        String prefix = rawSecret.startsWith("sk-") ? "sk-" : "";
        String suffix = rawSecret.substring(rawSecret.length() - 4);
        return prefix + "****" + suffix;
    }
}
```

- [ ] **Step 4: Add properties and bean**

Create `ModelSecretProperties` with prefix `nexa.model.secret` and field `masterKey`. Register `ModelSecretEncryptor` in `ModelConfiguration`.

- [ ] **Step 5: Run encryptor test**

```bash
mvn -pl nexa-rag-model -am -Dtest=ModelSecretEncryptorTest "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add nexa-rag-model/src/main/java/com/nexarag/model/security nexa-rag-model/src/main/java/com/nexarag/model/config nexa-rag-model/src/test/java/com/nexarag/model/security/ModelSecretEncryptorTest.java
git commit -m "feat(model): 新增模型密钥加密器"
```

## Task 3: 模型配置与路由 Service

**Files:**
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/service/ModelConfigService.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/service/impl/ModelConfigServiceImpl.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/service/ModelRouteService.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/service/impl/ModelRouteServiceImpl.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/service/ModelRouteConfigService.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/service/impl/ModelRouteConfigServiceImpl.java`
- Create: request/response classes under `nexa-rag-model/src/main/java/com/nexarag/model/dto`
- Test: `nexa-rag-model/src/test/java/com/nexarag/model/service/impl/ModelConfigServiceImplTest.java`

- [ ] **Step 1: Write failing service test**

Create a Mockito test asserting create encrypts API Key and update preserves old secret when request omits `apiKey`.

Run:

```bash
mvn -pl nexa-rag-model -am -Dtest=ModelConfigServiceImplTest "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: compile failure because services and DTOs do not exist.

- [ ] **Step 2: Implement DTOs**

Create `ModelConfigCreateRequest`、`ModelConfigUpdateRequest`、`ModelConfigResponse` with JavaDoc and validation annotations:

```java
public record ModelConfigCreateRequest(
        String configKey,
        ModelType modelType,
        ModelProvider provider,
        String baseUrl,
        String apiKey,
        String modelName,
        Integer timeoutMs,
        Integer maxRetries,
        String extraConfig,
        String remark
) {
}
```

Add analogous route and route-config request/response records.

- [ ] **Step 3: Implement services**

Each service interface extends `IService<Entity>`. Each implementation extends `ServiceImpl<Mapper, Entity>`.

`ModelConfigServiceImpl.createConfig`:

```java
// 1. 校验 configKey 唯一。
// 2. 加密 API Key 并生成脱敏值。
// 3. 构建模型配置实体。
// 4. 保存模型配置。
// 5. 返回脱敏响应。
```

Use `lambdaQuery` and `lambdaUpdate` for uniqueness and update checks.

- [ ] **Step 4: Run service tests**

```bash
mvn -pl nexa-rag-model -am -Dtest=ModelConfigServiceImplTest "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add nexa-rag-model/src/main/java/com/nexarag/model/dto nexa-rag-model/src/main/java/com/nexarag/model/service nexa-rag-model/src/test/java/com/nexarag/model/service/impl/ModelConfigServiceImplTest.java
git commit -m "feat(model): 新增模型配置管理服务"
```

## Task 4: ModelRegistry 快照与刷新器

**Files:**
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/registry/ModelRegistry.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/registry/ModelRegistrySnapshot.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/registry/ModelRegistryRefresher.java`
- Test: `nexa-rag-model/src/test/java/com/nexarag/model/registry/ModelRegistryTest.java`

- [ ] **Step 1: Write failing registry test**

Test that `ModelRegistry.refresh(snapshot)` atomically replaces version and route lookup returns the new route configs.

- [ ] **Step 2: Implement snapshot**

Use immutable collections:

```java
public record ModelRegistrySnapshot(
        long versionNo,
        Map<Long, ModelConfig> configMap,
        Map<Long, ModelRoute> routeMap,
        Map<Long, List<ModelRouteConfig>> routeConfigMap
) {
}
```

- [ ] **Step 3: Implement registry**

Use `AtomicReference<ModelRegistrySnapshot>`. Provide:

```java
public ModelRegistrySnapshot current();
public void replace(ModelRegistrySnapshot snapshot);
public ModelRoute getRoute(Long routeId);
public ModelRoute getRoute(String routeKey);
public ModelConfig getConfig(Long configId);
public List<ModelRouteConfig> getRouteConfigs(Long routeId);
```

- [ ] **Step 4: Implement refresher**

`ModelRegistryRefresher.refreshIfNewer(long remoteVersion)`:

```java
// 1. 比较远端版本和本地版本。
// 2. 远端版本不更新时直接返回。
// 3. 从 DB 加载启用配置、启用路由和启用关联关系。
// 4. 构建不可变快照。
// 5. 原子替换注册表快照。
// 6. 清理模型客户端缓存。
```

- [ ] **Step 5: Run registry tests**

```bash
mvn -pl nexa-rag-model -am -Dtest=ModelRegistryTest "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add nexa-rag-model/src/main/java/com/nexarag/model/registry nexa-rag-model/src/test/java/com/nexarag/model/registry/ModelRegistryTest.java
git commit -m "feat(model): 新增模型注册表快照"
```

## Task 5: 刷新消息抽象与 MQ/PUB_SUB 通道

**Files:**
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/refresh/**`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/config/ModelConfiguration.java`
- Test: `nexa-rag-model/src/test/java/com/nexarag/model/refresh/ModelRegistryChangePublisherTest.java`

- [ ] **Step 1: Write failing publisher test**

Test that publisher publishes a message containing version and topic through selected channel.

- [ ] **Step 2: Add refresh properties**

Create properties:

```java
@ConfigurationProperties(prefix = "nexa.model.registry")
public class ModelRegistryRefreshProperties {
    private ModelRefreshChannel refreshChannel = ModelRefreshChannel.MQ;
    private String refreshTopic = "nexa.model.registry.changed";
}
```

- [ ] **Step 3: Add refresh interfaces**

```java
public interface ModelRegistryChangePublisher {
    void publish(long versionNo);
}
```

```java
public interface ModelRegistryChangeListener {
    void onMessage(ModelRegistryChangedMessage message);
}
```

- [ ] **Step 4: Implement channel adapters**

Create MQ and PubSub implementations behind the same interface. If infra messaging abstraction is not ready, create narrow model-side ports:

```java
public interface ModelRefreshMessageClient {
    void publish(String topic, ModelRegistryChangedMessage message);
}
```

Provide in-memory/no-op test implementation for unit tests; concrete infra adapters can bind later without changing model services.

- [ ] **Step 5: Run refresh tests**

```bash
mvn -pl nexa-rag-model -am -Dtest=ModelRegistryChangePublisherTest "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add nexa-rag-model/src/main/java/com/nexarag/model/refresh nexa-rag-model/src/main/java/com/nexarag/model/config nexa-rag-model/src/test/java/com/nexarag/model/refresh/ModelRegistryChangePublisherTest.java
git commit -m "feat(model): 新增模型注册表刷新消息"
```

## Task 6: Provider 推荐值与 REST 管理接口

**Files:**
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/controller/ModelProviderController.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/controller/ModelConfigController.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/controller/ModelRouteController.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/controller/ModelRegistryController.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/service/ModelProviderCatalogService.java`
- Test: `nexa-rag-model/src/test/java/com/nexarag/model/controller/ModelProviderControllerTest.java`

- [ ] **Step 1: Write failing controller test**

Use `@WebMvcTest(ModelProviderController.class)` and assert `GET /api/model/providers` returns `OPENAI`、`OLLAMA`、`DASHSCOPE`.

- [ ] **Step 2: Implement provider catalog service**

Return hard-coded recommended values. Include provider, displayName, supportedTypes, defaultBaseUrl, recommendedModels, apiKeyRequired.

- [ ] **Step 3: Implement controllers**

Use REST paths from spec:

```text
/api/model/providers
/api/model/configs
/api/model/routes
/api/model/registry
```

Controllers delegate to services only; no business logic in controllers.

- [ ] **Step 4: Run controller tests**

```bash
mvn -pl nexa-rag-model -am -Dtest=ModelProviderControllerTest "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add nexa-rag-model/src/main/java/com/nexarag/model/controller nexa-rag-model/src/main/java/com/nexarag/model/service nexa-rag-model/src/test/java/com/nexarag/model/controller/ModelProviderControllerTest.java
git commit -m "feat(model): 新增模型管理REST接口"
```

## Task 7: 统一 ModelGateway 与 Provider Dispatcher

**Files:**
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/gateway/ModelGateway.java`
- Delete: existing gateway interfaces under `gateway/chat`、`gateway/embedding`、`gateway/rerank`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/provider/ModelProviderAdapter.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/provider/ModelProviderDispatcher.java`
- Modify: request/response package imports if needed
- Test: `nexa-rag-model/src/test/java/com/nexarag/model/gateway/ModelGatewayTest.java`

- [ ] **Step 1: Write failing gateway test**

Test:
- `embedding` delegates to dispatcher through execution template.
- `rerank` delegates to dispatcher through execution template.
- `chat` throws a clear unsupported exception.

- [ ] **Step 2: Implement ModelGateway class**

```java
@Service
@RequiredArgsConstructor
public class ModelGateway {

    private final ModelExecutionTemplate executionTemplate;
    private final ModelProviderDispatcher providerDispatcher;

    public ChatModelResponse chat(ChatModelRequest request) {
        throw new ServiceException("Chat 模型调用暂未支持", BaseErrorCode.SERVICE_ERROR);
    }

    public EmbeddingModelResponse embedding(EmbeddingModelRequest request) {
        return executionTemplate.execute(ModelExecutionCommand.ofEmbedding(request,
                decision -> providerDispatcher.embedding(decision, request)));
    }

    public RerankModelResponse rerank(RerankModelRequest request) {
        return executionTemplate.execute(ModelExecutionCommand.ofRerank(request,
                decision -> providerDispatcher.rerank(decision, request)));
    }
}
```

If `ModelExecutionCommand.ofEmbedding` does not exist yet, add static factory methods in `ModelExecutionCommand`.

- [ ] **Step 3: Implement dispatcher**

`ModelProviderDispatcher` chooses adapter by `provider + modelType`.

- [ ] **Step 4: Run gateway tests**

```bash
mvn -pl nexa-rag-model -am -Dtest=ModelGatewayTest "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add nexa-rag-model/src/main/java/com/nexarag/model/gateway nexa-rag-model/src/main/java/com/nexarag/model/provider nexa-rag-model/src/main/java/com/nexarag/model/execution nexa-rag-model/src/test/java/com/nexarag/model/gateway/ModelGatewayTest.java
git commit -m "refactor(model): 统一模型网关入口"
```

## Task 8: 动态客户端池与 OpenAI Embedding Provider

**Files:**
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/client/ModelClientFactory.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/provider/openai/OpenAiEmbeddingProvider.java`
- Modify: `nexa-rag-model/pom.xml`
- Test: `nexa-rag-model/src/test/java/com/nexarag/model/provider/openai/OpenAiEmbeddingProviderTest.java`

- [ ] **Step 1: Inspect Spring AI OpenAI constructors**

Run:

```bash
jar tf D:/Repository/org/springframework/ai/spring-ai-openai*/**/*.jar
```

If shell glob fails, use `Get-ChildItem` to locate `spring-ai-openai` jars and inspect `OpenAiApi`、`OpenAiEmbeddingModel` constructor signatures with `javap`.

- [ ] **Step 2: Write provider test with fake client**

Design `ModelClientFactory` so provider can be tested with a fake embedding client. Test maps returned float arrays to `EmbeddingModelResponse`.

- [ ] **Step 3: Implement ModelClientFactory**

Cache key:

```text
configId + ":" + version
```

Provide:

```java
public OpenAiEmbeddingModel getOpenAiEmbeddingClient(ModelConfig config, String apiKey);
public DashScopeRerankModel getDashScopeRerankClient(ModelConfig config, String apiKey);
public void clear();
```

- [ ] **Step 4: Implement OpenAiEmbeddingProvider**

Provider:
- supports OpenAI-compatible family provider values and `ModelType.EMBEDDING`
- decrypts API Key through `ModelSecretEncryptor`
- uses placeholder key when decrypted key is blank
- calls Spring AI embedding model
- returns embeddings and token usage when available

- [ ] **Step 5: Run provider tests**

```bash
mvn -pl nexa-rag-model -am -Dtest=OpenAiEmbeddingProviderTest "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add nexa-rag-model/pom.xml nexa-rag-model/src/main/java/com/nexarag/model/client nexa-rag-model/src/main/java/com/nexarag/model/provider/openai nexa-rag-model/src/test/java/com/nexarag/model/provider/openai/OpenAiEmbeddingProviderTest.java
git commit -m "feat(model): 接入OpenAI兼容Embedding"
```

## Task 9: DashScope Rerank Provider

**Files:**
- Modify: `pom.xml`
- Modify: `nexa-rag-model/pom.xml`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/provider/dashscope/DashScopeRerankProvider.java`
- Test: `nexa-rag-model/src/test/java/com/nexarag/model/provider/dashscope/DashScopeRerankProviderTest.java`

- [ ] **Step 1: Add Spring AI Alibaba DashScope dependency**

Add dependency:

```xml
<dependency>
    <groupId>com.alibaba.cloud.ai</groupId>
    <artifactId>spring-ai-alibaba-dashscope</artifactId>
</dependency>
```

If dependency management does not contain it, add the version under parent dependency management using `${spring-ai-alibaba.version}`.

- [ ] **Step 2: Inspect DashScope Rerank API**

Run:

```bash
javap -classpath D:/Repository/com/alibaba/cloud/ai/spring-ai-alibaba-dashscope/1.1.2.0/spring-ai-alibaba-dashscope-1.1.2.0.jar com.alibaba.cloud.ai.dashscope.rerank.DashScopeRerankModel
```

Also inspect `DashScopeRerankOptions`、`RerankRequest`、`RerankResponse`.

- [ ] **Step 3: Write provider test with fake rerank client**

Test that query and candidates map to response scores and preserve candidate IDs.

- [ ] **Step 4: Implement DashScopeRerankProvider**

Provider:
- supports `DASHSCOPE + RERANK`
- decrypts API Key
- uses `model_config.modelName`, default recommendation remains catalog-only
- maps candidate text list to DashScope request
- maps DashScope score response to `RerankModelResponse`

- [ ] **Step 5: Run provider tests**

```bash
mvn -pl nexa-rag-model -am -Dtest=DashScopeRerankProviderTest "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add pom.xml nexa-rag-model/pom.xml nexa-rag-model/src/main/java/com/nexarag/model/provider/dashscope nexa-rag-model/src/test/java/com/nexarag/model/provider/dashscope/DashScopeRerankProviderTest.java
git commit -m "feat(model): 接入DashScope重排序模型"
```

## Task 10: 模型连接测试服务

**Files:**
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/service/ModelConnectionTestService.java`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/service/impl/ModelConnectionTestServiceImpl.java`
- Create: DTOs for test request/response
- Modify: `ModelConfigController`
- Modify: `ModelRouteController`
- Test: `nexa-rag-model/src/test/java/com/nexarag/model/service/impl/ModelConnectionTestServiceImplTest.java`

- [ ] **Step 1: Write failing connection test**

Test:
- config embedding test uses direct config and writes `MODEL_TEST + EMBEDDING_TEST`
- route rerank test uses route selection and writes `MODEL_TEST + RERANK_TEST`
- chat test returns unsupported result

- [ ] **Step 2: Implement test DTOs**

```java
public record ModelConnectionTestRequest(
        String input,
        String query,
        List<String> documents
) {
}
```

```java
public record ModelConnectionTestResponse(
        boolean success,
        ModelProvider provider,
        ModelType modelType,
        String modelName,
        String baseUrl,
        long durationMs,
        Integer vectorDimension,
        Integer rerankCount,
        String errorCode,
        String errorMessage
) {
}
```

- [ ] **Step 3: Implement service**

Default values:
- embedding input: `你好，NexaRAG`
- rerank query: `什么是 RAG？`
- rerank documents: `RAG 是检索增强生成。` and `今天天气很好。`

Use `ModelGateway` so tests run the same path as business calls.

- [ ] **Step 4: Wire controller endpoints**

Add:

```text
POST /api/model/configs/{configId}/test
POST /api/model/routes/{routeId}/test
```

- [ ] **Step 5: Run connection test**

```bash
mvn -pl nexa-rag-model -am -Dtest=ModelConnectionTestServiceImplTest "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add nexa-rag-model/src/main/java/com/nexarag/model/service nexa-rag-model/src/main/java/com/nexarag/model/controller nexa-rag-model/src/main/java/com/nexarag/model/dto nexa-rag-model/src/test/java/com/nexarag/model/service/impl/ModelConnectionTestServiceImplTest.java
git commit -m "feat(model): 新增模型连接测试"
```

## Task 11: 配置装配、TODO 与集成验证

**Files:**
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/config/ModelConfiguration.java`
- Modify: `TODO.md`
- Test: existing model and boot tests

- [ ] **Step 1: Update TODO**

Add unchecked items:
- Chat 真实调用。
- Chat 连接测试。
- 客户端池按 config 精确淘汰。
- 客户端空闲过期清理。
- HTTP 连接池参数按模型配置定制。
- 客户端级指标采集。
- 配置刷新时优雅等待旧客户端请求结束。
- API Key 轮换期间双密钥兼容。
- Provider 推荐值动态配置化。

- [ ] **Step 2: Wire beans**

Ensure `ModelConfiguration` registers:
- `ModelSecretProperties`
- `ModelRegistryRefreshProperties`
- `ModelSecretEncryptor`
- `ModelRegistry`
- `ModelRegistryRefresher`
- `ModelClientFactory`
- `ModelProviderDispatcher`
- provider adapters

- [ ] **Step 3: Run model module tests**

```bash
mvn -pl nexa-rag-model -am test
```

Expected: BUILD SUCCESS.

- [ ] **Step 4: Run boot context and architecture tests**

```bash
mvn -pl nexa-rag-boot -am test -Dtest=NexaRagApplicationTest,ModuleDependencyTest "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: BUILD SUCCESS.

- [ ] **Step 5: Run full test suite**

```bash
mvn clean test
```

Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add TODO.md nexa-rag-model/src/main/java/com/nexarag/model/config
git commit -m "chore(model): 完成模型网关装配与TODO"
```

---

## Self-Review Result

- [x] Spec coverage: tasks cover DB schema, REST 管理接口, API Key 加密, `ModelGateway`, Embedding, Rerank, 连接测试, 注册表刷新, TODO 更新。
- [x] Placeholder scan: no `TBD`, no vague "handle edge cases" without specific behavior.
- [x] Type consistency: `ModelConfig`、`ModelType`、`ModelProvider`、`ModelGateway` names are consistent across tasks.
- [x] Scope check: Chat real call, document/retrieval integration, fallback/circuit breaker remain outside this plan.
