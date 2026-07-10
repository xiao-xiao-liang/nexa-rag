package com.nexarag.retrieval.index.vector;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.retrieval.config.RetrievalProperties;
import com.nexarag.retrieval.constants.DocumentIndexFieldConstants;
import com.nexarag.retrieval.dto.VectorIndexWriteRequest;
import com.nexarag.retrieval.model.VectorIndexDocument;
import com.nexarag.retrieval.model.VectorIndexWriteResult;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.database.request.CreateDatabaseReq;
import io.milvus.v2.service.database.response.ListDatabasesResp;
import io.milvus.v2.service.utility.request.FlushReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.response.DeleteResp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.nexarag.retrieval.constants.MilvusIndexConstants.*;

/**
 * Milvus 向量索引客户端，负责创建文档片段集合、写入向量索引并按文档清理向量数据。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "nexa.retrieval.vector", name = "type", havingValue = "milvus")
public class MilvusVectorIndexClient implements VectorIndexClient, DisposableBean {

    private final RetrievalProperties.Vector vectorProperties;
    private final MilvusClientV2 milvusClient;

    public MilvusVectorIndexClient(RetrievalProperties retrievalProperties) {
        this.vectorProperties = retrievalProperties.getVector();
        ensureDatabase();
        this.milvusClient = new MilvusClientV2(connectConfig());
    }

    /**
     * 批量写入或更新 Milvus 向量索引。
     *
     * @param request 向量索引写入请求
     * @return 写入结果列表
     */
    @Override
    public List<VectorIndexWriteResult> upsert(VectorIndexWriteRequest request) {
        if (request == null || request.documents() == null || request.documents().isEmpty()) {
            return List.of();
        }

        // 1. 根据首个有效向量确认集合维度，并在集合不存在时创建集合
        String collectionName = resolveCollectionName(request.collectionName());
        int vectorDimension = resolveVectorDimension(request.documents());
        ensureCollection(collectionName, vectorDimension);

        // 2. 转换为 Milvus 写入格式，不记录片段全文和向量内容
        List<JsonObject> rows = request.documents().stream()
                .map(this::toMilvusRow)
                .toList();
        milvusClient.upsert(upsertReq(collectionName, rows));
        flush(collectionName);

        // 3. 返回稳定向量ID，便于数据库回写和后续定位
        log.info("Milvus 向量索引写入完成，documentId={}，collectionName={}，chunkCount={}",
                request.documentId(), collectionName, request.documents().size());
        return request.documents().stream()
                .map(document -> new VectorIndexWriteResult(document.chunkId(), vectorId(collectionName, document.chunkId()),
                        true, null))
                .toList();
    }

    /**
     * 按文档ID删除 Milvus 向量索引。
     *
     * @param documentId 文档ID
     * @return 删除数量
     */
    @Override
    public int deleteByDocumentId(Long documentId) {
        String collectionName = resolveCollectionName(null);
        if (documentId == null || !hasCollection(collectionName)) {
            return 0;
        }

        // 1. 使用稳定文档ID过滤条件清理该文档的所有向量记录
        DeleteResp response = milvusClient.delete(deleteReq(collectionName, documentId));
        long deletedCount = response == null ? 0 : response.getDeleteCnt();
        log.info("Milvus 向量索引清理完成，documentId={}，collectionName={}，deletedCount={}",
                documentId, collectionName, deletedCount);
        return Math.toIntExact(Math.min(deletedCount, Integer.MAX_VALUE));
    }

    /**
     * 关闭 Milvus 客户端连接。
     */
    @Override
    public void destroy() {
        milvusClient.close();
    }

    private ConnectConfig connectConfig() {
        return connectConfig(databaseNameOrNull());
    }

    private ConnectConfig connectConfig(String databaseName) {
        ConnectConfig.ConnectConfigBuilder builder = ConnectConfig.builder()
                .uri("http://" + vectorProperties.getHost() + ":" + vectorProperties.getPort())
                .rpcDeadlineMs(vectorProperties.getRpcDeadlineMs());
        if (StringUtils.hasText(databaseName)) {
            builder.dbName(databaseName);
        }
        if (StringUtils.hasText(vectorProperties.getUsername())) {
            builder.username(vectorProperties.getUsername());
        }
        if (StringUtils.hasText(vectorProperties.getPassword())) {
            builder.password(vectorProperties.getPassword());
        }
        return builder.build();
    }

    private void ensureDatabase() {
        String databaseName = databaseNameOrNull();
        if (databaseName == null) {
            return;
        }
        validateDatabaseName(databaseName);

        // 1. 使用默认库连接检查目标数据库，避免目标库不存在时正式连接失败
        MilvusClientV2 bootstrapClient = new MilvusClientV2(connectConfig(null));
        try {
            ListDatabasesResp response = bootstrapClient.listDatabases();
            if (response != null && response.getDatabaseNames().contains(databaseName)) {
                return;
            }

            // 2. 目标数据库不存在时自动创建，后续集合初始化仍由 ensureCollection 负责
            bootstrapClient.createDatabase(CreateDatabaseReq.builder()
                    .databaseName(databaseName)
                    .build());
            log.info("Milvus 数据库自动创建完成，databaseName={}", databaseName);
        } finally {
            bootstrapClient.close();
        }
    }

    private void validateDatabaseName(String databaseName) {
        if (!databaseName.matches(DATABASE_NAME_REGEX)) {
            throw new ServiceException("Milvus 数据库名称不合法，仅允许数字、字母和下划线，databaseName=" + databaseName);
        }
    }

    private synchronized void ensureCollection(String collectionName, int vectorDimension) {
        if (hasCollection(collectionName)) {
            return;
        }

        // 1. 首次写入时按真实向量维度创建文档片段集合
        CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder()
                .enableDynamicField(false)
                .fieldSchemaList(List.of(
                        varcharField(DocumentIndexFieldConstants.CHUNK_ID, CHUNK_ID_MAX_LENGTH, false, true),
                        field(DocumentIndexFieldConstants.DOCUMENT_ID, DataType.Int64),
                        varcharField(DocumentIndexFieldConstants.PARENT_CHUNK_ID, CHUNK_ID_MAX_LENGTH, true, false),
                        field(DocumentIndexFieldConstants.CHUNK_ORDER, DataType.Int32),
                        varcharField(DocumentIndexFieldConstants.TEXT, TEXT_MAX_LENGTH, false, false),
                        varcharField(DocumentIndexFieldConstants.METADATA_JSON, METADATA_MAX_LENGTH, true, false),
                        CreateCollectionReq.FieldSchema.builder()
                                .name(VECTOR)
                                .dataType(DataType.FloatVector)
                                .dimension(vectorDimension)
                                .build()))
                .build();
        IndexParam vectorIndex = IndexParam.builder()
                .fieldName(VECTOR)
                .indexName("idx_vector")
                .indexType(IndexParam.IndexType.AUTOINDEX)
                .metricType(IndexParam.MetricType.COSINE)
                .build();
        milvusClient.createCollection(CreateCollectionReq.builder()
                .collectionName(collectionName)
                .databaseName(databaseNameOrNull())
                .description("NexaRAG 文档片段向量集合")
                .collectionSchema(schema)
                .indexParams(List.of(vectorIndex))
                .build());
        milvusClient.loadCollection(LoadCollectionReq.builder()
                .collectionName(collectionName)
                .databaseName(databaseNameOrNull())
                .sync(true)
                .build());
        log.info("Milvus 文档片段集合创建完成，collectionName={}，dimension={}", collectionName, vectorDimension);
    }

    private boolean hasCollection(String collectionName) {
        return Boolean.TRUE.equals(milvusClient.hasCollection(HasCollectionReq.builder()
                .collectionName(collectionName)
                .databaseName(databaseNameOrNull())
                .build()));
    }

    private CreateCollectionReq.FieldSchema field(String name, DataType dataType) {
        return CreateCollectionReq.FieldSchema.builder()
                .name(name)
                .dataType(dataType)
                .build();
    }

    private CreateCollectionReq.FieldSchema varcharField(String name, int maxLength, boolean nullable, boolean primaryKey) {
        return CreateCollectionReq.FieldSchema.builder()
                .name(name)
                .dataType(DataType.VarChar)
                .maxLength(maxLength)
                .isNullable(nullable)
                .isPrimaryKey(primaryKey)
                .autoID(false)
                .build();
    }

    private UpsertReq upsertReq(String collectionName, List<JsonObject> rows) {
        return UpsertReq.builder()
                .collectionName(collectionName)
                .databaseName(databaseNameOrNull())
                .data(rows)
                .build();
    }

    private DeleteReq deleteReq(String collectionName, Long documentId) {
        return DeleteReq.builder()
                .collectionName(collectionName)
                .databaseName(databaseNameOrNull())
                .filter(DocumentIndexFieldConstants.DOCUMENT_ID + " == " + documentId)
                .build();
    }

    private void flush(String collectionName) {
        milvusClient.flush(FlushReq.builder()
                .collectionNames(List.of(collectionName))
                .databaseName(databaseNameOrNull())
                .build());
    }

    private JsonObject toMilvusRow(VectorIndexDocument document) {
        if (document.vector() == null || document.vector().length == 0) {
            throw new ServiceException("Milvus 向量写入失败，片段向量为空，chunkId=" + document.chunkId());
        }
        JsonObject row = new JsonObject();
        row.addProperty(DocumentIndexFieldConstants.CHUNK_ID, document.chunkId());
        row.addProperty(DocumentIndexFieldConstants.DOCUMENT_ID, document.documentId());
        addNullableString(row, DocumentIndexFieldConstants.PARENT_CHUNK_ID, document.parentChunkId(), CHUNK_ID_MAX_LENGTH);
        row.addProperty(DocumentIndexFieldConstants.CHUNK_ORDER, document.chunkOrder());
        row.addProperty(DocumentIndexFieldConstants.TEXT, truncate(document.text(), TEXT_MAX_LENGTH));
        addNullableString(row, DocumentIndexFieldConstants.METADATA_JSON, document.metadataJson(), METADATA_MAX_LENGTH);
        row.add(VECTOR, toJsonArray(document.vector()));
        return row;
    }

    private void addNullableString(JsonObject row, String fieldName, String value, int maxLength) {
        if (StringUtils.hasText(value)) {
            row.addProperty(fieldName, truncate(value, maxLength));
            return;
        }
        row.add(fieldName, JsonNull.INSTANCE);
    }

    private JsonArray toJsonArray(float[] vector) {
        JsonArray array = new JsonArray(vector.length);
        for (float value : vector) {
            array.add(value);
        }
        return array;
    }

    private int resolveVectorDimension(List<VectorIndexDocument> documents) {
        int configuredDimension = vectorProperties.getDimension();
        if (configuredDimension > 0) {
            return configuredDimension;
        }
        return documents.stream()
                .map(VectorIndexDocument::vector)
                .filter(vector -> vector != null && vector.length > 0)
                .findFirst()
                .map(vector -> vector.length)
                .orElseThrow(() -> new ServiceException("Milvus 集合创建失败，缺少有效向量维度"));
    }

    private String resolveCollectionName(String collectionName) {
        if (StringUtils.hasText(collectionName)) {
            return collectionName;
        }
        return vectorProperties.getCollectionName();
    }

    private String databaseNameOrNull() {
        return StringUtils.hasText(vectorProperties.getDatabaseName()) ? vectorProperties.getDatabaseName() : null;
    }

    private String vectorId(String collectionName, String chunkId) {
        return "milvus:" + collectionName + ":" + chunkId;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
