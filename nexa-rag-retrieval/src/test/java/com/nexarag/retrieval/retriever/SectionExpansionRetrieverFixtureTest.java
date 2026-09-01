package com.nexarag.retrieval.retriever;

import com.nexarag.retrieval.config.RetrievalProperties;
import com.nexarag.retrieval.model.RetrievalChunk;
import com.nexarag.retrieval.model.SectionContentChunk;
import com.nexarag.retrieval.model.SectionNavigationHit;
import com.nexarag.retrieval.repository.SectionContentRepository;
import com.nexarag.retrieval.repository.SectionNavigationIndexRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 章节扩展回归夹具，覆盖“微调的目录有什么规范”由导航范围定位、再读取原始正文的链路。
 */
class SectionExpansionRetrieverFixtureTest {

    @Test
    void retrieveShouldUseNavigationOnlyAsScopeForFineTuningDirectoryQuestion() {
        String question = "微调的目录有什么规范";
        SectionNavigationIndexRepository navigationRepository = mock(SectionNavigationIndexRepository.class);
        SectionContentRepository contentRepository = mock(SectionContentRepository.class);
        RetrievalProperties properties = new RetrievalProperties();
        properties.getCandidate().setExpansionCandidateLimit(5);
        properties.getCandidate().setExpansionEvidenceLimit(3);
        when(navigationRepository.search(question, 5, Set.of(2001L))).thenReturn(List.of(
                new SectionNavigationHit(101L, 1001L, 2001L, 0.82D, "KEYWORD")));
        when(contentRepository.listBySectionScope(1001L, 2001L, 101L, 3)).thenReturn(List.of(
                new SectionContentChunk("chunk-1", 1001L, 2001L, 101L, "微调目录必须包含配置、数据和输出目录。", 12),
                new SectionContentChunk("chunk-2", 1001L, 2001L, 102L, "输出目录不得与训练数据目录重叠。", 10)));
        SectionExpansionRetriever retriever = new SectionExpansionRetriever(navigationRepository, contentRepository,
                properties);

        List<RetrievalChunk> result = retriever.retrieve(question, Set.of(2001L));

        assertThat(result).extracting(RetrievalChunk::chunkId).containsExactly("chunk-1", "chunk-2");
        assertThat(result).allMatch(chunk -> SectionExpansionRetriever.CHANNEL.equals(chunk.channel()));
        assertThat(result).allMatch(chunk -> Long.valueOf(2001L).equals(chunk.documentVersionId()));
        assertThat(result).allMatch(chunk -> !chunk.content().contains("目录规范"));
        verify(contentRepository).listBySectionScope(1001L, 2001L, 101L, 3);
    }

    @Test
    void retrieveShouldRejectNavigationHitsOutsideActiveVersionScope() {
        SectionNavigationIndexRepository navigationRepository = mock(SectionNavigationIndexRepository.class);
        SectionContentRepository contentRepository = mock(SectionContentRepository.class);
        RetrievalProperties properties = new RetrievalProperties();
        when(navigationRepository.search("退款规则", properties.getCandidate().getExpansionCandidateLimit(), Set.of(2001L)))
                .thenReturn(List.of(new SectionNavigationHit(101L, 1001L, 2002L, 0.82D, "KEYWORD")));
        SectionExpansionRetriever retriever = new SectionExpansionRetriever(navigationRepository, contentRepository, properties);

        assertThat(retriever.retrieve("退款规则", Set.of(2001L))).isEmpty();
    }
}
