package com.nexarag.workflow.citation;

import com.nexarag.chat.domain.ChatCitationDTO;
import com.nexarag.retrieval.model.RetrievalChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 已接纳证据到引用清单的映射测试。
 */
class CitationSetFactoryTest {

    private final CitationSetFactory factory = new CitationSetFactory();

    @Test
    void shouldAssignCitationIdsInAcceptedEvidenceOrder() {
        List<ChatCitationDTO> citations = factory.create(List.of(
                new RetrievalChunk("chunk-2", 20L, 2, null, "制度 B", "file", "正文 B", 0.8D, "hybrid", 2),
                new RetrievalChunk("chunk-1", 10L, 1, null, "制度 A", "file", "正文 A", 0.9D, "hybrid", 1)));

        assertThat(citations).extracting(ChatCitationDTO::citationId).containsExactly(1, 2);
        assertThat(citations).extracting(ChatCitationDTO::chunkId).containsExactly("chunk-2", "chunk-1");
    }
}
