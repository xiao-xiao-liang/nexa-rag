package com.nexarag.retrieval.index.keyword;

import com.nexarag.common.exception.ServiceException;
import com.nexarag.retrieval.config.RetrievalProperties;
import com.nexarag.retrieval.dto.req.KeywordIndexSearchRequest;
import com.nexarag.retrieval.dto.req.KeywordIndexWriteRequest;
import com.nexarag.retrieval.model.KeywordIndexDocument;
import com.nexarag.retrieval.model.KeywordIndexSearchResult;
import com.nexarag.retrieval.model.KeywordIndexWriteResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.ByQueryResponse;
import org.springframework.data.elasticsearch.core.query.DeleteQuery;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.nexarag.retrieval.constants.DocumentIndexFieldConstants.DOCUMENT_ID;
import static com.nexarag.retrieval.constants.DocumentIndexFieldConstants.INDEX_CONTENT;
import static com.nexarag.retrieval.constants.DocumentIndexFieldConstants.TEXT;

/**
 * 基于 Spring Data Elasticsearch 的关键词索引客户端。
 *
 * <p>该客户端只承担 BM25 索引读写；召回通道排序与 RRF 融合仍由既有检索节点负责。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "nexa.retrieval.keyword", name = "type", havingValue = "elasticsearch")
public class ElasticsearchKeywordIndexClient implements KeywordIndexClient {

    private final RetrievalProperties retrievalProperties;
    private final ElasticsearchOperations elasticsearchOperations;

    /**
     * 批量写入或更新 Elasticsearch 关键词索引。
     *
     * @param request 关键词索引写入请求
     * @return 写入结果列表
     */
    @Override
    public List<KeywordIndexWriteResult> upsert(KeywordIndexWriteRequest request) {
        if (request == null || request.documents() == null || request.documents().isEmpty()) {
            return List.of();
        }

        // 1. 按运行时索引名创建或补齐映射，支持正文和章节导航物理隔离。
        String indexName = resolveIndexName(request.indexName());
        IndexCoordinates indexCoordinates = IndexCoordinates.of(indexName);
        ensureIndex(indexCoordinates);

        // 2. 由 Spring Data 统一完成批量 upsert 和对象字段转换。
        List<KeywordIndexDocumentDO> documents = request.documents().stream()
                .map(KeywordIndexDocumentDO::from)
                .toList();
        elasticsearchOperations.save(documents, indexCoordinates);

        // 3. 保持数据库回写使用的稳定关键词索引ID不变。
        return request.documents().stream()
                .map(document -> new KeywordIndexWriteResult(document.chunkId(),
                        keywordIndexId(indexName, document.chunkId()), true, null))
                .toList();
    }

    /**
     * 使用 Elasticsearch BM25 检索片段。
     *
     * @param request 关键词检索请求
     * @return 按相关性排序的片段结果
     */
    @Override
    public List<KeywordIndexSearchResult> search(KeywordIndexSearchRequest request) {
        if (request == null || !StringUtils.hasText(request.query()) || request.topK() <= 0) {
            return List.of();
        }

        // 1. 同时检索新索引字段和历史正文，兼容未完成重建的旧文档。
        NativeQuery query = buildSearchQuery(request.query(), request.topK());
        String indexName = resolveIndexName(request.indexName());
        List<SearchHit<KeywordIndexDocumentDO>> searchHits = elasticsearchOperations
                .search(query, KeywordIndexDocumentDO.class, IndexCoordinates.of(indexName))
                .getSearchHits();

        // 2. 保留 Elasticsearch 原始 BM25 分数，交由既有 RRF 节点融合。
        return searchHits.stream()
                .map(this::toSearchResult)
                .toList();
    }

    /**
     * 按文档ID删除默认 Elasticsearch 关键词索引中的记录。
     *
     * @param documentId 文档ID
     * @return 删除数量
     */
    @Override
    public int deleteByDocumentId(Long documentId) {
        return deleteByDocumentId(documentId, null);
    }

    /**
     * 按文档ID删除指定 Elasticsearch 索引中的记录。
     *
     * @param documentId 文档ID
     * @param indexName  索引名称，为空时使用默认正文索引
     * @return 删除数量
     */
    @Override
    public int deleteByDocumentId(Long documentId, String indexName) {
        if (documentId == null) {
            return 0;
        }

        // 1. 通过 Spring Data delete_by_query 清理指定文档的全部片段。
        String resolvedIndexName = resolveIndexName(indexName);
        NativeQuery query = NativeQuery.builder()
                .withQuery(queryBuilder -> queryBuilder.term(term -> term
                        .field(DOCUMENT_ID)
                        .value(documentId)))
                .build();
        ByQueryResponse response = elasticsearchOperations.delete(DeleteQuery.builder(query).build(),
                KeywordIndexDocumentDO.class, IndexCoordinates.of(resolvedIndexName));
        int deletedCount = Math.toIntExact(response.getDeleted());

        // 2. 记录真实删除数量，供异步索引清理任务排查。
        log.info("Elasticsearch 关键词索引清理完成，文档ID：{}，indexName={}，删除数量：{}", documentId, resolvedIndexName, deletedCount);
        return deletedCount;
    }

    private void ensureIndex(IndexCoordinates indexCoordinates) {
        IndexOperations targetIndexOperations = elasticsearchOperations.indexOps(indexCoordinates);
        Document mapping = elasticsearchOperations.indexOps(KeywordIndexDocumentDO.class).createMapping();
        if (!targetIndexOperations.exists()) {
            createIndex(indexCoordinates, targetIndexOperations, mapping);
            return;
        }

        // 1. 为已存在索引补齐字段映射，不改变历史文档数据。
        if (!targetIndexOperations.putMapping(mapping)) {
            throw new ServiceException("Elasticsearch 关键词索引映射更新失败，indexName="
                    + indexCoordinates.getIndexName());
        }
        log.info("Elasticsearch 关键词索引映射已确认，indexName={}", indexCoordinates.getIndexName());
    }

    private void createIndex(IndexCoordinates indexCoordinates, IndexOperations targetIndexOperations, Document mapping) {
        // 1. 索引不存在时使用实体映射创建，保证精确字段和全文字段类型稳定。
        if (!targetIndexOperations.create(java.util.Map.of(), mapping) && !targetIndexOperations.exists()) {
            throw new ServiceException("Elasticsearch 关键词索引创建失败，indexName="
                    + indexCoordinates.getIndexName());
        }
        log.info("Elasticsearch 关键词索引创建完成，indexName={}", indexCoordinates.getIndexName());
    }

    private NativeQuery buildSearchQuery(String queryText, int topK) {
        return NativeQuery.builder()
                .withQuery(queryBuilder -> queryBuilder.bool(bool -> bool
                        .should(should -> should.match(match -> match.field(INDEX_CONTENT).query(queryText)))
                        .should(should -> should.match(match -> match.field(TEXT).query(queryText)))
                        .minimumShouldMatch("1")))
                .withPageable(PageRequest.of(0, topK))
                .build();
    }

    private KeywordIndexSearchResult toSearchResult(SearchHit<KeywordIndexDocumentDO> searchHit) {
        KeywordIndexDocumentDO document = searchHit.getContent();
        return new KeywordIndexSearchResult(document.chunkId(), document.documentId(), document.parentChunkId(),
                document.chunkOrder(), document.sectionId(), document.text(), document.metadataJson(),
                searchHit.getScore());
    }

    private String resolveIndexName(String indexName) {
        return StringUtils.hasText(indexName) ? indexName : retrievalProperties.getKeyword().getIndexName();
    }

    private String keywordIndexId(String indexName, String chunkId) {
        return "elasticsearch:" + indexName + ":" + chunkId;
    }
}
