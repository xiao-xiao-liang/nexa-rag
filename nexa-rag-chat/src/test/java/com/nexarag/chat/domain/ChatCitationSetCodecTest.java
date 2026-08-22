package com.nexarag.chat.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 引用清单 JSON 编解码测试。
 */
class ChatCitationSetCodecTest {

    private final ChatCitationSetCodec codec = new ChatCitationSetCodec();

    @Test
    void shouldReturnEmptySetForBlankAndUnknownVersion() {
        assertThat(codec.decode(" ").citations()).isEmpty();
        assertThat(codec.decode("{\"version\":2,\"citations\":[]}").citations()).isEmpty();
    }

    @Test
    void shouldRoundTripCurrentVersionWithoutEvidenceBody() {
        ChatCitationSetDTO source = new ChatCitationSetDTO(1, java.util.List.of(
                new ChatCitationDTO(1, 20L, "chunk-1", 3, "制度", null, 1, 0.9D, "hybrid")));

        String json = codec.encode(source);

        assertThat(json).doesNotContain("content", "text", "excerpt", "sourceUrl", "knowledgeBaseId");
        assertThat(codec.decode(json)).isEqualTo(source);
    }
}
