package com.nexarag.chat.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 引用标题路径解析器测试。
 */
class ChatCitationHeadingPathResolverTest {

    private final ChatCitationHeadingPathResolver resolver = new ChatCitationHeadingPathResolver();

    @Test
    void resolveShouldReadTitlePathFromChunkMetadata() {
        assertThat(resolver.resolve("{\"titlePath\":[\"二、定位与边界\",\"2.1 范围\"]}"))
                .containsExactly("二、定位与边界", "2.1 范围");
    }

    @Test
    void resolveShouldReturnEmptyPathForInvalidMetadata() {
        assertThat(resolver.resolve("not-json")).isEmpty();
    }
}
