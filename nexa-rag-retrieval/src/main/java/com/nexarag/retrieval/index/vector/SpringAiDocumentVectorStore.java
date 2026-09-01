package com.nexarag.retrieval.index.vector;

import com.nexarag.retrieval.config.RetrievalProperties;
import com.nexarag.retrieval.model.IndexableChunk;
import com.nexarag.retrieval.model.VectorIndexSearchResult;
import com.nexarag.retrieval.model.VectorIndexWriteResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.nexarag.retrieval.constants.SpringAiVectorStoreMetadataConstants.*;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.*;

/**
 * 基于 Spring AI VectorStore 的文档片段向量存储实现。
 *
 * <p>该实现只接收业务片段和查询文本，Embedding 由底层 VectorStore 经
 * ModelGatewayEmbeddingModel 统一调用模型网关生成。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "nexa.retrieval.vector", name = "type", havingValue = "milvus")
public class SpringAiDocumentVectorStore implements DocumentVectorStore {

    private final VectorStore vectorStore;
    private final RetrievalProperties retrievalProperties;

    /**
     * 删除指定文档版本已有向量后，写入该版本的全部待索引片段。
     *
     * @param documentId        文档ID
     * @param documentVersionId 文档版本ID
     * @param chunks            当前待索引片段
     * @return 写入成功的片段结果
     */
    @Override
    public List<VectorIndexWriteResult> replaceDocumentVersion(Long documentId, Long documentVersionId,
                                                               List<IndexableChunk> chunks) {
        // 1. 校验版本边界，禁止同一文档的历史版本互相覆盖
        validateDocumentVersionChunks(documentId, documentVersionId, chunks);

        // 2. 仅清理当前版本，再复用统一批量写入逻辑
        deleteByDocumentVersionId(documentId, documentVersionId);
        return writeChunks(chunks);
    }

    /**
     * 按文本查询并恢复业务检索结果。
     *
     * @param query 查询文本
     * @param topK  最大候选数量
     * @return 业务片段检索结果
     */
    @Override
    public List<VectorIndexSearchResult> search(String query, int topK) {
        if (!StringUtils.hasText(query) || topK <= 0) {
            return List.of();
        }

        // 1. 委托 VectorStore 执行向量化和相似度检索
        List<Document> documents = vectorStore.similaritySearch(SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThresholdAll()
                .build());
        if (documents.isEmpty()) {
            return List.of();
        }

        // 2. 从 metadata 恢复业务字段，异常记录不参与后续 RAG 候选链路
        List<VectorIndexSearchResult> results = new ArrayList<>();
        for (Document document : documents) {
            toSearchResult(document).ifPresent(results::add);
        }
        return results;
    }

    @Override
    public List<VectorIndexSearchResult> search(String query, int topK, java.util.Set<Long> activeVersionIds) {
        if (!StringUtils.hasText(query) || topK <= 0 || activeVersionIds == null || activeVersionIds.isEmpty())
            return List.of();
        List<Document> documents = vectorStore.similaritySearch(SearchRequest.builder().query(query).topK(topK)
                .similarityThresholdAll().filterExpression(new Filter.Expression(IN, new Filter.Key(DOCUMENT_VERSION_ID),
                        new Filter.Value(activeVersionIds))).build());
        return documents.stream().map(this::toSearchResult).flatMap(java.util.Optional::stream).toList();
    }

    /**
     * 按文档和版本ID删除向量记录。
     *
     * @param documentId        文档ID
     * @param documentVersionId 文档版本ID
     */
    @Override
    public void deleteByDocumentVersionId(Long documentId, Long documentVersionId) {
        if (documentId == null || documentVersionId == null) {
            return;
        }
        Filter.Expression documentFilter = new Filter.Expression(EQ, new Filter.Key(DOCUMENT_ID),
                new Filter.Value(documentId));
        Filter.Expression versionFilter = new Filter.Expression(EQ, new Filter.Key(DOCUMENT_VERSION_ID),
                new Filter.Value(documentVersionId));
        vectorStore.delete(new Filter.Expression(AND, documentFilter, versionFilter));
    }

    private void validateDocumentChunks(Long documentId, List<IndexableChunk> chunks) {
        if (documentId == null) {
            throw new IllegalArgumentException("文档ID不能为空");
        }
        if (chunks == null) {
            throw new IllegalArgumentException("待索引片段不能为空");
        }
        boolean inconsistentChunk = chunks.stream()
                .anyMatch(chunk -> chunk == null || !documentId.equals(chunk.documentId()));
        if (inconsistentChunk) {
            throw new IllegalArgumentException("待索引片段与文档ID不一致，documentId=" + documentId);
        }
    }

    private void validateDocumentVersionChunks(Long documentId, Long documentVersionId, List<IndexableChunk> chunks) {
        validateDocumentChunks(documentId, chunks);
        if (documentVersionId == null) {
            throw new IllegalArgumentException("文档版本ID不能为空");
        }
        boolean inconsistentVersion = chunks.stream()
                .anyMatch(chunk -> !documentVersionId.equals(chunk.documentVersionId()));
        if (inconsistentVersion) {
            throw new IllegalArgumentException("待索引片段与文档版本ID不一致，documentVersionId=" + documentVersionId);
        }
    }

    private List<VectorIndexWriteResult> writeChunks(List<IndexableChunk> chunks) {
        if (chunks.isEmpty()) {
            return List.of();
        }

        // 1. 按模型批量限制写入，任一批失败由异常向上交给既有任务重试
        int batchSize = retrievalProperties.getEmbedding().getMaxBatchSize();
        if (batchSize <= 0) {
            throw new IllegalStateException("nexa.retrieval.embedding.max-batch-size必须大于0");
        }
        for (int start = 0; start < chunks.size(); start += batchSize) {
            int end = Math.min(start + batchSize, chunks.size());
            vectorStore.add(chunks.subList(start, end).stream().map(this::toDocument).toList());
        }
        return chunks.stream()
                .map(chunk -> new VectorIndexWriteResult(chunk.chunkId(), chunk.chunkId(), true, null))
                .toList();
    }

    private Document toDocument(IndexableChunk chunk) {
        if (!StringUtils.hasText(chunk.chunkId()) || !StringUtils.hasText(chunk.indexContent())) {
            throw new IllegalArgumentException("向量索引片段缺少chunkId或indexContent");
        }
        Map<String, Object> metadata = new HashMap<>(8);
        metadata.put(DOCUMENT_ID, chunk.documentId());
        putIfNotNull(metadata, DOCUMENT_VERSION_ID, chunk.documentVersionId());
        putIfNotNull(metadata, PARENT_CHUNK_ID, chunk.parentChunkId());
        putIfNotNull(metadata, CHUNK_ORDER, chunk.chunkOrder());
        putIfNotNull(metadata, SECTION_ID, chunk.sectionId());
        putIfNotNull(metadata, TEXT, chunk.text());
        putIfNotNull(metadata, METADATA_JSON, chunk.metadataJson());
        return new Document(chunk.chunkId(), chunk.indexContent(), metadata);
    }

    private java.util.Optional<VectorIndexSearchResult> toSearchResult(Document document) {
        if (document == null || !StringUtils.hasText(document.getId())) {
            log.warn("Spring AI向量检索结果缺少Document.id，已跳过");
            return java.util.Optional.empty();
        }
        try {
            Map<String, Object> metadata = document.getMetadata();
            Long documentId = requiredLong(metadata);
            Long documentVersionId = optionalLong(metadata, DOCUMENT_VERSION_ID);
            Integer chunkOrder = optionalInteger(metadata);
            Long sectionId = optionalLong(metadata, SECTION_ID);
            String text = optionalString(metadata, TEXT, document.getText());
            return java.util.Optional.of(new VectorIndexSearchResult(document.getId(), documentId, documentVersionId,
                    optionalString(metadata, PARENT_CHUNK_ID, null), chunkOrder,
                    sectionId, text, optionalString(metadata, METADATA_JSON, null),
                    document.getScore() == null ? 0D : document.getScore()));
        } catch (IllegalArgumentException exception) {
            log.warn("Spring AI向量检索结果元数据不完整，documentId={}，已跳过", document.getId());
            return java.util.Optional.empty();
        }
    }

    private void putIfNotNull(Map<String, Object> metadata, String key, Object value) {
        if (value != null) {
            metadata.put(key, value);
        }
    }

    private Long requiredLong(Map<String, Object> metadata) {
        Long value = optionalLong(metadata, DOCUMENT_ID);
        if (value == null) {
            throw new IllegalArgumentException("缺少元数据字段" + DOCUMENT_ID);
        }
        return value;
    }

    private Long optionalLong(Map<String, Object> metadata, String key) {
        Object value = metadata == null ? null : metadata.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return Long.valueOf(text);
        }
        return null;
    }

    private Integer optionalInteger(Map<String, Object> metadata) {
        Object value = metadata == null ? null : metadata.get(CHUNK_ORDER);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return Integer.valueOf(text);
        }
        return null;
    }

    private String optionalString(Map<String, Object> metadata, String key, String defaultValue) {
        Object value = metadata == null ? null : metadata.get(key);
        return value instanceof String text ? text : defaultValue;
    }
}
