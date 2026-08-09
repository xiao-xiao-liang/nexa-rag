# Spring AI MilvusVectorStore 切换说明

## 前置条件

- 当前 Embedding 模型的向量维度为 `1024`。
- 新产生的数据库 `chunkId` 为标准 UUID，并与 Spring AI `Document.id` 相同。
- 历史文档块和旧 Milvus collection 数据不保留。

## 切换步骤

1. 停止文档索引写入任务。
2. 由操作者清理关系库中的历史文档块数据。
3. 由操作者核对 Milvus 数据库和 collection 名称后，删除旧 `nexa_document_chunk` collection。
4. 部署包含 Spring AI MilvusVectorStore 的版本并启动应用。
5. 导入新文档，确认其生成的 `chunkId` 为 UUID；索引和查询均通过 `ModelGatewayEmbeddingModel` 调用 ModelGateway。

## 注意事项

- 清理关系库数据和删除 Milvus collection 都是破坏性操作，应用代码不会自动执行。
- 新 collection 由 Spring AI 自动初始化，schema 为 `doc_id`、`content`、`metadata`、`embedding`。
- 旧 collection schema 不可复用；未完成删除时禁止启动新版本的向量索引写入。
