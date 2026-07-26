package com.nexarag.common.web;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 分页响应对象测试。
 */
class PageVOTest {

    @Test
    void shouldStorePageMetadataAndRecords() {
        PageVO<String> page = new PageVO<>();
        page.setRecords(List.of("a"));
        page.setTotal(1L);
        page.setCurrent(1L);
        page.setSize(20L);
        page.setPages(1L);

        assertThat(page.getRecords()).containsExactly("a");
        assertThat(page.getTotal()).isEqualTo(1L);
        assertThat(page.getCurrent()).isEqualTo(1L);
        assertThat(page.getSize()).isEqualTo(20L);
        assertThat(page.getPages()).isEqualTo(1L);
    }

    @Test
    void shouldStoreCursorMetadataAndRecords() {
        CursorPageVO<String> page = new CursorPageVO<>(List.of("a"), true, 10L);

        assertThat(page.getRecords()).containsExactly("a");
        assertThat(page.isHasMore()).isTrue();
        assertThat(page.getNextBeforeSequence()).isEqualTo(10L);
    }
}
