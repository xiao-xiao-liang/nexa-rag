# 租户隔离知识库后端设计

**日期：** 2026-08-18  
**状态：** 已确认，待评审文档  
**范围：** 知识库领域模型、文档 API 迁移、检索范围隔离与空环境切换

## 1. 背景与目标

当前系统的 `document`、文档处理、分块和检索链路以全局文档池工作。文档没有知识库归属，文档接口和检索接口均无法按知识库区分数据。

本期引入租户范围内的知识库实体，使文档必须归属一个知识库，并在文档管理和 RAG 检索阶段强制应用知识库边界。首期采用固定单租户运行，后续由 Sa-Token `auth` 模块接入真实登录、租户与组织成员关系。

## 2. 范围

### 2.1 本期实现

- 新增知识库的创建、分页列表、详情、名称/描述更新和删除接口。
- 文档创建、上传、外部导入、列表、详情、处理、重试、状态、分块、概览和删除接口迁移为知识库嵌套路由。
- 知识库按租户归属，名称在同租户的未删除知识库中唯一。
- 知识库列表和详情返回总数、待处理、处理中、已索引、失败五项文档统计。
- RAG 检索默认覆盖当前租户全部知识库，并支持指定一个或多个知识库缩小范围。
- 向量、关键词和章节检索写入并在查询时过滤租户、知识库元数据。
- 以空数据环境切换；数据库、Elasticsearch 与 Milvus 的旧数据由用户在上线前清空。

### 2.2 明确不实现

- Sa-Token 登录鉴权、租户选择、组织成员、角色和共享权限管理。
- 文档在知识库之间移动、批量移动或复制。
- 知识库级解析、切分、向量或关键词索引默认配置。
- 历史文档回填、历史向量/关键词/章节索引重建，或应用启动时自动清空数据。
- 旧全局文档 API 的兼容层。

## 3. 领域模型

| 术语 | 标识与归属 | 核心约束 |
| --- | --- | --- |
| 租户 | `tenantId`；首期由服务端固定默认值提供 | 当前请求的租户不能由客户端任意指定；后续由 Sa-Token 替换提供方式。 |
| 知识库 | `knowledgeBaseId`；归属一个租户 | 同租户未删除名称唯一；每租户最多一个默认知识库。 |
| 默认知识库 | 一个租户中的受保护知识库 | 不可重命名、不可删除；不能由名称推断，必须持久化默认标记。 |
| 文档 | `documentId`；归属一个知识库 | 创建时确定唯一归属，生命周期内不可跨库移动。 |
| 文档处理配置快照 | 文档的 `processConfigJson` | 保持文档级，不受知识库创建或更新影响。 |
| 检索范围 | 一次请求的可访问知识库集合 | 未指定或空集合为当前租户全部可访问知识库；显式集合必须全部校验通过。 |

### 3.1 文档统计状态

统计仅聚合当前知识库的未删除文档：

| 统计项 | 包含状态 |
| --- | --- |
| 文档总数 | 所有未删除文档 |
| 待处理数 | `UPLOADED` |
| 处理中数 | 已进入处理流水线但尚未进入终态的状态 |
| 已索引数 | `INDEXED` |
| 失败数 | `FAILED` |

`UPLOADED` 只计入总数和待处理数，不计入处理中、已索引或失败。

## 4. 数据模型与空环境切换

### 4.1 知识库表

新增 `knowledge_base`，最小字段为：

- `knowledge_base_id`：主键。
- `tenant_id`：租户归属。
- `name`：显示名称。
- 有效名称键：仅对未删除记录参与租户内唯一约束，支持逻辑删除后重用同名知识库。
- `description`：可选描述。
- `is_default`：默认知识库标记。
- `create_time`、`update_time`、`create_by`、`update_by`、`del_flag`、`delete_time`、`version`：与现有实体一致的审计、逻辑删除和并发控制字段。

数据库必须保证租户内默认知识库至多一个，并保证租户内有效名称唯一。服务层预校验只改善错误提示；数据库约束是最终保证。

### 4.2 文档与索引字段

- `document` 增加非空 `knowledge_base_id`，并建立支持按库列表和状态统计的组合索引。
- `document_chunk`、章节、文档任务和 Outbox 继续通过 `document_id` 关联，不冗余保存知识库外键。
- 向量、关键词和章节索引记录写入 `tenantId`、`knowledgeBaseId` 元数据，以便在检索阶段过滤。

### 4.3 切换前提

用户在上线前自行清空数据库、Elasticsearch 和 Milvus 中的既有数据。本期实现不执行任何删除操作。

Flyway 迁移仅负责创建/演进表结构，并幂等初始化内建默认租户及其默认知识库。由于目标环境为空，不进行历史文档回填或历史索引重建；新文档从创建起即具备知识库和租户元数据。

## 5. 接口设计

### 5.1 知识库管理

```text
POST   /api/knowledge-bases
GET    /api/knowledge-bases?pageNum&pageSize
GET    /api/knowledge-bases/{knowledgeBaseId}
PATCH  /api/knowledge-bases/{knowledgeBaseId}
DELETE /api/knowledge-bases/{knowledgeBaseId}
```

列表和详情返回知识库基本信息、`isDefault` 和五项文档统计。创建及更新仅接受名称、描述等可变基础字段；默认库的重命名请求必须拒绝。

### 5.2 按知识库管理文档

```text
POST /api/knowledge-bases/{knowledgeBaseId}/documents
POST /api/knowledge-bases/{knowledgeBaseId}/documents/upload
POST /api/knowledge-bases/{knowledgeBaseId}/documents/external

GET    /api/knowledge-bases/{knowledgeBaseId}/documents
GET    /api/knowledge-bases/{knowledgeBaseId}/documents/{documentId}
GET    /api/knowledge-bases/{knowledgeBaseId}/documents/{documentId}/overview
DELETE /api/knowledge-bases/{knowledgeBaseId}/documents/{documentId}

POST /api/knowledge-bases/{knowledgeBaseId}/documents/{documentId}/process
POST /api/knowledge-bases/{knowledgeBaseId}/documents/{documentId}/retry
GET  /api/knowledge-bases/{knowledgeBaseId}/documents/{documentId}/process-status
GET  /api/knowledge-bases/{knowledgeBaseId}/documents/{documentId}/chunks
```

现有全局 `/api/documents` 接口全部移除，不保留隐式默认知识库兼容逻辑。文档请求体不得接受可写的 `knowledgeBaseId`；知识库归属由父资源路径确定且创建后不可变。

### 5.3 检索请求

聊天/检索请求增加可选 `knowledgeBaseIds`：

- 未提供或为空集合：检索当前租户全部可访问知识库。
- 提供一个或多个标识：检索范围缩小至该集合。
- 标识不存在、不属于当前租户或未来不具备访问权限：请求失败，不静默忽略，也不降级为全范围。

## 6. 运行时数据流

```text
当前租户提供者（首期固定默认租户）
  → 知识库服务：校验租户归属、默认库保护、名称唯一
  → 文档服务：校验目标知识库后创建、上传、处理和删除
  → 索引写入：写入 tenantId 与 knowledgeBaseId 元数据
  → 检索服务：解析可选 knowledgeBaseIds，校验后在检索阶段过滤
```

检索过滤必须在向量、关键词和章节检索查询阶段完成，不能从全局候选集中先召回再在应用层过滤。后续 Sa-Token 接入仅替换当前租户提供者及访问集合解析，不改变知识库、文档或索引归属模型。

## 7. 删除与错误语义

- 默认知识库不可重命名、不可删除。
- 普通知识库仅在没有未删除文档时允许删除；非空删除不级联触发文档、对象存储、分块或索引清理。
- 目标知识库不属于当前租户，或文档不属于路径知识库时，按资源不存在处理。
- 同租户名称冲突、默认库更新/删除、非空库删除均返回明确业务冲突。
- 文档不可移动；调用方如需在另一库使用同内容，首期重新上传或导入。

## 8. 测试与验收

### 8.1 自动化测试

- Flyway 空库初始化幂等创建表、默认租户和受保护默认知识库。
- 同租户名称冲突、逻辑删除后名称重用、跨租户同名及默认库唯一性。
- 嵌套路由的知识库—文档归属校验，且旧全局接口不可访问。
- 空库删除成功；非空库及默认库删除拒绝。
- 五项统计的状态映射，尤其 `UPLOADED` 仅属于待处理。
- 上传、外部导入、处理、重试、分块、概览和删除在嵌套路由下保持既有文档生命周期行为。
- 向量、关键词和章节索引写入租户/知识库元数据；默认全范围及多选范围检索在查询阶段完成过滤。

### 8.2 验收标准

1. 空环境启动后存在受保护默认知识库，用户可新建并管理普通知识库。
2. 上传或导入的文档只能归属路径指定的知识库，且无法移动到另一知识库。
3. 各知识库的文档列表、五项统计和检索结果不混入其他知识库数据。
4. 不指定范围可检索当前租户全部知识库；指定一个或多个知识库可严格缩小范围。
5. 默认知识库不可重命名或删除；普通空库可删除，非空库被拒绝。
6. 全局文档 API 不再可用；所有文档操作需通过知识库父路径访问。
