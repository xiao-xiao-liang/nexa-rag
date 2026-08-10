package com.nexarag.retrieval.index.keyword;

import com.nexarag.retrieval.config.RetrievalProperties;
import com.nexarag.retrieval.dto.req.KeywordIndexSearchRequest;
import com.nexarag.retrieval.dto.req.KeywordIndexWriteRequest;
import com.nexarag.retrieval.model.KeywordIndexDocument;
import com.nexarag.retrieval.model.KeywordIndexWriteResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.ByQueryResponse;
import org.springframework.data.elasticsearch.core.query.DeleteQuery;
import org.springframework.data.elasticsearch.core.query.Query;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Elasticsearch 关键词索引客户端测试，验证 Spring Data Elasticsearch 操作契约。
 */
@ExtendWith(MockitoExtension.class)
class ElasticsearchKeywordIndexClientTest {

    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    @Mock
    private IndexOperations indexOperations;

    @Mock
    private IndexOperations documentIndexOperations;

    @Mock
    private SearchHits<KeywordIndexDocumentDO> searchHits;

    @Mock
    private SearchHit<KeywordIndexDocumentDO> searchHit;

    @Mock
    private ByQueryResponse byQueryResponse;

    @Captor
    private ArgumentCaptor<IndexCoordinates> indexCoordinatesCaptor;

    @Captor
    private ArgumentCaptor<DeleteQuery> deleteQueryCaptor;

    @Captor
    private ArgumentCaptor<Query> searchQueryCaptor;

    @Captor
    private ArgumentCaptor<List<KeywordIndexDocumentDO>> indexedDocumentsCaptor;

    private ElasticsearchKeywordIndexClient client;

    @BeforeEach
    void setUp() {
        client = new ElasticsearchKeywordIndexClient(properties(), elasticsearchOperations);
    }

    @Test
    void upsertShouldCreateDynamicIndexAndPersistChunkDocuments() {
        when(elasticsearchOperations.indexOps(any(IndexCoordinates.class))).thenReturn(indexOperations);
        when(elasticsearchOperations.indexOps(KeywordIndexDocumentDO.class)).thenReturn(documentIndexOperations);
        when(documentIndexOperations.createMapping()).thenReturn(Document.create());
        when(indexOperations.exists()).thenReturn(false);
        when(indexOperations.create(any(), any(Document.class))).thenReturn(true);
        KeywordIndexWriteRequest request = new KeywordIndexWriteRequest("nexa_document_chunk", 1L,
                List.of(new KeywordIndexDocument("chunk-1", 1L, null, 0, 11L,
                        "测试文本", "一级标题\n测试文本", "{\"source\":\"unit\"}")));

        List<KeywordIndexWriteResult> results = client.upsert(request);

        assertThat(results).containsExactly(new KeywordIndexWriteResult("chunk-1",
                "elasticsearch:nexa_document_chunk:chunk-1", true, null));
        verify(elasticsearchOperations).save(indexedDocumentsCaptor.capture(), indexCoordinatesCaptor.capture());
        assertThat(indexCoordinatesCaptor.getValue().getIndexName()).isEqualTo("nexa_document_chunk");
        assertThat(indexedDocumentsCaptor.getValue()).singleElement().satisfies(document -> {
            assertThat(document.id()).isEqualTo("chunk-1");
            assertThat(document.chunkId()).isEqualTo("chunk-1");
            assertThat(document.sectionId()).isEqualTo(11L);
            assertThat(document.indexContent()).isEqualTo("一级标题\n测试文本");
        });
    }

    @Test
    void searchShouldUseBothContentFieldsAndKeepBm25Score() {
        KeywordIndexDocumentDO content = new KeywordIndexDocumentDO("chunk-1", 1L, null, 0,
                11L, "测试文本", "一级标题\n测试文本", "{\"source\":\"unit\"}");
        when(searchHit.getContent()).thenReturn(content);
        when(searchHit.getScore()).thenReturn(3.25F);
        when(searchHits.getSearchHits()).thenReturn(List.of(searchHit));
        when(elasticsearchOperations.search(any(Query.class), eq(KeywordIndexDocumentDO.class),
                any(IndexCoordinates.class))).thenReturn(searchHits);

        var results = client.search(new KeywordIndexSearchRequest("nexa_document_chunk", "退款规则", 5));

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.chunkId()).isEqualTo("chunk-1");
            assertThat(result.sectionId()).isEqualTo(11L);
            assertThat(result.score()).isEqualTo(3.25D);
        });
        verify(elasticsearchOperations).search(searchQueryCaptor.capture(), eq(KeywordIndexDocumentDO.class),
                indexCoordinatesCaptor.capture());
        assertThat(searchQueryCaptor.getValue()).isInstanceOf(NativeQuery.class);
        NativeQuery nativeQuery = (NativeQuery) searchQueryCaptor.getValue();
        assertThat(nativeQuery.getQuery().isBool()).isTrue();
        assertThat(nativeQuery.getQuery().bool().minimumShouldMatch()).isEqualTo("1");
        assertThat(nativeQuery.getQuery().bool().should())
                .extracting(should -> should.match().field())
                .containsExactly("index_content", "text");
        assertThat(nativeQuery.getPageable().getPageSize()).isEqualTo(5);
        assertThat(indexCoordinatesCaptor.getValue().getIndexName()).isEqualTo("nexa_document_chunk");
    }

    @Test
    void deleteByDocumentIdShouldUseDeleteByQueryAndReturnDeletedCount() {
        when(byQueryResponse.getDeleted()).thenReturn(2L);
        when(elasticsearchOperations.delete(any(DeleteQuery.class), eq(KeywordIndexDocumentDO.class),
                any(IndexCoordinates.class))).thenReturn(byQueryResponse);

        int deletedCount = client.deleteByDocumentId(1L);

        assertThat(deletedCount).isEqualTo(2);
        verify(elasticsearchOperations).delete(deleteQueryCaptor.capture(), eq(KeywordIndexDocumentDO.class),
                indexCoordinatesCaptor.capture());
        assertThat(deleteQueryCaptor.getValue().getQuery()).isInstanceOf(NativeQuery.class);
        assertThat(indexCoordinatesCaptor.getValue().getIndexName()).isEqualTo("nexa_document_chunk");
    }

    private RetrievalProperties properties() {
        RetrievalProperties properties = new RetrievalProperties();
        properties.getKeyword().setIndexName("nexa_document_chunk");
        return properties;
    }
}
