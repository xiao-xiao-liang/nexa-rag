# Document 模块审查问题修复实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 Document 模块的片段替换事务、上传校验与补偿、片段分页及正则配置安全问题。

**Architecture:** 保持现有模块边界，不引入 MQ、Outbox 或新的调度基础设施。片段替换使用本地数据库事务；上传大小复用 Spring Boot `MultipartProperties`；分页继续使用 MyBatis-Plus；正则安全校验封装为 Document 模块内的独立组件。

**Tech Stack:** Java 21、Spring Boot、Spring MVC、Bean Validation、MyBatis-Plus、JUnit 5、Mockito、AssertJ。

---

## 文件结构

### 新增文件

- `nexa-rag-document/src/main/java/com/nexarag/document/splitter/text/RegexSafetyValidator.java`
  - 校验自定义正则长度、语法和常见嵌套量词。
- `nexa-rag-document/src/test/java/com/nexarag/document/splitter/text/RegexSafetyValidatorTest.java`
  - 验证合法、非法、超长和嵌套量词正则。
- `nexa-rag-document/src/test/java/com/nexarag/document/dto/DocumentRequestValidationTest.java`
  - 验证嵌套 Bean Validation 和 Excel 最大行数限制。

### 修改文件

- `nexa-rag-document/src/main/java/com/nexarag/document/service/impl/DocumentChunkServiceImpl.java`
  - 为片段替换增加事务，并增加分页查询实现。
- `nexa-rag-document/src/main/java/com/nexarag/document/service/DocumentChunkService.java`
  - 将全量片段查询调整为分页查询接口。
- `nexa-rag-document/src/test/java/com/nexarag/document/service/impl/DocumentChunkServiceImplTest.java`
  - 验证事务声明、分页参数归一化和分页结果。
- `nexa-rag-document/src/main/java/com/nexarag/document/service/impl/DocumentUploadServiceImpl.java`
  - 复用 Multipart 配置，上传前校验大小和类型，失败时删除已保存对象。
- `nexa-rag-document/src/test/java/com/nexarag/document/service/impl/DocumentUploadServiceImplTest.java`
  - 覆盖大小、类型和补偿路径。
- `nexa-rag-boot/src/main/resources/application.yml`
  - 增加 Spring Boot 原生 Multipart 限制。
- `nexa-rag-boot/src/test/java/com/nexarag/boot/NexaRagApplicationConfigurationTest.java`
  - 验证 Multipart 配置存在且取值正确。
- `nexa-rag-document/src/main/java/com/nexarag/document/controller/DocumentController.java`
  - 片段查询增加分页参数并返回 `PageVO`。
- `nexa-rag-document/src/main/java/com/nexarag/document/converter/DocumentConverter.java`
  - 增加片段分页转换。
- `nexa-rag-document/src/test/java/com/nexarag/document/converter/DocumentConverterTest.java`
  - 验证片段分页转换。
- `nexa-rag-document/src/main/java/com/nexarag/document/dto/SplitConfigRequest.java`
  - 为嵌套配置增加 `@Valid`。
- `nexa-rag-document/src/main/java/com/nexarag/document/dto/RegexSplitOptions.java`
  - 限制正则最大长度为 256。
- `nexa-rag-document/src/main/java/com/nexarag/document/dto/ExcelSplitOptions.java`
  - 限制单片段最大行数为 10000。
- `nexa-rag-document/src/main/java/com/nexarag/document/splitter/text/RegexTextDocumentSplitter.java`
  - 在编译正则前调用安全校验器。
- `nexa-rag-document/src/test/java/com/nexarag/document/splitter/text/RegexTextDocumentSplitterTest.java`
  - 适配新的构造依赖并验证非法正则异常。

---

### Task 1: 为文档片段替换增加事务保护

**Files:**
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/service/impl/DocumentChunkServiceImpl.java:38-59`
- Create: `nexa-rag-document/src/test/java/com/nexarag/document/service/impl/DocumentChunkServiceImplTest.java`

- [ ] **Step 1: 编写事务声明失败测试**

创建 `DocumentChunkServiceImplTest`，先验证 `replaceDocumentChunks` 必须声明回滚事务：

```java
package com.nexarag.document.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文档片段服务实现测试。
 */
class DocumentChunkServiceImplTest {

    @Test
    void replaceDocumentChunksShouldRollbackWhenAnyStepFails() throws NoSuchMethodException {
        Transactional transactional = DocumentChunkServiceImpl.class
                .getMethod("replaceDocumentChunks", Long.class, java.util.List.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(Arrays.asList(transactional.rollbackFor())).contains(Exception.class);
    }
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run:

```powershell
mvn -pl nexa-rag-document -am "-Dtest=DocumentChunkServiceImplTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: FAIL，`transactional` 为 `null`。

- [ ] **Step 3: 添加最小事务实现**

在 `DocumentChunkServiceImpl` 增加导入：

```java
import org.springframework.transaction.annotation.Transactional;
```

将注解直接添加到现有 `replaceDocumentChunks` 方法的 `@Override` 上方，不修改方法体：

```java
@Transactional(rollbackFor = Exception.class)
@Override
public List<DocumentChunk> replaceDocumentChunks(Long documentId, List<ChunkDraft> drafts)
```

- [ ] **Step 4: 运行测试并确认通过**

Run:

```powershell
mvn -pl nexa-rag-document -am "-Dtest=DocumentChunkServiceImplTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: PASS。

- [ ] **Step 5: 提交事务修复**

```powershell
git add nexa-rag-document/src/main/java/com/nexarag/document/service/impl/DocumentChunkServiceImpl.java nexa-rag-document/src/test/java/com/nexarag/document/service/impl/DocumentChunkServiceImplTest.java
git commit -m "fix(document): 保证文档片段替换事务一致性"
```

---

### Task 2: 增加上传大小、类型校验和对象存储补偿

**Files:**
- Modify: `nexa-rag-boot/src/main/resources/application.yml:4-22`
- Modify: `nexa-rag-boot/src/test/java/com/nexarag/boot/NexaRagApplicationConfigurationTest.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/service/impl/DocumentUploadServiceImpl.java:35-99`
- Modify: `nexa-rag-document/src/test/java/com/nexarag/document/service/impl/DocumentUploadServiceImplTest.java`

- [ ] **Step 1: 编写上传限制和补偿失败测试**

扩展 `DocumentUploadServiceImplTest`，统一通过辅助方法创建配置：

```java
private MultipartProperties multipartProperties() {
    MultipartProperties properties = new MultipartProperties();
    properties.setMaxFileSize(DataSize.ofMegabytes(100));
    properties.setMaxRequestSize(DataSize.ofMegabytes(110));
    return properties;
}
```

调整正常测试构造器：

```java
DocumentUploadServiceImpl uploadService = new DocumentUploadServiceImpl(
        fileStorageService, documentService, new ProcessConfigDefaults(), dispatcher,
        multipartProperties());
```

增加超限测试：

```java
@Test
void uploadShouldRejectFileLargerThanMultipartLimitBeforeStorage() {
    RecordingFileStorageService storageService = new RecordingFileStorageService();
    MultipartProperties properties = multipartProperties();
    properties.setMaxFileSize(DataSize.ofBytes(4));
    DocumentUploadServiceImpl uploadService = new DocumentUploadServiceImpl(
            storageService, new RecordingDocumentService(), new ProcessConfigDefaults(),
            new FixedDocumentProcessTaskDispatcher(), properties);
    MockMultipartFile file = new MockMultipartFile(
            "file", "demo.pdf", "application/pdf", "hello".getBytes(StandardCharsets.UTF_8));

    assertThatThrownBy(() -> uploadService.upload(file, null))
            .isInstanceOf(ClientException.class)
            .hasMessageContaining("文件大小超过限制");
    assertThat(storageService.savedFileName).isNull();
}
```

增加不支持类型测试：

```java
@Test
void uploadShouldRejectUnsupportedFileTypeBeforeStorage() {
    RecordingFileStorageService storageService = new RecordingFileStorageService();
    DocumentUploadServiceImpl uploadService = new DocumentUploadServiceImpl(
            storageService, new RecordingDocumentService(), new ProcessConfigDefaults(),
            new FixedDocumentProcessTaskDispatcher(), multipartProperties());
    MockMultipartFile file = new MockMultipartFile(
            "file", "demo.exe", "application/octet-stream", new byte[]{1});

    assertThatThrownBy(() -> uploadService.upload(file, null))
            .isInstanceOf(ClientException.class)
            .hasMessageContaining("不支持的文档类型");
    assertThat(storageService.savedFileName).isNull();
}
```

让测试用 `RecordingFileStorageService` 记录删除对象：

```java
private String deletedObjectName;

@Override
public void delete(String objectName) {
    this.deletedObjectName = objectName;
}
```

增加文档创建成功后入队失败保留对象测试：

```java
@Test
void uploadShouldKeepStoredObjectWhenEnqueueFailsAfterDocumentCreated() {
    RecordingFileStorageService storageService = new RecordingFileStorageService();
    DocumentProcessTaskDispatcher dispatcher = documentId -> {
        throw new ServiceException("模拟Redis入队失败");
    };
    DocumentUploadServiceImpl uploadService = new DocumentUploadServiceImpl(
            storageService, new RecordingDocumentService(), new ProcessConfigDefaults(),
            dispatcher, multipartProperties());
    MockMultipartFile file = new MockMultipartFile(
            "file", "demo.pdf", "application/pdf", "hello".getBytes(StandardCharsets.UTF_8));

    assertThatThrownBy(() -> uploadService.upload(file, null))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("模拟Redis入队失败");
    assertThat(storageService.deletedObjectName).isNull();
}
```

增加文档创建失败补偿测试：

```java
@Test
void uploadShouldDeleteStoredObjectWhenCreateDocumentFails() {
    RecordingFileStorageService storageService = new RecordingFileStorageService();
    DocumentService documentService = mock(DocumentService.class);
    when(documentService.createDocument(any(CreateDocumentRequest.class)))
            .thenThrow(new ServiceException("模拟文档创建失败"));
    DocumentUploadServiceImpl uploadService = new DocumentUploadServiceImpl(
            storageService, documentService, new ProcessConfigDefaults(),
            new FixedDocumentProcessTaskDispatcher(), multipartProperties());
    MockMultipartFile file = new MockMultipartFile(
            "file", "demo.pdf", "application/pdf", "hello".getBytes(StandardCharsets.UTF_8));

    assertThatThrownBy(() -> uploadService.upload(file, null))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("模拟文档创建失败");
    assertThat(storageService.deletedObjectName).isEqualTo("original/demo.pdf");
}
```

增加补偿失败不覆盖原异常测试。让测试存储实现通过 `deleteException` 控制删除失败：

```java
private RuntimeException deleteException;

@Override
public void delete(String objectName) {
    this.deletedObjectName = objectName;
    if (deleteException != null) {
        throw deleteException;
    }
}
```

测试代码：

```java
@Test
void uploadShouldKeepOriginalExceptionWhenStoredObjectCleanupFails() {
    RecordingFileStorageService storageService = new RecordingFileStorageService();
    storageService.deleteException = new IllegalStateException("模拟对象删除失败");
    RecordingDocumentService documentService = new RecordingDocumentService();
    ServiceException createException = new ServiceException("模拟文档创建失败");
    documentService.createException = createException;
    DocumentUploadServiceImpl uploadService = new DocumentUploadServiceImpl(
            storageService, documentService, new ProcessConfigDefaults(),
            new FixedDocumentProcessTaskDispatcher(), multipartProperties());
    MockMultipartFile file = new MockMultipartFile(
            "file", "demo.pdf", "application/pdf", "hello".getBytes(StandardCharsets.UTF_8));

    assertThatThrownBy(() -> uploadService.upload(file, null))
            .isSameAs(createException)
            .satisfies(exception -> assertThat(exception.getSuppressed())
                    .extracting(Throwable::getMessage)
                    .containsExactly("模拟对象删除失败"));
}
```

- [ ] **Step 2: 编写 Boot Multipart 配置失败测试**

在 `NexaRagApplicationConfigurationTest` 增加：

```java
@Test
void defaultApplicationShouldLimitMultipartUploadSize() throws Exception {
    ClassPathResource resource = new ClassPathResource("application.yml");

    String content = resource.getContentAsString(StandardCharsets.UTF_8);
    assertThat(content).contains("max-file-size: 100MB");
    assertThat(content).contains("max-request-size: 110MB");
}
```

- [ ] **Step 3: 运行测试并确认失败**

Run:

```powershell
mvn -pl nexa-rag-boot -am "-Dtest=DocumentUploadServiceImplTest,NexaRagApplicationConfigurationTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: FAIL，上传服务构造器尚未接收 `MultipartProperties`，配置文件也没有 Multipart 限制。

- [ ] **Step 4: 增加 Spring Multipart 配置**

在 `application.yml` 的 `spring` 节点增加：

```yaml
  servlet:
    multipart:
      max-file-size: 100MB
      max-request-size: 110MB
```

- [ ] **Step 5: 实现上传前校验**

在 `DocumentUploadServiceImpl` 增加依赖：

```java
private final MultipartProperties multipartProperties;
```

调整 `validateFile`：

```java
private void validateFile(MultipartFile file) {
    if (file == null || file.isEmpty()) {
        throw new ClientException("上传文件不能为空", DocumentErrorCode.DOCUMENT_UPLOAD_FILE_INVALID);
    }
    String originalFileName = file.getOriginalFilename();
    if (!StringUtils.hasText(originalFileName)) {
        throw new ClientException("上传文件名不能为空", DocumentErrorCode.DOCUMENT_UPLOAD_FILE_INVALID);
    }

    // 1. 复用 Spring Multipart 配置限制单文件大小
    long maxFileSize = multipartProperties.getMaxFileSize().toBytes();
    if (file.getSize() > maxFileSize) {
        throw new ClientException("上传文件大小超过限制，最大允许=" + multipartProperties.getMaxFileSize(),
                DocumentErrorCode.DOCUMENT_UPLOAD_FILE_INVALID);
    }

    // 2. 在写入对象存储前校验文档类型
    if (FileType.fromFileName(originalFileName) == FileType.UNKNOWN) {
        throw new ClientException("不支持的文档类型，fileName=" + originalFileName,
                DocumentErrorCode.DOCUMENT_FILE_TYPE_UNSUPPORTED);
    }
}
```

- [ ] **Step 6: 实现对象存储失败补偿**

仅将文档记录创建步骤放入 `try-catch`：

```java
StoredFile storedFile = saveOriginalFile(file, originalFileName);
Document uploadedDocument;
try {
    uploadedDocument = documentService.createDocument(buildCreateDocumentRequest(
            safeRequest, originalFileName, storedFile));
} catch (RuntimeException exception) {
    compensateStoredFile(storedFile.objectName(), exception);
    throw exception;
}

ProcessDocumentRequest processRequest = processConfigDefaults.merge(uploadedDocument.getFileType(), safeRequest);
Document queuedDocument = documentService.submitProcess(uploadedDocument.getDocumentId(), processRequest);
DocumentQueueInfo queueInfo = taskDispatcher.enqueue(queuedDocument.getDocumentId());
log.info("文档上传并提交处理成功，documentId={}，status={}",
        queuedDocument.getDocumentId(), queuedDocument.getStatus());
return new UploadDocumentResponse(queuedDocument.getDocumentId(), queuedDocument.getStatus(),
        queueInfo.queuePosition(), queueInfo.waitingCount());
```

新增补偿方法：

```java
private void compensateStoredFile(String objectName, RuntimeException originalException) {
    try {
        // 1. 删除本次上传刚创建的原始对象
        fileStorageService.delete(objectName);
    } catch (RuntimeException cleanupException) {
        log.error("上传流程失败后删除原始对象失败，objectName={}", objectName, cleanupException);
        originalException.addSuppressed(cleanupException);
    }
}
```

- [ ] **Step 7: 运行上传与配置测试**

Run:

```powershell
mvn -pl nexa-rag-boot -am "-Dtest=DocumentUploadServiceImplTest,NexaRagApplicationConfigurationTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: PASS。

- [ ] **Step 8: 提交上传安全修复**

```powershell
git add nexa-rag-boot/src/main/resources/application.yml nexa-rag-boot/src/test/java/com/nexarag/boot/NexaRagApplicationConfigurationTest.java nexa-rag-document/src/main/java/com/nexarag/document/service/impl/DocumentUploadServiceImpl.java nexa-rag-document/src/test/java/com/nexarag/document/service/impl/DocumentUploadServiceImplTest.java
git commit -m "fix(document): 增强文档上传校验和失败补偿"
```

---

### Task 3: 将文档片段查询改为分页接口

**Files:**
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/service/DocumentChunkService.java:13-21`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/service/impl/DocumentChunkServiceImpl.java:29-36`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/converter/DocumentConverter.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/controller/DocumentController.java:146-157`
- Modify: `nexa-rag-document/src/test/java/com/nexarag/document/service/impl/DocumentChunkServiceImplTest.java`
- Modify: `nexa-rag-document/src/test/java/com/nexarag/document/converter/DocumentConverterTest.java`

- [ ] **Step 1: 编写分页 Service 失败测试**

在 `DocumentChunkServiceImplTest` 增加可测试子类，覆盖查询分页对象：

```java
@Test
void pageByDocumentIdShouldNormalizePageArguments() {
    TestableDocumentChunkServiceImpl service = new TestableDocumentChunkServiceImpl();
    Page<DocumentChunk> page = Page.of(1, 20);
    page.setRecords(List.of());
    service.chunkPage = page;

    IPage<DocumentChunk> result = service.pageByDocumentId(1L, 0, 1000);

    assertThat(result).isSameAs(page);
    assertThat(service.queriedDocumentId).isEqualTo(1L);
    assertThat(service.queriedPageNum).isEqualTo(1);
    assertThat(service.queriedPageSize).isEqualTo(100);
}

private static final class TestableDocumentChunkServiceImpl extends DocumentChunkServiceImpl {
    private Long queriedDocumentId;
    private long queriedPageNum;
    private long queriedPageSize;
    private IPage<DocumentChunk> chunkPage;

    @Override
    protected IPage<DocumentChunk> queryChunkPage(Long documentId, long pageNum, long pageSize) {
        this.queriedDocumentId = documentId;
        this.queriedPageNum = pageNum;
        this.queriedPageSize = pageSize;
        return chunkPage;
    }
}
```

- [ ] **Step 2: 编写分页转换失败测试**

在 `DocumentConverterTest` 增加：

```java
@Test
void toChunkPageVOShouldKeepPaginationMetadata() {
    Page<DocumentChunk> page = Page.of(2, 20);
    page.setTotal(41);
    page.setRecords(List.of(DocumentChunk.builder()
            .chunkId("chunk-1")
            .documentId(1L)
            .chunkOrder(20)
            .text("测试片段")
            .status(ChunkStatus.PENDING_INDEX)
            .build()));

    PageVO<DocumentChunkVO> result = DocumentConverter.toChunkPageVO(page);

    assertThat(result.current()).isEqualTo(2);
    assertThat(result.size()).isEqualTo(20);
    assertThat(result.total()).isEqualTo(41);
    assertThat(result.records()).extracting(DocumentChunkVO::chunkId).containsExactly("chunk-1");
}
```

- [ ] **Step 3: 运行测试并确认失败**

Run:

```powershell
mvn -pl nexa-rag-document -am "-Dtest=DocumentChunkServiceImplTest,DocumentConverterTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: FAIL，分页方法和分页转换方法不存在。

- [ ] **Step 4: 修改 Service 分页接口**

保留 `listByDocumentId` 供检索索引内部流程读取完整片段；新增分页方法，Controller 不再调用全量方法：

```java
/**
 * 分页查询指定文档的片段。
 *
 * @param documentId 文档ID
 * @param pageNum    页码
 * @param pageSize   每页数量
 * @return 文档片段分页数据
 */
IPage<DocumentChunk> pageByDocumentId(Long documentId, long pageNum, long pageSize);
```

在实现类增加：

```java
private static final long DEFAULT_PAGE_SIZE = 20;
private static final long MAX_PAGE_SIZE = 100;

@Override
public IPage<DocumentChunk> pageByDocumentId(Long documentId, long pageNum, long pageSize) {
    long safePageNum = pageNum <= 0 ? 1 : pageNum;
    long safePageSize = pageSize <= 0 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
    return queryChunkPage(documentId, safePageNum, safePageSize);
}

protected IPage<DocumentChunk> queryChunkPage(Long documentId, long pageNum, long pageSize) {
    Page<DocumentChunk> page = Page.of(pageNum, pageSize);
    return this.lambdaQuery()
            .eq(DocumentChunk::getDocumentId, documentId)
            .orderByAsc(DocumentChunk::getChunkOrder)
            .page(page);
}
```

- [ ] **Step 5: 增加分页转换**

在 `DocumentConverter` 增加：

```java
/**
 * 将文档片段分页数据转换为分页响应。
 *
 * @param page 文档片段分页数据
 * @return 文档片段分页响应
 */
public static PageVO<DocumentChunkVO> toChunkPageVO(IPage<DocumentChunk> page) {
    return PageVO.<DocumentChunkVO>builder()
            .records(page.getRecords().stream().map(DocumentConverter::toChunkVO).toList())
            .total(page.getTotal())
            .current(page.getCurrent())
            .size(page.getSize())
            .pages(page.getPages())
            .build();
}
```

- [ ] **Step 6: 修改 Controller 响应**

将接口修改为：

```java
@GetMapping("/{documentId}/chunks")
public Result<PageVO<DocumentChunkVO>> listChunks(
        @PathVariable Long documentId,
        @RequestParam(defaultValue = "1") long pageNum,
        @RequestParam(defaultValue = "20") long pageSize) {
    return Results.success(DocumentConverter.toChunkPageVO(
            documentChunkService.pageByDocumentId(documentId, pageNum, pageSize)));
}
```

删除不再使用的 `java.util.List` 导入。

- [ ] **Step 7: 运行分页相关测试**

Run:

```powershell
mvn -pl nexa-rag-document -am "-Dtest=DocumentChunkServiceImplTest,DocumentConverterTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: PASS。

- [ ] **Step 8: 编译调用方并修正旧接口引用**

Run:

```powershell
mvn -pl nexa-rag-boot -am -DskipTests compile
```

Expected: BUILD SUCCESS。检索模块和集成测试继续使用 `listByDocumentId`，只有 HTTP Controller 改用 `pageByDocumentId`。

- [ ] **Step 9: 提交分页改造**

```powershell
git add nexa-rag-document/src/main/java/com/nexarag/document/service/DocumentChunkService.java nexa-rag-document/src/main/java/com/nexarag/document/service/impl/DocumentChunkServiceImpl.java nexa-rag-document/src/main/java/com/nexarag/document/converter/DocumentConverter.java nexa-rag-document/src/main/java/com/nexarag/document/controller/DocumentController.java nexa-rag-document/src/test/java/com/nexarag/document/service/impl/DocumentChunkServiceImplTest.java nexa-rag-document/src/test/java/com/nexarag/document/converter/DocumentConverterTest.java
git commit -m "feat(document): 支持文档片段分页查询"
```

---

### Task 4: 增加嵌套配置校验和正则安全限制

**Files:**
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/dto/SplitConfigRequest.java:18-29`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/dto/RegexSplitOptions.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/dto/ExcelSplitOptions.java`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/splitter/text/RegexSafetyValidator.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/splitter/text/RegexTextDocumentSplitter.java:23-66`
- Create: `nexa-rag-document/src/test/java/com/nexarag/document/dto/DocumentRequestValidationTest.java`
- Create: `nexa-rag-document/src/test/java/com/nexarag/document/splitter/text/RegexSafetyValidatorTest.java`
- Modify: `nexa-rag-document/src/test/java/com/nexarag/document/splitter/text/RegexTextDocumentSplitterTest.java`

- [ ] **Step 1: 编写嵌套 Bean Validation 失败测试**

创建 `DocumentRequestValidationTest`：

```java
package com.nexarag.document.dto;

import com.nexarag.document.enums.SplitStrategy;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文档请求参数校验测试。
 */
class DocumentRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void uploadRequestShouldValidateNestedRegexLength() {
        RegexSplitOptions regex = new RegexSplitOptions(null, "a".repeat(257), false);
        SplitConfigRequest splitConfig = new SplitConfigRequest(
                SplitStrategy.REGEX_TEXT, 1000, 100, null, regex, null);
        UploadDocumentRequest request = new UploadDocumentRequest(null, null, splitConfig, null, null);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("splitConfig.regex.regex");
    }

    @Test
    void processRequestShouldValidateNestedExcelMaxRows() {
        ExcelSplitOptions excel = new ExcelSplitOptions(ExcelSplitMode.KEY_VALUE, true, null, 10001);
        SplitConfigRequest splitConfig = new SplitConfigRequest(
                SplitStrategy.EXCEL, 1000, 100, null, null, excel);
        ProcessDocumentRequest request = new ProcessDocumentRequest(splitConfig, null, null);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("splitConfig.excel.maxRowsPerChunk");
    }
}
```

- [ ] **Step 2: 编写正则安全校验失败测试**

创建 `RegexSafetyValidatorTest`：

```java
package com.nexarag.document.splitter.text;

import com.nexarag.common.exception.ServiceException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 正则安全校验器测试。
 */
class RegexSafetyValidatorTest {

    private final RegexSafetyValidator validator = new RegexSafetyValidator();

    @Test
    void validateShouldAcceptSimpleRegex() {
        assertThatCode(() -> validator.validate("\\r?\\n"))
                .doesNotThrowAnyException();
    }

    @Test
    void validateShouldRejectInvalidRegex() {
        assertThatThrownBy(() -> validator.validate("["))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("正则表达式语法不合法");
    }

    @Test
    void validateShouldRejectNestedQuantifier() {
        assertThatThrownBy(() -> validator.validate("(a+)+"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("嵌套量词");
    }

    @Test
    void validateShouldRejectTooLongRegex() {
        assertThatThrownBy(() -> validator.validate("a".repeat(257)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不能超过256个字符");
    }
}
```

- [ ] **Step 3: 运行测试并确认失败**

Run:

```powershell
mvn -pl nexa-rag-document -am "-Dtest=DocumentRequestValidationTest,RegexSafetyValidatorTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: FAIL，嵌套约束和 `RegexSafetyValidator` 尚不存在。

- [ ] **Step 4: 增加嵌套配置约束**

在 `SplitConfigRequest` 增加 `jakarta.validation.Valid` 导入，并修改字段：

```java
@Valid MarkdownSplitOptions markdown,
@Valid RegexSplitOptions regex,
@Valid ExcelSplitOptions excel
```

修改 `RegexSplitOptions`：

```java
public record RegexSplitOptions(
        String separator,
        @Size(max = 256, message = "正则表达式不能超过256个字符") String regex,
        Boolean keepSeparator) {
}
```

修改 `ExcelSplitOptions`：

```java
@Min(value = 1, message = "每个片段最多行数不能小于1")
@Max(value = 10000, message = "每个片段最多行数不能超过10000")
Integer maxRowsPerChunk
```

- [ ] **Step 5: 实现正则安全校验器**

创建 `RegexSafetyValidator`：

```java
package com.nexarag.document.splitter.text;

import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.error.DocumentErrorCode;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 正则表达式安全校验器，负责限制正则长度并拒绝常见高风险嵌套量词。
 */
@Component
public class RegexSafetyValidator {

    private static final int MAX_REGEX_LENGTH = 256;
    private static final Pattern NESTED_QUANTIFIER_PATTERN =
            Pattern.compile("\\([^)]*[+*][^)]*\\)[+*?]");

    /**
     * 校验并编译自定义正则表达式。
     *
     * @param regex 自定义正则表达式
     * @return 编译后的正则对象
     */
    public Pattern validateAndCompile(String regex) {
        if (regex.length() > MAX_REGEX_LENGTH) {
            throw new ServiceException("正则表达式不能超过256个字符",
                    DocumentErrorCode.DOCUMENT_PROCESS_CONFIG_INVALID);
        }
        if (NESTED_QUANTIFIER_PATTERN.matcher(regex).find()) {
            throw new ServiceException("正则表达式不能包含高风险嵌套量词",
                    DocumentErrorCode.DOCUMENT_PROCESS_CONFIG_INVALID);
        }
        try {
            return Pattern.compile(regex);
        } catch (PatternSyntaxException exception) {
            throw new ServiceException("正则表达式语法不合法", exception,
                    DocumentErrorCode.DOCUMENT_PROCESS_CONFIG_INVALID);
        }
    }

    /**
     * 校验自定义正则表达式。
     *
     * @param regex 自定义正则表达式
     */
    public void validate(String regex) {
        validateAndCompile(regex);
    }
}
```

- [ ] **Step 6: 接入文本切分器**

在 `RegexTextDocumentSplitter` 增加依赖：

```java
private final RegexSafetyValidator regexSafetyValidator;
```

修改正则分支：

```java
if (options != null && StringUtils.hasText(options.regex())) {
    return regexSafetyValidator.validateAndCompile(options.regex())
            .splitAsStream(content)
            .toList();
}
```

调整 `RegexTextDocumentSplitterTest` 构造器：

```java
RegexTextDocumentSplitter splitter = new RegexTextDocumentSplitter(
        new TextWindowSplitter(), new DocumentChunkIdGenerator(), new RegexSafetyValidator());
```

增加切分器异常测试：

```java
@Test
void splitShouldRejectNestedQuantifierRegex() {
    RegexTextDocumentSplitter splitter = new RegexTextDocumentSplitter(
            new TextWindowSplitter(), new DocumentChunkIdGenerator(), new RegexSafetyValidator());
    SplitConfigRequest splitConfig = new SplitConfigRequest(
            SplitStrategy.REGEX_TEXT, 1000, 100, null,
            new RegexSplitOptions(null, "(a+)+", false), null);
    DocumentSplitContext context = new DocumentSplitContext(
            1L, "测试文档", "demo.txt", FileType.TEXT,
            "original/demo.txt", null, null, null, "text/plain",
            "aaaaaaaaaaaaaaaa", null, splitConfig);

    assertThatThrownBy(() -> splitter.split(context))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("嵌套量词");
}
```

- [ ] **Step 7: 运行正则与校验测试**

Run:

```powershell
mvn -pl nexa-rag-document -am "-Dtest=DocumentRequestValidationTest,RegexSafetyValidatorTest,RegexTextDocumentSplitterTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: PASS。

- [ ] **Step 8: 提交配置安全修复**

```powershell
git add nexa-rag-document/src/main/java/com/nexarag/document/dto/SplitConfigRequest.java nexa-rag-document/src/main/java/com/nexarag/document/dto/RegexSplitOptions.java nexa-rag-document/src/main/java/com/nexarag/document/dto/ExcelSplitOptions.java nexa-rag-document/src/main/java/com/nexarag/document/splitter/text/RegexSafetyValidator.java nexa-rag-document/src/main/java/com/nexarag/document/splitter/text/RegexTextDocumentSplitter.java nexa-rag-document/src/test/java/com/nexarag/document/dto/DocumentRequestValidationTest.java nexa-rag-document/src/test/java/com/nexarag/document/splitter/text/RegexSafetyValidatorTest.java nexa-rag-document/src/test/java/com/nexarag/document/splitter/text/RegexTextDocumentSplitterTest.java
git commit -m "fix(document): 限制文档切分配置和自定义正则"
```

---

### Task 5: 执行跨模块回归验证

**Files:**
- Verify only: `nexa-rag-document`
- Verify only: `nexa-rag-boot`

- [ ] **Step 1: 运行 Document 模块完整测试**

Run:

```powershell
mvn -pl nexa-rag-document -am test -DskipITs
```

Expected: BUILD SUCCESS，Document 模块测试无失败。

- [ ] **Step 2: 运行 Boot 模块完整测试**

确保开发机 Redis、MySQL 等测试依赖已启动，然后运行：

```powershell
mvn -pl nexa-rag-boot -am test -DskipITs
```

Expected: BUILD SUCCESS，Boot 启动测试能够读取 Multipart 配置。

- [ ] **Step 3: 检查最终变更范围**

Run:

```powershell
git status --short --branch
git diff --check
git log -6 --oneline
```

Expected:

- 没有未提交的本次任务文件。
- `.superpowers/` 仍保持未跟踪且未提交。
- 最近提交依次覆盖事务、上传、分页和正则安全修复。
