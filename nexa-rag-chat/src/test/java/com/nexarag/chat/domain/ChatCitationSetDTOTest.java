package com.nexarag.chat.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 引用清单数据契约测试。
 */
class ChatCitationSetDTOTest {

    @Test
    void shouldKeepCitationIdentityAndNotContainEvidenceText() {
        ChatCitationDTO citation = new ChatCitationDTO(1, 20L, "chunk-1", 3,
                "费用报销管理制度", null, 1, 0.92D, "hybrid");
        ChatCitationSetDTO citationSet = new ChatCitationSetDTO(1, List.of(citation));

        assertThat(citationSet.version()).isEqualTo(1);
        assertThat(citationSet.citations()).containsExactly(citation);
        assertThat(ChatCitationDTO.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("content", "text", "excerpt", "originalFileUrl", "sourceUrl", "knowledgeBaseId");
    }
}
