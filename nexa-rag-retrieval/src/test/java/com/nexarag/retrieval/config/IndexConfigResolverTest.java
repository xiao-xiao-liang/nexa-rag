package com.nexarag.retrieval.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.model.dto.IndexConfigRequest;
import com.nexarag.document.model.dto.ProcessDocumentRequest;
import com.nexarag.document.model.entity.DocumentVersionDO;
import com.nexarag.retrieval.model.IndexConfigSnapshot;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 索引配置解析器测试。
 */
class IndexConfigResolverTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final IndexConfigResolver resolver = new IndexConfigResolver(objectMapper, new RetrievalProperties());

    @Test
    void resolveShouldUseDefaultConfigWhenProcessConfigIsBlank() {
        DocumentVersionDO documentVersion = DocumentVersionDO.builder().documentVersionId(1L).build();

        IndexConfigSnapshot snapshot = resolver.resolve(documentVersion);

        assertThat(snapshot.enabled()).isTrue();
        assertThat(snapshot.vectorEnabled()).isTrue();
        assertThat(snapshot.keywordEnabled()).isFalse();
        assertThat(snapshot.embeddingRouteKey()).isNull();
        assertThat(snapshot.vectorCollection()).isEqualTo("nexa_document_chunk");
        assertThat(snapshot.keywordIndexName()).isEqualTo("nexa_document_chunk");
    }

    @Test
    void resolveShouldDisableKeywordIndexWhenKeywordTypeIsNone() throws Exception {
        RetrievalProperties properties = new RetrievalProperties();
        properties.getKeyword().setType("none");
        IndexConfigResolver noneKeywordResolver = new IndexConfigResolver(objectMapper, properties);
        ProcessDocumentRequest request = new ProcessDocumentRequest(null, null,
                new IndexConfigRequest(true, true, true));
        DocumentVersionDO documentVersion = DocumentVersionDO.builder()
                .documentVersionId(1L)
                .processConfigJson(objectMapper.writeValueAsString(request))
                .build();

        IndexConfigSnapshot snapshot = noneKeywordResolver.resolve(documentVersion);

        assertThat(snapshot.enabled()).isTrue();
        assertThat(snapshot.vectorEnabled()).isTrue();
        assertThat(snapshot.keywordEnabled()).isFalse();
    }

    @Test
    void resolveShouldReadIndexConfigFromProcessConfigJson() throws Exception {
        RetrievalProperties properties = new RetrievalProperties();
        properties.getKeyword().setType("elasticsearch");
        IndexConfigResolver elasticsearchKeywordResolver = new IndexConfigResolver(objectMapper, properties);
        ProcessDocumentRequest request = new ProcessDocumentRequest(null, null,
                new IndexConfigRequest(false, false, true));
        DocumentVersionDO documentVersion = DocumentVersionDO.builder()
                .documentVersionId(1L)
                .processConfigJson(objectMapper.writeValueAsString(request))
                .build();

        IndexConfigSnapshot snapshot = elasticsearchKeywordResolver.resolve(documentVersion);

        assertThat(snapshot.enabled()).isFalse();
        assertThat(snapshot.vectorEnabled()).isFalse();
        assertThat(snapshot.keywordEnabled()).isTrue();
    }

    @Test
    void resolveShouldThrowServiceExceptionWhenJsonInvalid() {
        DocumentVersionDO documentVersion = DocumentVersionDO.builder()
                .documentVersionId(1L)
                .processConfigJson("{invalid")
                .build();

        assertThatThrownBy(() -> resolver.resolve(documentVersion))
                .isInstanceOf(ServiceException.class);
    }
}
