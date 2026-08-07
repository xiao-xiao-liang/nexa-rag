package com.nexarag.retrieval.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexarag.document.model.entity.DocumentSectionDO;
import com.nexarag.document.mapper.DocumentSectionMapper;
import com.nexarag.retrieval.config.RetrievalProperties;
import com.nexarag.retrieval.dto.req.KeywordIndexWriteRequest;
import com.nexarag.retrieval.index.keyword.KeywordIndexClient;
import com.nexarag.retrieval.model.KeywordIndexDocument;
import com.nexarag.retrieval.model.KeywordIndexSearchResult;
import com.nexarag.retrieval.model.KeywordIndexWriteResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 章节导航索引仓储测试，验证标题路径与正文片段索引的隔离。
 */
class SectionNavigationIndexRepositoryImplTest {

    @Test
    void upsertShouldWriteTitlePathToDedicatedNavigationIndexOnly() {
        DocumentSectionMapper sectionMapper = mock(DocumentSectionMapper.class);
        KeywordIndexClient keywordIndexClient = mock(KeywordIndexClient.class);
        when(sectionMapper.selectList(any())).thenReturn(List.of(DocumentSectionDO.builder()
                .sectionId(11L)
                .documentId(1L)
                .parentSectionId(null)
                .title("退款规则")
                .headingPathJson("[\"帮助中心\",\"退款规则\"]")
                .headingLevel(2)
                .startLine(3)
                .build()));
        when(keywordIndexClient.upsert(any())).thenReturn(List.of(
                new KeywordIndexWriteResult("section-11", "navigation-11", true, null)));
        SectionNavigationIndexRepository repository = repository(sectionMapper, keywordIndexClient);

        repository.upsert(1L);

        ArgumentCaptor<KeywordIndexWriteRequest> captor = ArgumentCaptor.forClass(KeywordIndexWriteRequest.class);
        verify(keywordIndexClient).upsert(captor.capture());
        KeywordIndexWriteRequest request = captor.getValue();
        KeywordIndexDocument document = request.documents().getFirst();
        assertThat(request.indexName()).isEqualTo("nexa_document_section_navigation");
        assertThat(document.chunkId()).isEqualTo("section-11");
        assertThat(document.sectionId()).isEqualTo(11L);
        assertThat(document.text()).isEqualTo("退款规则");
        assertThat(document.indexContent()).isEqualTo("退款规则\n帮助中心 > 退款规则");
        verify(keywordIndexClient).deleteByDocumentId(1L, "nexa_document_section_navigation");
    }

    @Test
    void searchAndDeleteShouldUseDedicatedNavigationIndex() {
        DocumentSectionMapper sectionMapper = mock(DocumentSectionMapper.class);
        KeywordIndexClient keywordIndexClient = mock(KeywordIndexClient.class);
        when(keywordIndexClient.search(any())).thenReturn(List.of(new KeywordIndexSearchResult("section-11", 1L,
                null, 2, 11L, "退款规则", null, 8.5D)));
        when(keywordIndexClient.deleteByDocumentId(1L, "nexa_document_section_navigation")).thenReturn(1);
        SectionNavigationIndexRepository repository = repository(sectionMapper, keywordIndexClient);

        assertThat(repository.search("退款", 5)).containsExactly(new com.nexarag.retrieval.model.SectionNavigationHit(
                11L, 1L, 8.5D, "KEYWORD"));
        assertThat(repository.deleteByDocumentId(1L)).isEqualTo(1);
        verify(keywordIndexClient).deleteByDocumentId(1L, "nexa_document_section_navigation");
    }

    private SectionNavigationIndexRepository repository(DocumentSectionMapper sectionMapper,
                                                         KeywordIndexClient keywordIndexClient) {
        return new SectionNavigationIndexRepositoryImpl(sectionMapper, keywordIndexClient,
                new RetrievalProperties(), new ObjectMapper());
    }
}
