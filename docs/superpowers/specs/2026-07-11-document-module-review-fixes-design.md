# Document 模块审查问题修复设计

## 1. 背景

本次设计处理 `nexa-rag-document` 模块代码审查中已经确认的四项问题：

1. 文档片段替换缺少事务保护。
2. 上传文件缺少大小和类型的业务校验，后续流程失败时可能残留对象存储文件。
3. 文档片段查询一次返回全部数据，缺少分页限制。
4. 自定义正则和嵌套切分配置缺少安全限制及完整校验。

MySQL 与 Redis 队列之间的投递一致性问题暂不处理。本次不引入 MQ、Redis Stream、Outbox 或新的调度任务。

## 2. 设计目标

- 保证替换文档片段时，旧片段删除和新片段保存具备原子性。
- 复用 Spring Boot 原生 Multipart 配置，避免重复维护上传大小配置。
- 在对象存储写入前完成文件大小和文件类型校验。
- 对对象存储写入后的失败路径执行删除补偿。
- 将文档片段查询改为有上限的分页接口。
- 限制自定义正则带来的 CPU 长时间占用风险。
- 确保嵌套配置上的 Bean Validation 约束能够生效。

## 3. 非目标

- 不处理 MySQL 与 Redis 队列的最终一致性。
- 不实现事务消息、Outbox、Redis Stream 或 RocketMQ。
- 不实现文档删除时的索引、片段及对象存储全链路清理。
- 不修改文档解析、切分和索引的业务流程。
- 不保留原有文档片段全量查询响应格式。

## 4. 片段替换事务

### 4.1 调整范围

在 `DocumentChunkServiceImpl.replaceDocumentChunks(...)` 公共方法上增加：

```java
@Transactional(rollbackFor = Exception.class)
```

该事务覆盖以下操作：

1. 按文档 ID 逻辑删除旧片段。
2. 构造新片段集合。
3. 批量保存新片段。

如果新片段构造、元数据序列化或批量保存失败，旧片段删除操作必须回滚。

### 4.2 测试

- 保留正常替换测试。
- 增加保存新片段失败时的事务回滚验证。
- 优先使用能够验证真实事务行为的 Spring 测试；若当前模块缺少内存数据库条件，则至少补充事务注解和异常路径单元测试，并在后续集成测试中覆盖真实回滚。

## 5. 上传文件校验与补偿

### 5.1 Spring Multipart 配置

在 `application.yml` 中使用 Spring Boot 原生配置：

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 100MB
      max-request-size: 110MB
```

`max-request-size` 略大于单文件限制，用于容纳 Multipart 边界和上传请求 JSON。

不新增 `nexa.document.upload.max-file-size`，避免出现两套大小限制配置。

### 5.2 Service 校验

`DocumentUploadServiceImpl` 注入 Spring Boot 的 `MultipartProperties`，使用 `getMaxFileSize().toBytes()` 作为业务校验的唯一大小来源。

保存对象存储前依次校验：

1. 文件对象不为空且内容不为空。
2. 原始文件名不为空。
3. 文件大小不超过 `spring.servlet.multipart.max-file-size`。
4. 文件扩展名能够映射为项目支持的 `FileType`，不能为 `UNKNOWN`。

Servlet 层负责提前拒绝 HTTP 超大请求，Service 层负责覆盖单元测试、内部调用和绕过 Controller 的调用场景。

### 5.3 失败补偿

对象存储保存成功后，如果创建文档记录失败，则尝试删除已保存的原始对象。

文档记录创建成功后，后续配置合并、状态提交或 Redis 入队失败时必须保留对象文件，避免留下“数据库记录存在但原文件丢失”的文档。MySQL 与 Redis 的投递一致性仍按本设计非目标处理。

补偿删除规则：

1. 保留并继续抛出原始业务异常。
2. 删除补偿失败时记录中文错误日志，包含对象名和原始异常上下文。
3. 补偿异常不能覆盖原始异常。

该补偿仅处理当前上传请求刚创建的对象，不扩展为通用资源清理框架。

### 5.4 测试

- 空文件和空文件名校验。
- 文件大小等于限制时允许上传，超过限制时拒绝上传。
- 不支持的扩展名在对象存储写入前被拒绝。
- 数据库创建失败时删除已保存对象。
- 队列入队失败时保留已保存对象，并继续抛出原始异常。
- 删除补偿失败时不覆盖原始异常。

## 6. 文档片段分页

### 6.1 接口调整

原接口路径保持不变：

```text
GET /api/documents/{documentId}/chunks
```

新增查询参数：

- `pageNum`：默认值为 `1`。
- `pageSize`：默认值为 `20`。

页大小最大值为 `100`。小于等于零的页码归一化为 `1`，小于等于零的页大小归一化为 `20`，超过上限时截断为 `100`。

响应从：

```java
Result<List<DocumentChunkVO>>
```

调整为：

```java
Result<PageVO<DocumentChunkVO>>
```

该调整为已确认的破坏性变更，不保留旧的全量查询接口。

### 6.2 Service 调整

`DocumentChunkService` 增加分页查询能力，使用 MyBatis-Plus `Page<DocumentChunk>`：

1. 按 `documentId` 过滤。
2. 按 `chunkOrder` 升序排列。
3. 返回统一的 `PageVO<DocumentChunkVO>`，或返回分页实体后由上层统一转换。

转换职责应保持现有项目风格，避免 Controller 中堆叠分页组装逻辑。

### 6.3 测试

- 默认分页参数。
- 非法页码和页大小归一化。
- 页大小上限为 100。
- 查询条件包含文档 ID，并按片段顺序升序排列。
- Controller 返回分页响应结构。

## 7. 正则与嵌套配置校验

### 7.1 嵌套校验

在 `SplitConfigRequest` 的以下字段上增加 `@Valid`：

- `markdown`
- `regex`
- `excel`

确保其内部约束能够通过 `UploadDocumentRequest` 和 `ProcessDocumentRequest` 继续级联执行。

### 7.2 配置约束

`RegexSplitOptions.regex` 增加最大长度 256 的约束。

`ExcelSplitOptions.maxRowsPerChunk` 在现有最小值 1 的基础上增加最大值 10000。

### 7.3 正则安全校验

本次不引入第三方正则引擎，也不实现完整正则语法分析。编译正则前执行轻量安全校验：

1. 拒绝长度超过 256 的正则。
2. 拒绝常见嵌套量词结构，例如 `(a+)+`、`(.*)+`、`(.+)*`。
3. 捕获 `PatternSyntaxException`，转换为项目统一的 `ServiceException` 或客户端参数异常。

安全校验应封装为职责明确的私有方法或独立组件，错误信息使用简体中文，不回显完整大段正则内容。

### 7.4 测试

- 合法正则正常切分。
- 非法正则返回统一业务异常。
- 超长正则被拒绝。
- 常见嵌套量词正则被拒绝。
- Excel 最大行数超过 10000 时校验失败。
- 嵌套配置约束能够从上传和处理请求入口触发。

## 8. 错误处理与日志

- 所有新增异常信息使用简体中文。
- 上传补偿失败使用 `error` 日志，必须包含对象名并传入异常对象。
- 参数不合法使用现有 `DocumentErrorCode.DOCUMENT_PROCESS_CONFIG_INVALID` 或更匹配的文档错误码。
- 不记录文件内容、自定义正则全文或其他可能造成日志膨胀的数据。

## 9. 验证范围

实现完成后至少执行：

```powershell
mvn -pl nexa-rag-document -am test -DskipITs
```

如果修改了 Boot 配置绑定或启动上下文，同时执行：

```powershell
mvn -pl nexa-rag-boot -am test -DskipITs
```

验证重点包括：

- 片段替换事务不破坏现有切分流程。
- 上传正常路径和补偿路径均符合预期。
- 分页接口编译通过且返回结构正确。
- 正则限制不影响已有合法切分测试。
- Boot 启动时能够正常绑定 Multipart 配置。

## 10. 实施顺序

1. 为每项行为补充失败测试。
2. 实现片段替换事务。
3. 配置并复用 Spring Multipart 大小限制。
4. 实现上传前校验和对象存储删除补偿。
5. 将片段查询调整为分页。
6. 补充嵌套校验和正则安全限制。
7. 运行 Document 模块测试。
8. 运行 Boot 模块启动及回归测试。
