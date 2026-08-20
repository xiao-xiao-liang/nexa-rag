# 租户隔离知识库后端 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在空数据环境中引入租户隔离的知识库，并将文档管理和 RAG 检索改为按知识库强制隔离。

**Architecture:** `knowledge_base` 成为文档的聚合根，`document.knowledge_base_id` 为不可变归属。首期通过 document 模块内的固定租户提供者解析默认租户，后续 Sa-Token `auth` 模块以同一接口替换实现。所有索引写入 tenant/knowledge-base 元数据；所有检索通道在查询阶段使用范围过滤。

**Tech Stack:** Java 21、Spring Boot 3、MyBatis-Plus、Flyway、MySQL、Spring AI Milvus、Spring Data Elasticsearch、JUnit 5、AssertJ、Mockito。

---

## 文件结构

| 路径 | 职责 |
| --- | --- |
| `nexa-rag-boot/src/main/resources/db/migration/V20__add_tenant_scoped_knowledge_base.sql` | 创建知识库、初始化默认租户/默认知识库、为文档增加不可空归属及索引。 |
| `nexa-rag-boot/src/main/resources/db/schema/nexa_rag_schema.sql` | 空库安装时的完整 schema，与 V20 保持等价。 |
| `nexa-rag-document/.../tenant/*` | 当前租户边界及首期固定默认租户实现。 |
| `nexa-rag-document/.../model/dataobject/KnowledgeBaseDO.java` | 知识库持久化数据对象。 |
| `nexa-rag-document/.../service/KnowledgeBaseService*.java` | 知识库 CRUD、名称约束、默认库保护、统计和文档归属校验。 |
| `nexa-rag-document/.../controller/KnowledgeBaseController.java` | 知识库 REST 管理接口。 |
| `nexa-rag-document/.../controller/DocumentController.java` | 改为知识库嵌套的文档接口。 |
| `nexa-rag-retrieval/...` | 索引元数据写入与向量、关键词、章节检索范围过滤。 |
| `nexa-rag-workflow/...`、`nexa-rag-boot/...` | 将聊天请求中的可选知识库范围传入检索节点。 |

## Task 1: 先锁定数据库契约与空环境初始化

**Files:**
- Create: `nexa-rag-boot/src/main/resources/db/migration/V20__add_tenant_scoped_knowledge_base.sql`
- Modify: `nexa-rag-boot/src/main/resources/db/schema/nexa_rag_schema.sql`
- Create: `nexa-rag-document/src/test/java/com/nexarag/document/KnowledgeBaseSchemaContractTest.java`

- [ ] **Step 1: 编写 schema 契约失败测试**

断言 V20 与完整 schema 均包含 `knowledge_base`，并验证如下列与索引：

```java
assertColumn(definition, "knowledge_base_id", "BIGINT NOT NULL");
assertColumn(definition, "tenant_id", "VARCHAR(64) NOT NULL");
assertColumn(definition, "name", "VARCHAR(128) NOT NULL");
assertColumn(definition, "active_name_key", "VARCHAR(128) NULL");
assertColumn(definition, "is_default", "TINYINT NOT NULL DEFAULT 0");
assertThat(definition).contains("UNIQUE KEY uk_knowledge_base_tenant_active_name (tenant_id, active_name_key)");
assertThat(definition).contains("UNIQUE KEY uk_knowledge_base_default_tenant (default_tenant_key)");
assertThat(documentAlter).contains("knowledge_base_id BIGINT NOT NULL");
assertThat(documentAlter).contains("KEY idx_document_knowledge_base_status (knowledge_base_id, del_flag, status)");
```

复用 `DocumentSectionSchemaContractTest` 的仓库根目录定位与 SQL 片段提取方式。

- [ ] **Step 2: 运行测试，确认当前失败**

Run: `mvn -pl nexa-rag-document -am -Dtest=KnowledgeBaseSchemaContractTest test`

Expected: FAIL，因为 V20、表定义和 `document.knowledge_base_id` 尚不存在。

- [ ] **Step 3: 编写 V20 与完整 schema**

在 V20 创建如下表，再为 `document` 增加归属字段和聚合索引；不要添加任何历史文档回填、ES/Milvus 重建或删除语句。

```sql
CREATE TABLE IF NOT EXISTS knowledge_base (
    knowledge_base_id BIGINT NOT NULL COMMENT '知识库ID',
    tenant_id VARCHAR(64) NOT NULL COMMENT '租户ID',
    name VARCHAR(128) NOT NULL COMMENT '知识库名称',
    active_name_key VARCHAR(128) NULL COMMENT '有效名称规范键',
    description VARCHAR(1024) NULL COMMENT '知识库描述',
    is_default TINYINT NOT NULL DEFAULT 0 COMMENT '是否默认知识库：0否，1是',
    default_tenant_key VARCHAR(64) NULL COMMENT '默认库租户唯一键',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    create_by VARCHAR(64) NULL COMMENT '创建人',
    update_by VARCHAR(64) NULL COMMENT '更新人',
    del_flag TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记：0未删除，1已删除',
    delete_time DATETIME NULL COMMENT '删除时间',
    version INT NOT NULL DEFAULT 0 COMMENT '版本号',
    PRIMARY KEY (knowledge_base_id),
    UNIQUE KEY uk_knowledge_base_tenant_active_name (tenant_id, active_name_key),
    UNIQUE KEY uk_knowledge_base_default_tenant (default_tenant_key),
    KEY idx_knowledge_base_tenant_update (tenant_id, del_flag, update_time)
) COMMENT='知识库表';
```

以 `INSERT ... SELECT ... WHERE NOT EXISTS` 初始化稳定的 `DEFAULT_TENANT_ID` 和 `DEFAULT_KNOWLEDGE_BASE_ID`；默认记录的 `active_name_key` 使用同一名称规范化函数的结果，`default_tenant_key` 等于该租户 ID。将相同 `knowledge_base` 定义放入完整 schema 的 `document` 表之前。

- [ ] **Step 4: 运行 schema 契约测试**

Run: `mvn -pl nexa-rag-document -am -Dtest=KnowledgeBaseSchemaContractTest test`

Expected: PASS。

## Task 2: 建立租户、知识库领域对象与基础 DTO

**Files:**
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/tenant/CurrentTenantProvider.java`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/tenant/FixedCurrentTenantProvider.java`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/tenant/TenantConstants.java`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/model/dataobject/KnowledgeBaseDO.java`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/mapper/KnowledgeBaseMapper.java`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/model/dto/CreateKnowledgeBaseDTO.java`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/model/dto/UpdateKnowledgeBaseDTO.java`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/model/vo/KnowledgeBaseStatisticsVO.java`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/model/vo/KnowledgeBaseSummaryVO.java`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/model/vo/KnowledgeBaseDetailVO.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/model/entity/Document.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/enums/DocumentErrorCode.java`
- Test: `nexa-rag-document/src/test/java/com/nexarag/document/tenant/FixedCurrentTenantProviderTest.java`
- Test: `nexa-rag-document/src/test/java/com/nexarag/document/model/KnowledgeBaseModelTest.java`

- [ ] **Step 1: 编写模型与固定租户失败测试**

```java
assertThat(provider.getRequiredTenantId()).isEqualTo(TenantConstants.DEFAULT_TENANT_ID);
assertThat(KnowledgeBaseDO.class.getDeclaredField("isDefault").getType()).isEqualTo(Integer.class);
assertThat(Document.class.getDeclaredField("knowledgeBaseId").getType()).isEqualTo(Long.class);
assertThat(new CreateKnowledgeBaseDTO("产品资料", "面向产品团队").name()).isEqualTo("产品资料");
```

- [ ] **Step 2: 运行测试，确认失败**

Run: `mvn -pl nexa-rag-document -am -Dtest=FixedCurrentTenantProviderTest,KnowledgeBaseModelTest test`

Expected: FAIL，因为类型、租户提供者和知识库字段尚不存在。

- [ ] **Step 3: 实现领域对象与校验**

定义稳定的租户接口，并保留未来 Sa-Token 替换点：

```java
public interface CurrentTenantProvider {
    String getRequiredTenantId();
}

@Component
public class FixedCurrentTenantProvider implements CurrentTenantProvider {
    @Override
    public String getRequiredTenantId() {
        return TenantConstants.DEFAULT_TENANT_ID;
    }
}
```

`KnowledgeBaseDO` 使用 MyBatis-Plus 的 `@TableName("knowledge_base")`、`@TableLogic`、`@Version` 与现有 `Document` 审计字段风格。`CreateKnowledgeBaseDTO` 的名称为必填、最多 128 字符，描述最多 1024 字符；`UpdateKnowledgeBaseDTO` 允许更新名称和描述。`Document` 新增 `Long knowledgeBaseId`，不向任何现有文档请求 DTO 暴露可写字段。新增错误码：知识库不存在、名称冲突、默认库受保护、知识库非空、知识库范围非法。

- [ ] **Step 4: 运行模型测试**

Run: `mvn -pl nexa-rag-document -am -Dtest=FixedCurrentTenantProviderTest,KnowledgeBaseModelTest test`

Expected: PASS。

## Task 3: 以测试驱动知识库服务、统计和删除保护

**Files:**
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/service/KnowledgeBaseService.java`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/service/impl/KnowledgeBaseServiceImpl.java`
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/converter/KnowledgeBaseConverter.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/mapper/DocumentMapper.java`
- Test: `nexa-rag-document/src/test/java/com/nexarag/document/service/impl/KnowledgeBaseServiceImplTest.java`

- [ ] **Step 1: 编写知识库服务失败测试**

覆盖以下最小行为：同租户规范化名称冲突拒绝、跨租户同名允许、默认库更新/删除拒绝、非空库删除拒绝、空普通库删除成功、统计状态映射及文档归属验证。

```java
assertThatThrownBy(() -> service.update(defaultKnowledgeBaseId, request))
        .hasMessageContaining("默认知识库")
        .isInstanceOf(ClientException.class);
assertThatThrownBy(() -> service.delete(nonEmptyKnowledgeBaseId))
        .hasMessageContaining("知识库不为空");
assertThat(service.statistics(knowledgeBaseId))
        .isEqualTo(new KnowledgeBaseStatisticsVO(7, 1, 3, 2, 1));
assertThatThrownBy(() -> service.getRequiredDocument(knowledgeBaseId, foreignDocumentId))
        .hasMessageContaining("文档不存在");
```

- [ ] **Step 2: 运行测试，确认失败**

Run: `mvn -pl nexa-rag-document -am -Dtest=KnowledgeBaseServiceImplTest test`

Expected: FAIL，因为服务与聚合查询尚不存在。

- [ ] **Step 3: 实现最小服务和聚合查询**

`KnowledgeBaseService` 至少定义以下方法：

```java
KnowledgeBaseDetailVO create(CreateKnowledgeBaseDTO request);
PageVO<KnowledgeBaseSummaryVO> page(long pageNum, long pageSize);
KnowledgeBaseDetailVO getRequired(Long knowledgeBaseId);
KnowledgeBaseDetailVO update(Long knowledgeBaseId, UpdateKnowledgeBaseDTO request);
void delete(Long knowledgeBaseId);
Document getRequiredDocument(Long knowledgeBaseId, Long documentId);
Set<Long> validateRequestedKnowledgeBases(Collection<Long> knowledgeBaseIds);
boolean isDocumentInCurrentTenantScope(Long documentId, Set<Long> knowledgeBaseIds);
```

实现要求：

- 通过 `CurrentTenantProvider` 限定所有知识库读写；查不到或不属于当前租户均抛出知识库不存在。
- 规范化名称使用 `trim()`；创建/重命名时写入 `activeNameKey`。逻辑删除前在同一条件更新中将 `activeNameKey`、`defaultTenantKey` 置空，以释放唯一键。
- 仅 `isDefault == 1` 的库禁止更新和删除；普通库删除前用 `documentMapper.selectCount` 查询 `knowledge_base_id` 且未逻辑删除的文档。
- 用单条按状态聚合查询计算 `total`、`pending(UPLOADED)`、`processing(QUEUED/PARSING/PARSED/CHUNKING/CHUNKED/INDEXING)`、`indexed(INDEXED)`、`failed(FAILED)`；列表与详情均调用同一投影构造方法。
- `getRequiredDocument` 必须把 `document_id`、`knowledge_base_id` 和逻辑删除条件写在同一查询中，禁止先按裸 ID 查询再在内存比较。
- `validateRequestedKnowledgeBases` 对空集合返回空不可变集合；对非空集合一次性校验全部标识属于当前租户，发现任一非法标识即拒绝。`isDocumentInCurrentTenantScope` 通过文档—知识库归属查询验证章节正文读取：空集合表示当前租户全部知识库，非空集合再追加知识库集合条件。

- [ ] **Step 4: 运行服务测试**

Run: `mvn -pl nexa-rag-document -am -Dtest=KnowledgeBaseServiceImplTest test`

Expected: PASS。

## Task 4: 将文档生命周期全部绑定到知识库父资源

**Files:**
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/service/DocumentService.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/service/impl/DocumentServiceImpl.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/service/DocumentPipelineSubmitService.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/service/impl/DocumentPipelineSubmitServiceImpl.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/service/DocumentUploadService.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/service/impl/DocumentUploadServiceImpl.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/service/impl/ExternalDocumentSubmitServiceImpl.java`
- Test: `nexa-rag-document/src/test/java/com/nexarag/document/service/impl/DocumentServiceImplTest.java`
- Test: `nexa-rag-document/src/test/java/com/nexarag/document/service/impl/DocumentUploadServiceImplTest.java`
- Test: `nexa-rag-document/src/test/java/com/nexarag/document/service/impl/ExternalDocumentSubmitServiceImplTest.java`

- [ ] **Step 1: 先扩展失败测试的父资源断言**

为创建、列表、上传、外部导入、处理、重试、删除、状态、概览和分块入口增加 `knowledgeBaseId` 参数，并断言上传及外部导入传给 `createAndSubmit` 的文档拥有路径指定归属：

```java
verify(knowledgeBaseService).getRequired(22L);
assertThat(savedDocument.getKnowledgeBaseId()).isEqualTo(22L);
assertThat(service.pageDocuments(22L, 1, 20).records()).allMatch(
        document -> document.knowledgeBaseId().equals(22L));
```

- [ ] **Step 2: 运行文档服务测试，确认失败**

Run: `mvn -pl nexa-rag-document -am -Dtest=DocumentServiceImplTest,DocumentUploadServiceImplTest,ExternalDocumentSubmitServiceImplTest test`

Expected: FAIL，因为现有服务签名不带知识库 ID，且创建的 `Document` 未设置归属。

- [ ] **Step 3: 逐层传递并校验 knowledgeBaseId**

采用以下服务签名方向，避免把知识库 ID 放入客户端请求体：

```java
Document createDocument(Long knowledgeBaseId, CreateDocumentRequest request);
PageVO<DocumentSummaryVO> pageDocuments(Long knowledgeBaseId, long pageNum, long pageSize);
DocumentProcessStatusVO submitProcess(Long knowledgeBaseId, Long documentId, ProcessDocumentRequest request);
DocumentProcessStatusVO retryProcess(Long knowledgeBaseId, Long documentId);
Document createAndSubmit(Long knowledgeBaseId, CreateDocumentRequest request,
                         ProcessDocumentRequest processRequest);
```

`DocumentServiceImpl` 创建前调用 `knowledgeBaseService.getRequired(knowledgeBaseId)`，构建实体时写入 `knowledgeBaseId`；所有对外文档读取入口先调用 `knowledgeBaseService.getRequiredDocument(knowledgeBaseId, documentId)`。异步消费者、分块器、索引器等内部仅依赖 `documentId` 的既有任务链路保持不变，因为它们从数据库加载的文档已经带有不可变归属。

- [ ] **Step 4: 运行文档生命周期测试**

Run: `mvn -pl nexa-rag-document -am -Dtest=DocumentServiceImplTest,DocumentUploadServiceImplTest,ExternalDocumentSubmitServiceImplTest test`

Expected: PASS，且原有处理状态机测试继续通过。

## Task 5: 发布知识库与嵌套文档 REST 契约

**Files:**
- Create: `nexa-rag-document/src/main/java/com/nexarag/document/controller/KnowledgeBaseController.java`
- Modify: `nexa-rag-document/src/main/java/com/nexarag/document/controller/DocumentController.java`
- Create: `nexa-rag-document/src/test/java/com/nexarag/document/controller/KnowledgeBaseControllerTest.java`
- Create: `nexa-rag-document/src/test/java/com/nexarag/document/controller/KnowledgeBaseDocumentControllerTest.java`

- [ ] **Step 1: 编写控制器失败测试**

使用 `MockMvc` 或当前项目已采用的 Web MVC 测试基座，断言如下路径存在，旧根路径不再映射：

```java
mockMvc.perform(post("/api/knowledge-bases")
        .contentType(APPLICATION_JSON)
        .content("{\"name\":\"产品资料\",\"description\":\"说明\"}"))
        .andExpect(status().isOk());
mockMvc.perform(get("/api/knowledge-bases/22/documents"))
        .andExpect(status().isOk());
mockMvc.perform(get("/api/documents"))
        .andExpect(status().isNotFound());
```

- [ ] **Step 2: 运行控制器测试，确认失败**

Run: `mvn -pl nexa-rag-document -am -Dtest=KnowledgeBaseControllerTest,KnowledgeBaseDocumentControllerTest test`

Expected: FAIL，因为知识库控制器、嵌套路由和旧映射移除尚未完成。

- [ ] **Step 3: 实现控制器路径和归属调用**

实现：

```text
POST/GET                    /api/knowledge-bases
GET/PATCH/DELETE            /api/knowledge-bases/{knowledgeBaseId}
POST/GET                    /api/knowledge-bases/{knowledgeBaseId}/documents
POST                        /api/knowledge-bases/{knowledgeBaseId}/documents/upload
POST                        /api/knowledge-bases/{knowledgeBaseId}/documents/external
GET/DELETE                  /api/knowledge-bases/{knowledgeBaseId}/documents/{documentId}
GET                         /.../{documentId}/overview
POST                        /.../{documentId}/process
POST                        /.../{documentId}/retry
GET                         /.../{documentId}/process-status
GET                         /.../{documentId}/chunks
```

移除 `@RequestMapping("/api/documents")` 的旧控制器映射，不保留过渡端点。控制器只提取路径参数和 DTO；租户、默认库和文档归属校验必须保留在服务层。

- [ ] **Step 4: 运行控制器测试**

Run: `mvn -pl nexa-rag-document -am -Dtest=KnowledgeBaseControllerTest,KnowledgeBaseDocumentControllerTest test`

Expected: PASS。

## Task 6: 为全部索引写入附加租户与知识库元数据

**Files:**
- Create: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/model/DocumentIndexScope.java`
- Modify: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/constants/SpringAiVectorStoreMetadataConstants.java`
- Modify: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/constants/DocumentIndexFieldConstants.java`
- Modify: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/model/IndexableChunk.java`
- Modify: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/model/KeywordIndexDocument.java`
- Modify: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/index/keyword/KeywordIndexDocumentDO.java`
- Modify: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/index/vector/DocumentVectorStore.java`
- Modify: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/index/vector/SpringAiDocumentVectorStore.java`
- Modify: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/service/impl/DocumentIndexServiceImpl.java`
- Modify: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/repository/SectionNavigationIndexRepository.java`
- Modify: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/repository/SectionNavigationIndexRepositoryImpl.java`
- Test: `nexa-rag-retrieval/src/test/java/com/nexarag/retrieval/index/vector/SpringAiDocumentVectorStoreTest.java`
- Test: `nexa-rag-retrieval/src/test/java/com/nexarag/retrieval/service/impl/DocumentIndexServiceImplTest.java`
- Test: `nexa-rag-retrieval/src/test/java/com/nexarag/retrieval/repository/SectionNavigationIndexRepositoryImplTest.java`

- [ ] **Step 1: 编写索引元数据失败测试**

```java
assertThat(vectorDocument.getMetadata())
        .containsEntry("tenantId", "default-tenant")
        .containsEntry("knowledgeBaseId", 22L);
assertThat(keywordDocument.tenantId()).isEqualTo("default-tenant");
assertThat(keywordDocument.knowledgeBaseId()).isEqualTo(22L);
```

章节导航的 `KeywordIndexDocument` 也必须包含相同的租户和知识库值。

- [ ] **Step 2: 运行索引写入测试，确认失败**

Run: `mvn -pl nexa-rag-retrieval -am -Dtest=SpringAiDocumentVectorStoreTest,DocumentIndexServiceImplTest,SectionNavigationIndexRepositoryImplTest test`

Expected: FAIL，因为当前索引模型只携带 `documentId`。

- [ ] **Step 3: 实现统一索引归属载荷**

将 `tenantId`、`knowledgeBaseId` 加入 `IndexableChunk`、`KeywordIndexDocument`、ES DO 与 Spring AI metadata 常量。`DocumentIndexServiceImpl` 在加载文档后，将其 `knowledgeBaseId` 和由知识库服务解析出的 `tenantId` 一次性传入正文向量、正文关键词和章节导航写入；写入方法不从请求参数或全局状态猜测归属。

将 `DocumentVectorStore.replaceDocument` 的入参替换为包含文档、租户和片段的明确写入请求对象，例如：

```java
public record DocumentIndexScope(String tenantId, Long knowledgeBaseId) {}
List<VectorIndexWriteResult> replaceDocument(Long documentId, DocumentIndexScope scope,
                                             List<IndexableChunk> chunks);
```

同步更新所有 stub、mock 与 `None` 实现，确保索引替换和删除仍按 `documentId` 执行。

- [ ] **Step 4: 运行索引写入测试**

Run: `mvn -pl nexa-rag-retrieval -am -Dtest=SpringAiDocumentVectorStoreTest,DocumentIndexServiceImplTest,SectionNavigationIndexRepositoryImplTest test`

Expected: PASS。

## Task 7: 在向量、关键词和章节查询阶段过滤检索范围

**Files:**
- Create: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/model/RetrievalScopeFilter.java`
- Modify: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/dto/req/ConversationRetrievalRequest.java`
- Modify: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/dto/req/KeywordIndexSearchRequest.java`
- Modify: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/index/vector/DocumentVectorStore.java`
- Modify: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/index/vector/SpringAiDocumentVectorStore.java`
- Modify: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/index/keyword/ElasticsearchKeywordIndexClient.java`
- Modify: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/retriever/vector/MilvusConversationRetriever.java`
- Modify: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/retriever/keyword/Bm25ConversationRetriever.java`
- Modify: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/repository/SectionNavigationIndexRepository.java`
- Modify: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/repository/SectionNavigationIndexRepositoryImpl.java`
- Modify: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/retriever/SectionExpansionRetriever.java`
- Modify: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/repository/SectionContentRepository.java`
- Modify: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/repository/SectionContentRepositoryImpl.java`
- Test: `nexa-rag-retrieval/src/test/java/com/nexarag/retrieval/index/vector/SpringAiDocumentVectorStoreTest.java`
- Test: `nexa-rag-retrieval/src/test/java/com/nexarag/retrieval/index/keyword/ElasticsearchKeywordIndexClientTest.java`
- Test: `nexa-rag-retrieval/src/test/java/com/nexarag/retrieval/retriever/SectionExpansionRetrieverFixtureTest.java`

- [ ] **Step 1: 编写查询阶段过滤失败测试**

```java
RetrievalScopeFilter filter = new RetrievalScopeFilter("default-tenant", Set.of(22L, 23L));
documentVectorStore.search("退款规则", 10, filter);
assertThat(capturedSearchRequest.getFilterExpression()).contains("tenantId").contains("knowledgeBaseId");

keywordClient.search(new KeywordIndexSearchRequest(null, "退款规则", 10, filter));
assertThat(capturedNativeQuery).containsTerm("tenant_id", "default-tenant")
        .containsTerms("knowledge_base_id", List.of(22L, 23L));
```

章节导航搜索与 `SectionContentRepository` 的数据库读取也必须接收同一过滤对象，确保导航命中不能扩展到范围外文档。

- [ ] **Step 2: 运行过滤测试，确认失败**

Run: `mvn -pl nexa-rag-retrieval -am -Dtest=SpringAiDocumentVectorStoreTest,ElasticsearchKeywordIndexClientTest,SectionExpansionRetrieverFixtureTest test`

Expected: FAIL，因为检索请求尚无 tenant/knowledge-base 过滤条件。

- [ ] **Step 3: 实现不可为空的检索范围对象与三通道过滤**

定义：

```java
public record RetrievalScopeFilter(String tenantId, Set<Long> knowledgeBaseIds) {
    public RetrievalScopeFilter {
        if (!StringUtils.hasText(tenantId)) throw new IllegalArgumentException("租户ID不能为空");
        knowledgeBaseIds = knowledgeBaseIds == null ? Set.of() : Set.copyOf(knowledgeBaseIds);
    }
}
```

`knowledgeBaseIds` 为空表示当前租户全范围，仍必须过滤 `tenantId`。向量查询构造 Spring AI `Filter.Expression`：始终 `tenantId ==`，并在集合非空时追加 `knowledgeBaseId IN`。ES `bool.filter` 使用 `term tenant_id` 与可选 `terms knowledge_base_id`，不得把过滤放入 `should`。章节导航 ES 查询使用同一 `KeywordIndexSearchRequest` 过滤；章节正文仓储在查询片段前调用 `KnowledgeBaseService.isDocumentInCurrentTenantScope(documentId, filter.knowledgeBaseIds())`，不能只凭导航返回的裸 `documentId` 读取。

- [ ] **Step 4: 运行过滤测试**

Run: `mvn -pl nexa-rag-retrieval -am -Dtest=SpringAiDocumentVectorStoreTest,ElasticsearchKeywordIndexClientTest,SectionExpansionRetrieverFixtureTest test`

Expected: PASS。

## Task 8: 将聊天 API 的可选知识库集合传入工作流与检索服务

**Files:**
- Modify: `nexa-rag-boot/src/main/java/com/nexarag/boot/controller/ChatStreamRequest.java`
- Modify: `nexa-rag-boot/src/main/java/com/nexarag/boot/controller/ChatController.java`
- Modify: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/request/ChatWorkflowRequest.java`
- Modify: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/constants/ChatWorkflowStateKeys.java`
- Modify: `nexa-rag-workflow/src/main/java/com/nexarag/workflow/node/chat/RetrievalNode.java`
- Modify: `nexa-rag-retrieval/src/main/java/com/nexarag/retrieval/service/impl/ConversationRetrievalServiceImpl.java`
- Test: `nexa-rag-boot/src/test/java/com/nexarag/boot/controller/ChatControllerTest.java`
- Test: `nexa-rag-workflow/src/test/java/com/nexarag/workflow/node/chat/RetrievalNodeTest.java`
- Test: `nexa-rag-retrieval/src/test/java/com/nexarag/retrieval/chat/impl/ConversationRetrievalServiceImplTest.java`

- [ ] **Step 1: 编写聊天范围传递失败测试**

```java
ChatStreamRequest request = new ChatStreamRequest("c-1", "退款规则", List.of(22L, 23L));
assertThat(workflowRequest.toInitialState().get(RETRIEVAL_KNOWLEDGE_BASE_IDS))
        .isEqualTo(List.of(22L, 23L));
assertThat(workflowRequest.toInitialState().get(RETRIEVAL_TENANT_ID)).isEqualTo("default-tenant");
assertThat(capturedRetrievalRequest.scopeFilter().knowledgeBaseIds()).containsExactlyInAnyOrder(22L, 23L);
```

另加空集合用例：`List.of()` 与 `null` 都传入空集合，随后由范围校验服务解释为当前租户全部知识库。

- [ ] **Step 2: 运行聊天范围测试，确认失败**

Run: `mvn -pl nexa-rag-boot,nexa-rag-workflow,nexa-rag-retrieval -am -Dtest=ChatControllerTest,RetrievalNodeTest,ConversationRetrievalServiceImplTest test`

Expected: FAIL，因为聊天 DTO、工作流状态和检索请求尚未携带知识库集合。

- [ ] **Step 3: 实现范围校验和状态透传**

将 `knowledgeBaseIds` 和 `tenantId` 加入 `ChatWorkflowRequest`，新增状态键 `RETRIEVAL_KNOWLEDGE_BASE_IDS`、`RETRIEVAL_TENANT_ID`。`ChatController` 调用 `KnowledgeBaseService.validateRequestedKnowledgeBases`：空集合保持为空，表示当前租户全部可访问知识库；非空集合必须全部属于当前租户，否则失败。控制器同时从 `CurrentTenantProvider` 获取租户标识并传入工作流。`RetrievalNode` 使用两个状态键构造 `RetrievalScopeFilter` 并放入 `ConversationRetrievalRequest`，所有检索通道只使用此对象，不重新读取 HTTP 请求。

- [ ] **Step 4: 运行聊天范围测试**

Run: `mvn -pl nexa-rag-boot,nexa-rag-workflow,nexa-rag-retrieval -am -Dtest=ChatControllerTest,RetrievalNodeTest,ConversationRetrievalServiceImplTest test`

Expected: PASS。

## Task 9: 执行模块级回归与接口、文档同步

**Files:**
- Modify: `README.md` 或现有 API 文档（仅在仓库已有对应文档入口时）
- Modify: `nexa-rag-front` 的调用说明或 API 客户端（仅在本任务授权包含前端联调时；否则记录为后续调用方迁移项）
- Verify: `docs/superpowers/specs/2026-08-18-tenant-scoped-knowledge-base-design.md`

- [ ] **Step 1: 更新已存在的接口说明**

将旧 `/api/documents` 文档替换为知识库管理及嵌套文档路径；明确旧端点不兼容、固定单租户过渡、空环境前提和 `knowledgeBaseIds` 的“空集合为全部”语义。不要新增未实现的 Sa-Token 角色或共享接口。

- [ ] **Step 2: 运行模块级测试**

Run: `mvn -pl nexa-rag-document,nexa-rag-retrieval,nexa-rag-workflow,nexa-rag-boot -am test`

Expected: PASS。

- [ ] **Step 3: 运行静态和差异检查**

Run: `git diff --check`

Expected: 当前任务涉及文件无空白错误；工作区已有的无关前端格式问题不在本任务中修改。

- [ ] **Step 4: 人工验收空环境闭环**

在用户已完成数据库、ES、Milvus 清空的测试环境中：启动应用，确认默认知识库存在且不可改删；创建普通知识库；上传文档并等待 `INDEXED`；验证五项统计；分别以默认全范围和指定库范围发起聊天检索，确认召回不跨库。

- [ ] **Step 5: 提交前复核（仅在用户明确要求提交时）**

检查变更仅包含本计划的 Java、SQL、测试与文档；运行 `git status --short`、`git diff`、`git diff --check`。未经用户明确要求，不执行 `git add`、`git commit`、`git push`。

## 计划自检

- 规格覆盖：Task 1–5 覆盖知识库表、租户、默认库、统计、嵌套 API 和文档生命周期；Task 6–8 覆盖三类索引元数据、查询阶段过滤和聊天多选范围；Task 9 覆盖空环境验收与文档同步。
- 空环境边界：Task 1 不包含数据清理、历史回填或索引重建；Task 9 将外部数据清空明确为用户执行的环境前提。
- 类型一致性：`CurrentTenantProvider`、`KnowledgeBaseService`、`DocumentIndexScope`、`RetrievalScopeFilter`、`knowledgeBaseIds` 在后续任务中使用同一命名与职责。
