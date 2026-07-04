# Phase 2.5-02 Redis 队列与本地 Worker 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现整条文档入库流水线只排一次的 Redis 公平队列、本地 Worker、租约和排队状态查询。

**Architecture:** 本批采用 `infra Redis 队列原子能力 + document 业务适配 + boot 本地 Worker 装配` 的分层。Redis waiting 队列使用单调递增序号作为 ZSET score，保证按成功入队顺序 FIFO 处理；MySQL 仍保存文档稳定状态，Redis 只保存实时队列态和租约。Worker 只调用 `DocumentPipelineExecutor` 接口，本批提供占位执行器，后续 Workflow Graph 批次替换为真实执行器。

**Tech Stack:** Java 21、Spring Boot 3.5.x、Spring Data Redis、Maven 多模块、JUnit 5、AssertJ、Mockito。

---

## 1. 设计边界

### 1.1 本批包含

- Redis waiting/running/lease/retry/sequence key 设计与实现。
- 整条文档流水线只入队一次的公平 FIFO 队列。
- `DocumentProcessTaskDispatcher` 的 Redis 实现。
- 本地 Worker 线程池轮询、租约获取、成功 ack、失败 release。
- `/api/documents/{documentId}/process-status` 增强返回队列位置、等待人数、运行状态和租约剩余时间。
- 默认单元测试不访问真实 Redis，通过内存假实现或 mock 验证行为。
- 真实 Redis 冒烟验证可以访问 `192.168.0.134:6379`，密码通过命令参数或环境变量注入，不写入仓库。

### 1.2 本批不包含

- 阶段级队列完整实现。
- MQ、Redis Stream 或多实例强一致协议。
- 真实解析、切分、索引和 Workflow Graph。
- 补偿任务扫描 running 租约缺失后的自动恢复。
- 真实 Redis 集成测试默认启用。

### 1.3 公平队列规则

- 队列公平性定义为：按任务成功入队顺序 FIFO 处理。
- Redis ZSET `score` 不使用毫秒时间戳，而使用 `INCR nexa:document:pipeline:sequence` 生成的单调递增序号。
- 同一毫秒内多个上传请求也会得到不同序号，因此顺序稳定。
- `enqueueTime` 只写入元数据，用于展示和排查，不参与唯一排序。
- 重复入队同一 `documentId` 时：如果任务已在 waiting 或 running 中，返回当前队列状态，不重复追加。
- Worker 获取任务时总是取 waiting ZSET 中 score 最小的任务。

## 2. Redis Key 设计

```text
nexa:document:pipeline:waiting                 # ZSET，member=documentId，score=单调递增 sequence
nexa:document:pipeline:running                 # HASH，field=documentId，value=运行态 JSON
nexa:document:pipeline:lease:{documentId}      # STRING，value=leaseToken，带 TTL
nexa:document:pipeline:meta:{documentId}       # HASH，保存 enqueueSequence、enqueueTime、lastWorkerId
nexa:document:pipeline:retry:{documentId}      # STRING，运行态重试次数副本，带 TTL，本批只预留读写能力
nexa:document:pipeline:sequence                # STRING，Redis INCR 生成公平队列序号
```

运行态 JSON 字段：

```json
{
  "documentId": 1,
  "leaseToken": "uuid",
  "workerId": "host-pid-thread",
  "startTime": "2026-07-04T15:00:00",
  "leaseExpireTime": "2026-07-04T15:05:00",
  "enqueueSequence": 100
}
```

## 3. 文件结构与类职责

### 3.1 `nexa-rag-infra`

- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/queue/document/DocumentPipelineQueue.java`
  - Redis 队列能力抽象。
  - 方法：`enqueue`、`poll`、`ack`、`release`、`queryStatus`。
  - 不依赖 document、workflow、retrieval。

- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/queue/document/DocumentPipelineQueueStatus.java`
  - 队列状态结果。
  - 字段：`documentId`、`queuePosition`、`waitingCount`、`running`、`workerId`、`leaseTtlSeconds`、`enqueueSequence`。

- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/queue/document/DocumentPipelineTask.java`
  - Worker 成功获取租约后的任务对象。
  - 字段：`documentId`、`leaseToken`、`workerId`、`enqueueSequence`。

- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/queue/document/DocumentPipelineQueueProperties.java`
  - 队列配置属性。
  - prefix：`nexa.document.pipeline.queue`。
  - 字段：`keyPrefix`、`leaseTtlSeconds`、`retryTtlSeconds`。

- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/queue/document/DocumentPipelineQueueKeys.java`
  - Redis key 生成器，集中维护 key 名称。

- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/queue/document/RedisDocumentPipelineQueue.java`
  - `DocumentPipelineQueue` 的 Redis 实现。
  - 使用 `StringRedisTemplate`。
  - 使用 Lua 脚本保证 enqueue/poll/ack/release 原子性。

- Modify: `nexa-rag-infra/pom.xml`
  - 增加 `spring-boot-starter-data-redis`。

- Test: `nexa-rag-infra/src/test/java/com/nexarag/infra/queue/document/DocumentPipelineQueueKeysTest.java`
  - 验证 key prefix 和各 key 生成正确。

- Test: `nexa-rag-infra/src/test/java/com/nexarag/infra/queue/document/InMemoryDocumentPipelineQueueTest.java`
  - 用内存实现验证公平 FIFO、重复入队幂等、poll/ack/release 语义。
  - 该内存实现只放在测试代码中，避免单元测试访问真实 Redis。

### 3.2 `nexa-rag-document`

- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/service/DocumentQueueInfo.java`
  - 增加字段：`running`、`workerId`、`leaseTtlSeconds`。
  - 保留 `DocumentQueueInfo(Integer queuePosition, Integer waitingCount)` 兼容 01 测试。

- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/vo/UploadDocumentResponse.java`
  - 暂不新增字段，上传接口继续返回 `documentId/status/queuePosition/waitingCount`。

- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/vo/DocumentProcessStatusVO.java`
  - 增加字段：`queuePosition`、`waitingCount`、`running`、`workerId`、`leaseTtlSeconds`。

- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/converter/DocumentConverter.java`
  - 保留 `toProcessStatusVO(Document document)`，新增重载 `toProcessStatusVO(Document document, DocumentQueueInfo queueInfo)`。

- Create: `nexa-rag-document/src/main/java/com/nexarag/document/service/DocumentQueueStatusService.java`
  - 查询文档稳定状态和 Redis 实时队列状态。
  - Redis 状态不存在时返回仅含稳定状态的 VO，不报错。

- Create: `nexa-rag-document/src/main/java/com/nexarag/document/service/impl/DocumentQueueStatusServiceImpl.java`
  - 依赖 `DocumentService` 和 `DocumentPipelineQueue`。

- Create: `nexa-rag-document/src/main/java/com/nexarag/document/service/impl/RedisDocumentProcessTaskDispatcher.java`
  - 替代 01 的本地占位 dispatcher。
  - 调用 `DocumentPipelineQueue.enqueue(documentId)`，映射为 `DocumentQueueInfo`。

- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/service/impl/LocalDocumentProcessTaskDispatcher.java`
  - 删除该类，或移除 `@Service` 并标记为测试/占位不可被 Spring 扫描。
  - 推荐删除，避免 `DocumentProcessTaskDispatcher` 出现多个候选 Bean。

- Create: `nexa-rag-document/src/main/java/com/nexarag/document/service/DocumentPipelineExecutor.java`
  - Worker 执行文档流水线的业务接口。
  - 方法：`void execute(Long documentId)`。
  - 本批只定义接口，不依赖 workflow。

- Create: `nexa-rag-document/src/main/java/com/nexarag/document/service/impl/NoopDocumentPipelineExecutor.java`
  - 占位执行器。
  - 只记录日志：`文档流水线占位执行完成，documentId={}`。
  - 不推进 `PARSING/CHUNKING/INDEXING` 状态。
  - 后续 06 Workflow 批次用真实 Graph 执行器替换。

- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/controller/DocumentController.java`
  - `getProcessStatus` 改为调用 `DocumentQueueStatusService.getProcessStatus(documentId)`。

- Test: `nexa-rag-document/src/test/java/com/nexarag/document/service/impl/RedisDocumentProcessTaskDispatcherTest.java`
  - 验证 dispatcher 调用队列后返回 queuePosition/waitingCount。

- Test: `nexa-rag-document/src/test/java/com/nexarag/document/service/impl/DocumentQueueStatusServiceImplTest.java`
  - 验证 waiting/running/无 Redis 状态三种响应。

### 3.3 `nexa-rag-boot`

- Create: `nexa-rag-boot/src/main/java/com/nexarag/boot/worker/DocumentPipelineWorkerProperties.java`
  - prefix：`nexa.document.pipeline`。
  - 字段：`mode`、`queueMode`、`workerEnabled`、`maxConcurrency`、`pollIntervalMs`、`leaseTtlSeconds`。

- Create: `nexa-rag-boot/src/main/java/com/nexarag/boot/worker/LocalDocumentPipelineWorker.java`
  - 实现 `SmartLifecycle`。
  - `workerEnabled=true && mode=local && queueMode=pipeline` 时启动。
  - 启动 `maxConcurrency` 个后台循环。
  - 循环逻辑：`poll -> execute -> ack`，空队列 sleep `pollIntervalMs`，异常时 release 回 waiting。

- Modify: `nexa-rag-boot/src/main/resources/application.yml`
  - 增加：

```yaml
nexa:
  document:
    pipeline:
      worker-enabled: ${NEXA_DOCUMENT_PIPELINE_WORKER_ENABLED:false}
      poll-interval-ms: ${NEXA_DOCUMENT_PIPELINE_POLL_INTERVAL_MS:1000}
      lease-ttl-seconds: ${NEXA_DOCUMENT_PIPELINE_LEASE_TTL_SECONDS:300}
      queue:
        key-prefix: ${NEXA_DOCUMENT_PIPELINE_QUEUE_KEY_PREFIX:nexa:document:pipeline}
        lease-ttl-seconds: ${NEXA_DOCUMENT_PIPELINE_LEASE_TTL_SECONDS:300}
        retry-ttl-seconds: ${NEXA_DOCUMENT_PIPELINE_RETRY_TTL_SECONDS:86400}
```

- Modify: `nexa-rag-boot/src/main/resources/application-integration.yml`
  - 保留 Redis 地址默认 `192.168.0.134`。
  - 不强制打开 worker，集成测试仍由 `NEXA_INTEGRATION_ENABLED=true` 显式控制。

- Test: `nexa-rag-boot/src/test/java/com/nexarag/boot/worker/LocalDocumentPipelineWorkerTest.java`
  - 使用内存队列和测试 executor 验证 worker 按 FIFO 执行并 ack。
  - 验证 executor 抛异常时 release 任务。
  - 不访问真实 Redis。

## 4. 关键接口草案

### 4.1 `DocumentPipelineQueue`

```java
package com.nexarag.infra.queue.document;

import java.time.Duration;
import java.util.Optional;

/**
 * 文档流水线队列，负责整条文档处理任务的排队、租约和状态查询。
 */
public interface DocumentPipelineQueue {

    /**
     * 将文档加入等待队列；如果已在等待或运行中，则返回现有状态。
     *
     * @param documentId 文档ID
     * @return 文档队列状态
     */
    DocumentPipelineQueueStatus enqueue(Long documentId);

    /**
     * 按公平队列顺序获取一个任务租约。
     *
     * @param workerId 工作器ID
     * @param leaseTtl 租约时长
     * @return 获取到的任务；队列为空时返回 Optional.empty()
     */
    Optional<DocumentPipelineTask> poll(String workerId, Duration leaseTtl);

    /**
     * 确认任务完成并删除运行态。
     *
     * @param documentId 文档ID
     * @param leaseToken 租约令牌
     */
    void ack(Long documentId, String leaseToken);

    /**
     * 释放任务租约；requeue=true 时重新回到 waiting 队列尾部。
     *
     * @param documentId 文档ID
     * @param leaseToken 租约令牌
     * @param requeue    是否重新入队
     */
    void release(Long documentId, String leaseToken, boolean requeue);

    /**
     * 查询队列实时状态。
     *
     * @param documentId 文档ID
     * @return 队列状态；Redis 无状态时返回 Optional.empty()
     */
    Optional<DocumentPipelineQueueStatus> queryStatus(Long documentId);
}
```

### 4.2 `DocumentPipelineQueueStatus`

```java
package com.nexarag.infra.queue.document;

/**
 * 文档流水线队列状态。
 *
 * @param documentId      文档ID
 * @param queuePosition   等待队列位置，从 1 开始
 * @param waitingCount    等待队列总数
 * @param running         是否运行中
 * @param workerId        当前 Worker ID
 * @param leaseTtlSeconds 租约剩余秒数
 * @param enqueueSequence 入队序号
 */
public record DocumentPipelineQueueStatus(Long documentId,
                                          Integer queuePosition,
                                          Integer waitingCount,
                                          Boolean running,
                                          String workerId,
                                          Long leaseTtlSeconds,
                                          Long enqueueSequence) {
}
```

### 4.3 `DocumentPipelineTask`

```java
package com.nexarag.infra.queue.document;

/**
 * 文档流水线任务租约。
 *
 * @param documentId      文档ID
 * @param leaseToken      租约令牌
 * @param workerId        Worker ID
 * @param enqueueSequence 入队序号
 */
public record DocumentPipelineTask(Long documentId,
                                   String leaseToken,
                                   String workerId,
                                   Long enqueueSequence) {
}
```

### 4.4 `DocumentPipelineExecutor`

```java
package com.nexarag.document.service;

/**
 * 文档流水线执行器，后续由 Workflow Graph 实现真实解析、切分和索引。
 */
public interface DocumentPipelineExecutor {

    /**
     * 执行指定文档的入库流水线。
     *
     * @param documentId 文档ID
     */
    void execute(Long documentId);
}
```

## 5. TDD 任务拆分

### Task 1: 基线验证

**Files:** 无代码改动。

- [ ] **Step 1: 检查工作区**

Run:

```powershell
git status --short --branch
```

Expected: 工作区干净，或只包含当前计划文档改动。

- [ ] **Step 2: 运行相关模块测试**

Run:

```powershell
mvn -pl nexa-rag-infra,nexa-rag-document,nexa-rag-boot -am test
```

Expected: `BUILD SUCCESS`。

### Task 2: Infra 队列契约与公平 FIFO 行为

**Files:**

- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/queue/document/DocumentPipelineQueue.java`
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/queue/document/DocumentPipelineQueueStatus.java`
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/queue/document/DocumentPipelineTask.java`
- Test: `nexa-rag-infra/src/test/java/com/nexarag/infra/queue/document/InMemoryDocumentPipelineQueueTest.java`

- [ ] **Step 1: 写失败测试，验证 FIFO 公平顺序**

Create test file with these core cases:

```java
@Test
void pollShouldFollowSuccessfulEnqueueOrder() {
    InMemoryDocumentPipelineQueue queue = new InMemoryDocumentPipelineQueue();

    queue.enqueue(10L);
    queue.enqueue(20L);
    queue.enqueue(30L);

    assertThat(queue.poll("worker-1", Duration.ofMinutes(5)).orElseThrow().documentId()).isEqualTo(10L);
    assertThat(queue.poll("worker-1", Duration.ofMinutes(5)).orElseThrow().documentId()).isEqualTo(20L);
    assertThat(queue.poll("worker-1", Duration.ofMinutes(5)).orElseThrow().documentId()).isEqualTo(30L);
}
```

- [ ] **Step 2: 运行失败测试**

Run:

```powershell
mvn -pl nexa-rag-infra -am test "-Dtest=InMemoryDocumentPipelineQueueTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: 编译失败，提示 `DocumentPipelineQueue` 或 `InMemoryDocumentPipelineQueue` 不存在。

- [ ] **Step 3: 实现接口和测试内存队列**

Implementation rules:

- 生产代码只新增接口和 record。
- `InMemoryDocumentPipelineQueue` 放在 `src/test/java`，只用于单元测试。
- 内存队列使用 `AtomicLong sequence` 和 `TreeMap<Long, Long>` 模拟 Redis ZSET score。

- [ ] **Step 4: 运行测试确认通过**

Run:

```powershell
mvn -pl nexa-rag-infra -am test "-Dtest=InMemoryDocumentPipelineQueueTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: `BUILD SUCCESS`。

### Task 3: Redis Key 与配置属性

**Files:**

- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/queue/document/DocumentPipelineQueueProperties.java`
- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/queue/document/DocumentPipelineQueueKeys.java`
- Modify: `nexa-rag-infra/pom.xml`
- Test: `nexa-rag-infra/src/test/java/com/nexarag/infra/queue/document/DocumentPipelineQueueKeysTest.java`

- [ ] **Step 1: 写失败测试，验证 Redis key 命名**

Core assertions:

```java
DocumentPipelineQueueProperties properties = new DocumentPipelineQueueProperties();
DocumentPipelineQueueKeys keys = new DocumentPipelineQueueKeys(properties);

assertThat(keys.waitingKey()).isEqualTo("nexa:document:pipeline:waiting");
assertThat(keys.runningKey()).isEqualTo("nexa:document:pipeline:running");
assertThat(keys.leaseKey(1L)).isEqualTo("nexa:document:pipeline:lease:1");
assertThat(keys.metaKey(1L)).isEqualTo("nexa:document:pipeline:meta:1");
assertThat(keys.retryKey(1L)).isEqualTo("nexa:document:pipeline:retry:1");
assertThat(keys.sequenceKey()).isEqualTo("nexa:document:pipeline:sequence");
```

- [ ] **Step 2: 运行失败测试**

Run:

```powershell
mvn -pl nexa-rag-infra -am test "-Dtest=DocumentPipelineQueueKeysTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: 编译失败，提示 key 类不存在。

- [ ] **Step 3: 实现配置和 key 生成器**

Implementation requirements:

- `DocumentPipelineQueueProperties` 使用 `@ConfigurationProperties(prefix = "nexa.document.pipeline.queue")` 和 `@Component`。
- 默认值：
  - `keyPrefix = "nexa:document:pipeline"`
  - `leaseTtlSeconds = 300`
  - `retryTtlSeconds = 86400`
- `DocumentPipelineQueueKeys` 不硬编码文档 ID 字符串拼接到业务类中。

- [ ] **Step 4: 增加 Redis 依赖**

Modify `nexa-rag-infra/pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

- [ ] **Step 5: 运行测试确认通过**

Run:

```powershell
mvn -pl nexa-rag-infra -am test "-Dtest=DocumentPipelineQueueKeysTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: `BUILD SUCCESS`。

### Task 4: Redis 队列实现

**Files:**

- Create: `nexa-rag-infra/src/main/java/com/nexarag/infra/queue/document/RedisDocumentPipelineQueue.java`
- Test: `nexa-rag-infra/src/test/java/com/nexarag/infra/queue/document/RedisDocumentPipelineQueueScriptTest.java`

- [ ] **Step 1: 写失败测试，验证脚本文本包含公平队列关键操作**

Because default unit tests must not connect Redis, test script builder methods or constants:

```java
assertThat(RedisDocumentPipelineQueue.ENQUEUE_SCRIPT).contains("INCR");
assertThat(RedisDocumentPipelineQueue.ENQUEUE_SCRIPT).contains("ZADD");
assertThat(RedisDocumentPipelineQueue.POLL_SCRIPT).contains("ZRANGE");
assertThat(RedisDocumentPipelineQueue.POLL_SCRIPT).contains("ZREM");
assertThat(RedisDocumentPipelineQueue.ACK_SCRIPT).contains("HDEL");
assertThat(RedisDocumentPipelineQueue.RELEASE_SCRIPT).contains("ZADD");
```

- [ ] **Step 2: 运行失败测试**

Run:

```powershell
mvn -pl nexa-rag-infra -am test "-Dtest=RedisDocumentPipelineQueueScriptTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: 编译失败，提示 Redis 实现不存在。

- [ ] **Step 3: 实现 Redis 队列类**

Implementation requirements:

- 使用 `StringRedisTemplate.execute(new DefaultRedisScript<>(script, List.class), keys, args...)`。
- `enqueue` Lua 行为：
  - 如果 waiting 已存在该 documentId，返回当前 rank/count。
  - 如果 running 已存在该 documentId，返回 running 状态。
  - 否则 `INCR sequenceKey` 得到 sequence，`ZADD waiting sequence documentId`。
  - 写 `meta:{documentId}`：`enqueueSequence`、`enqueueTime`。
- `poll` Lua 行为：
  - `ZRANGE waiting 0 0 WITHSCORES` 取最早任务。
  - `ZREM waiting documentId`。
  - 生成的 `leaseToken` 由 Java 传入 UUID。
  - `HSET running documentId runningJson`。
  - `SET lease:{documentId} leaseToken PX leaseTtlMs`。
- `ack` Lua 行为：
  - 只有 `GET leaseKey == leaseToken` 时才 `HDEL running`、`DEL leaseKey`、`DEL metaKey`。
  - 租约不匹配时不删除，避免过期租约误 ack 新任务。
- `release` Lua 行为：
  - 只有租约匹配时才释放。
  - `requeue=true` 时使用新的 `INCR sequenceKey` 重新 `ZADD waiting`，因此失败任务回到队尾，不插队。
- 所有日志使用简体中文，不输出 Redis 密码或完整配置。

- [ ] **Step 4: 运行 infra 测试**

Run:

```powershell
mvn -pl nexa-rag-infra -am test "-Dtest=DocumentPipelineQueueKeysTest,InMemoryDocumentPipelineQueueTest,RedisDocumentPipelineQueueScriptTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: `BUILD SUCCESS`。

### Task 5: Document Dispatcher 接入 Redis 队列

**Files:**

- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/service/DocumentQueueInfo.java`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/service/impl/RedisDocumentProcessTaskDispatcher.java`
- Delete: `nexa-rag-document/src/main/java/com/nexarag/document/service/impl/LocalDocumentProcessTaskDispatcher.java`
- Test: `nexa-rag-document/src/test/java/com/nexarag/document/service/impl/RedisDocumentProcessTaskDispatcherTest.java`

- [ ] **Step 1: 写失败测试，验证 dispatcher 返回 Redis 队列状态**

Core assertions:

```java
FakeDocumentPipelineQueue queue = new FakeDocumentPipelineQueue(
        new DocumentPipelineQueueStatus(1L, 2, 5, false, null, null, 100L));
RedisDocumentProcessTaskDispatcher dispatcher = new RedisDocumentProcessTaskDispatcher(queue);

DocumentQueueInfo result = dispatcher.enqueue(1L);

assertThat(result.queuePosition()).isEqualTo(2);
assertThat(result.waitingCount()).isEqualTo(5);
assertThat(result.running()).isFalse();
```

- [ ] **Step 2: 运行失败测试**

Run:

```powershell
mvn -pl nexa-rag-document -am test "-Dtest=RedisDocumentProcessTaskDispatcherTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: 编译失败，提示 Redis dispatcher 不存在。

- [ ] **Step 3: 修改 `DocumentQueueInfo`**

Target shape:

```java
public record DocumentQueueInfo(Integer queuePosition,
                                Integer waitingCount,
                                Boolean running,
                                String workerId,
                                Long leaseTtlSeconds) {

    public DocumentQueueInfo(Integer queuePosition, Integer waitingCount) {
        this(queuePosition, waitingCount, false, null, null);
    }
}
```

- [ ] **Step 4: 实现 Redis dispatcher 并删除占位 dispatcher**

Implementation requirements:

- `RedisDocumentProcessTaskDispatcher` 使用构造器注入 `DocumentPipelineQueue`。
- `enqueue` 只负责映射 `DocumentPipelineQueueStatus -> DocumentQueueInfo`。
- 删除或取消 `LocalDocumentProcessTaskDispatcher` 的 Spring Bean，避免多个 dispatcher Bean。

- [ ] **Step 5: 运行 document 相关测试**

Run:

```powershell
mvn -pl nexa-rag-document -am test "-Dtest=RedisDocumentProcessTaskDispatcherTest,DocumentUploadServiceImplTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: `BUILD SUCCESS`。

### Task 6: 队列状态查询增强

**Files:**

- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/vo/DocumentProcessStatusVO.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/converter/DocumentConverter.java`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/service/DocumentQueueStatusService.java`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/service/impl/DocumentQueueStatusServiceImpl.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/controller/DocumentController.java`
- Test: `nexa-rag-document/src/test/java/com/nexarag/document/service/impl/DocumentQueueStatusServiceImplTest.java`

- [ ] **Step 1: 写失败测试，验证 waiting 状态返回队列位置**

Core assertions:

```java
Document document = Document.builder()
        .documentId(1L)
        .status(DocumentStatus.QUEUED)
        .retryCount(0)
        .build();
FakeDocumentService documentService = new FakeDocumentService(document);
FakeDocumentPipelineQueue queue = new FakeDocumentPipelineQueue(
        Optional.of(new DocumentPipelineQueueStatus(1L, 3, 8, false, null, null, 101L)));
DocumentQueueStatusServiceImpl service = new DocumentQueueStatusServiceImpl(documentService, queue);

DocumentProcessStatusVO vo = service.getProcessStatus(1L);

assertThat(vo.status()).isEqualTo(DocumentStatus.QUEUED);
assertThat(vo.queuePosition()).isEqualTo(3);
assertThat(vo.waitingCount()).isEqualTo(8);
assertThat(vo.running()).isFalse();
```

- [ ] **Step 2: 写失败测试，验证 Redis 无状态时不报错**

Core assertions:

```java
FakeDocumentPipelineQueue queue = new FakeDocumentPipelineQueue(Optional.empty());
DocumentProcessStatusVO vo = service.getProcessStatus(1L);

assertThat(vo.status()).isEqualTo(DocumentStatus.QUEUED);
assertThat(vo.queuePosition()).isNull();
assertThat(vo.waitingCount()).isNull();
assertThat(vo.running()).isFalse();
```

- [ ] **Step 3: 运行失败测试**

Run:

```powershell
mvn -pl nexa-rag-document -am test "-Dtest=DocumentQueueStatusServiceImplTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: 编译失败，提示 status service 或 VO 字段不存在。

- [ ] **Step 4: 实现 VO、converter 和 service**

Implementation requirements:

- `DocumentProcessStatusVO` 增加队列字段，但保留已有字段顺序语义清晰。
- `DocumentConverter.toProcessStatusVO(Document document)` 返回无 Redis 状态的 VO。
- 新增重载 `toProcessStatusVO(Document document, DocumentQueueInfo queueInfo)`。
- `DocumentQueueStatusServiceImpl`：
  - 第 1 步读取 MySQL 稳定状态。
  - 第 2 步查询 Redis 实时状态。
  - 第 3 步组装 VO；Redis 无状态时 `running=false`，队列字段为 null。

- [ ] **Step 5: 修改 Controller 状态接口**

`getProcessStatus` 从：

```java
return Results.success(DocumentConverter.toProcessStatusVO(documentService.getRequiredDocument(documentId)));
```

改为：

```java
return Results.success(documentQueueStatusService.getProcessStatus(documentId));
```

- [ ] **Step 6: 运行 document 测试**

Run:

```powershell
mvn -pl nexa-rag-document -am test "-Dtest=DocumentQueueStatusServiceImplTest,DocumentConverterTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: `BUILD SUCCESS`。

### Task 7: 本地 Worker 执行循环

**Files:**

- Create: `nexa-rag-document/src/main/java/com/nexarag/document/service/DocumentPipelineExecutor.java`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/service/impl/NoopDocumentPipelineExecutor.java`
- Create: `nexa-rag-boot/src/main/java/com/nexarag/boot/worker/DocumentPipelineWorkerProperties.java`
- Create: `nexa-rag-boot/src/main/java/com/nexarag/boot/worker/LocalDocumentPipelineWorker.java`
- Test: `nexa-rag-boot/src/test/java/com/nexarag/boot/worker/LocalDocumentPipelineWorkerTest.java`

- [ ] **Step 1: 写失败测试，验证 Worker 按 FIFO 执行并 ack**

Core assertions:

```java
InMemoryDocumentPipelineQueue queue = new InMemoryDocumentPipelineQueue();
queue.enqueue(10L);
queue.enqueue(20L);
RecordingDocumentPipelineExecutor executor = new RecordingDocumentPipelineExecutor();
LocalDocumentPipelineWorker worker = new LocalDocumentPipelineWorker(properties, queue, executor);

worker.runOnceForTest("worker-1");
worker.runOnceForTest("worker-1");

assertThat(executor.executedDocumentIds()).containsExactly(10L, 20L);
assertThat(queue.queryStatus(10L)).isEmpty();
assertThat(queue.queryStatus(20L)).isEmpty();
```

- [ ] **Step 2: 写失败测试，验证执行异常时 release 回队尾**

Core assertions:

```java
queue.enqueue(10L);
queue.enqueue(20L);
FailOnceDocumentPipelineExecutor executor = new FailOnceDocumentPipelineExecutor(10L);

worker.runOnceForTest("worker-1");
worker.runOnceForTest("worker-1");
worker.runOnceForTest("worker-1");

assertThat(executor.executedDocumentIds()).containsExactly(10L, 20L, 10L);
```

- [ ] **Step 3: 运行失败测试**

Run:

```powershell
mvn -pl nexa-rag-boot -am test "-Dtest=LocalDocumentPipelineWorkerTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: 编译失败，提示 worker 或 executor 不存在。

- [ ] **Step 4: 实现占位执行器**

Implementation requirements:

- `NoopDocumentPipelineExecutor` 标记为 `@Service`。
- 只记录中文日志，不推进文档状态。
- 日志包含 `documentId`。

- [ ] **Step 5: 实现 Worker 配置**

`DocumentPipelineWorkerProperties` 默认值：

```java
private String mode = "local";
private String queueMode = "pipeline";
private boolean workerEnabled = false;
private int maxConcurrency = 2;
private long pollIntervalMs = 1000L;
private long leaseTtlSeconds = 300L;
```

- [ ] **Step 6: 实现 Worker**

Implementation requirements:

- 实现 `SmartLifecycle`。
- `isAutoStartup()` 返回 `workerEnabled`。
- `start()` 中启动固定线程池。
- `stop()` 中关闭线程池。
- 提供包可见方法 `runOnceForTest(String workerId)`，仅用于测试单次执行，不启动无限循环。
- 正常执行：`poll -> executor.execute -> ack`。
- 异常执行：记录错误日志，`release(documentId, leaseToken, true)`，任务回到队尾。
- 空队列：循环模式 sleep `pollIntervalMs`；测试单次方法直接返回。

- [ ] **Step 7: 运行 boot worker 测试**

Run:

```powershell
mvn -pl nexa-rag-boot -am test "-Dtest=LocalDocumentPipelineWorkerTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: `BUILD SUCCESS`。

### Task 8: 配置补齐与默认不访问外部 Redis

**Files:**

- Modify: `nexa-rag-boot/src/main/resources/application.yml`
- Modify: `nexa-rag-boot/src/main/resources/application-integration.yml`
- Test: `nexa-rag-boot/src/test/java/com/nexarag/boot/NexaRagApplicationTest.java`

- [ ] **Step 1: 修改默认配置**

Add under `nexa.document.pipeline`:

```yaml
worker-enabled: ${NEXA_DOCUMENT_PIPELINE_WORKER_ENABLED:false}
poll-interval-ms: ${NEXA_DOCUMENT_PIPELINE_POLL_INTERVAL_MS:1000}
lease-ttl-seconds: ${NEXA_DOCUMENT_PIPELINE_LEASE_TTL_SECONDS:300}
queue:
  key-prefix: ${NEXA_DOCUMENT_PIPELINE_QUEUE_KEY_PREFIX:nexa:document:pipeline}
  lease-ttl-seconds: ${NEXA_DOCUMENT_PIPELINE_LEASE_TTL_SECONDS:300}
  retry-ttl-seconds: ${NEXA_DOCUMENT_PIPELINE_RETRY_TTL_SECONDS:86400}
```

Reason:

- 默认单元测试和应用上下文测试不能访问外部 Redis。
- 真实本地 Worker 通过环境变量显式启用。
- 上传接口仍会把任务写入 Redis；真实运行环境必须配置 Redis。

- [ ] **Step 2: 确认 integration profile Redis 地址保持 192.168.0.134**

No change unless missing:

```yaml
spring:
  data:
    redis:
      host: ${NEXA_REDIS_HOST:192.168.0.134}
      port: ${NEXA_REDIS_PORT:6379}
```

- [ ] **Step 3: 运行 Spring Boot 上下文测试**

Run:

```powershell
mvn -pl nexa-rag-boot -am test "-Dtest=NexaRagApplicationTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: `BUILD SUCCESS`，且不会连接真实 Redis。

### Task 9: 模块验证与架构边界

**Files:** 无新增文件。

- [ ] **Step 1: 运行当前批次最小测试**

Run:

```powershell
mvn -pl nexa-rag-infra,nexa-rag-document,nexa-rag-boot -am test "-Dtest=DocumentPipelineQueueKeysTest,InMemoryDocumentPipelineQueueTest,RedisDocumentPipelineQueueScriptTest,RedisDocumentProcessTaskDispatcherTest,DocumentQueueStatusServiceImplTest,LocalDocumentPipelineWorkerTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: `BUILD SUCCESS`。

- [ ] **Step 2: 运行模块测试**

Run:

```powershell
mvn -pl nexa-rag-infra,nexa-rag-document,nexa-rag-boot -am test
```

Expected: `BUILD SUCCESS`。

- [ ] **Step 3: 运行架构边界测试**

Run:

```powershell
mvn -pl nexa-rag-boot -am test "-Dtest=ModuleDependencyTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

Expected: `BUILD SUCCESS`。

- [ ] **Step 4: 检查空白问题**

Run:

```powershell
git diff --check
```

Expected: 无输出。

### Task 10: 真实 Redis 流程验证

**Files:**

- Test: `nexa-rag-infra/src/test/java/com/nexarag/infra/queue/document/RedisDocumentPipelineQueueIntegrationTest.java`

- [ ] **Step 1: 新增真实 Redis 集成测试**

测试通过 JUnit `Assumptions` 显式开关控制，默认跳过。开启条件：

```text
-Dnexa.redis.integration.enabled=true
```

连接参数：

```text
-Dnexa.redis.host=192.168.0.134
-Dnexa.redis.port=6379
-Dnexa.redis.password=<运行时传入>
```

测试行为：

- 使用测试专用 key prefix：`nexa:test:document:pipeline:{timestamp}`。
- 依次入队 `1001/1002/1003`，验证 queuePosition 为 `1/2/3`。
- poll 三次，验证 FIFO 顺序为 `1001/1002/1003`。
- 第一个任务 ack 后状态为空。
- 第二个任务 release 回队尾后，再次 poll 时排在第三个任务之后。
- 测试结束可以不删除 key；如果为了避免污染，可仅删除测试 prefix 下 key。

- [ ] **Step 2: 运行真实 Redis 集成测试**

Run:

```powershell
mvn -pl nexa-rag-infra -am test "-Dtest=RedisDocumentPipelineQueueIntegrationTest" "-Dsurefire.failIfNoSpecifiedTests=false" "-Dnexa.redis.integration.enabled=true" "-Dnexa.redis.host=192.168.0.134" "-Dnexa.redis.port=6379" "-Dnexa.redis.password=<运行时密码>"
```

Expected: `BUILD SUCCESS`，并确认 Redis 队列实际完成 `enqueue -> poll -> ack/release -> FIFO` 流程。

### Task 11: 提交

**Files:** 所有本批实现和测试文件。

- [ ] **Step 1: 使用 git-commit-workflow 审查变更**

Run:

```powershell
git status --short --branch
git diff --stat
git diff --cached --stat
```

Expected: 只包含 Redis 队列与本地 Worker 相关改动。

- [ ] **Step 2: 暂存并提交**

Run:

```powershell
git add pom.xml nexa-rag-infra nexa-rag-document nexa-rag-boot
git commit -m "feat(document): 接入Redis排队与本地Worker"
```

Expected: 提交成功，工作区干净。

## 6. 自审清单

- [ ] Redis waiting 队列使用 `INCR sequence` 作为 ZSET score，保证公平 FIFO。
- [ ] document 模块不直接依赖 RedisTemplate。
- [ ] boot Worker 不直接操作文档 Mapper。
- [ ] workflow 模块本批不参与实现。
- [ ] 默认单元测试不访问真实 Redis。
- [ ] Redis 无实时状态时，状态接口不会报错。
- [ ] Worker 执行失败时任务回到队尾，不插队。
- [ ] 所有新增类都有简体中文 JavaDoc。
- [ ] 关键方法和关键步骤有简体中文注释。
- [ ] 日志使用简体中文，且不输出 Redis 密码和完整配置。
