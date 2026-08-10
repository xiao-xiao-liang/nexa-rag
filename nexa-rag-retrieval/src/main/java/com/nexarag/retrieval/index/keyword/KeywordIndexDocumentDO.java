package com.nexarag.retrieval.index.keyword;

import com.nexarag.retrieval.model.KeywordIndexDocument;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import static com.nexarag.retrieval.constants.DocumentIndexFieldConstants.CHUNK_ID;
import static com.nexarag.retrieval.constants.DocumentIndexFieldConstants.CHUNK_ORDER;
import static com.nexarag.retrieval.constants.DocumentIndexFieldConstants.DOCUMENT_ID;
import static com.nexarag.retrieval.constants.DocumentIndexFieldConstants.INDEX_CONTENT;
import static com.nexarag.retrieval.constants.DocumentIndexFieldConstants.METADATA_JSON;
import static com.nexarag.retrieval.constants.DocumentIndexFieldConstants.PARENT_CHUNK_ID;
import static com.nexarag.retrieval.constants.DocumentIndexFieldConstants.SECTION_ID;
import static com.nexarag.retrieval.constants.DocumentIndexFieldConstants.TEXT;

/**
 * Elasticsearch 关键词索引文档对象。
 *
 * <p>通过 {@link Id} 将 {@code chunkId} 固定为 Elasticsearch 文档ID，同时保留同名业务字段，
 * 以兼容已有索引映射和数据库索引状态。</p>
 */
@Document(indexName = "nexa_rag_keyword_chunk")
public record KeywordIndexDocumentDO(
        @Id String id,
        @Field(name = CHUNK_ID, type = FieldType.Keyword) String chunkId,
        @Field(name = DOCUMENT_ID, type = FieldType.Long) Long documentId,
        @Field(name = PARENT_CHUNK_ID, type = FieldType.Keyword) String parentChunkId,
        @Field(name = CHUNK_ORDER, type = FieldType.Integer) Integer chunkOrder,
        @Field(name = SECTION_ID, type = FieldType.Long) Long sectionId,
        @Field(name = TEXT, type = FieldType.Text) String text,
        @Field(name = INDEX_CONTENT, type = FieldType.Text) String indexContent,
        @Field(name = METADATA_JSON, type = FieldType.Keyword, index = false) String metadataJson) {

    /**
     * 根据模块内关键词文档构造 Elasticsearch 持久化对象。
     *
     * @param document 模块内关键词文档
     * @return Elasticsearch 关键词索引文档对象
     */
    public static KeywordIndexDocumentDO from(KeywordIndexDocument document) {
        return new KeywordIndexDocumentDO(document.chunkId(), document.chunkId(), document.documentId(),
                document.parentChunkId(), document.chunkOrder(), document.sectionId(), document.text(),
                document.indexContent(), document.metadataJson());
    }

    /**
     * 用于测试和检索结果构造，始终令 Elasticsearch 文档ID与业务片段ID一致。
     */
    public KeywordIndexDocumentDO(String chunkId, Long documentId, String parentChunkId, Integer chunkOrder,
                                  Long sectionId, String text, String indexContent, String metadataJson) {
        this(chunkId, chunkId, documentId, parentChunkId, chunkOrder, sectionId, text, indexContent, metadataJson);
    }
}
