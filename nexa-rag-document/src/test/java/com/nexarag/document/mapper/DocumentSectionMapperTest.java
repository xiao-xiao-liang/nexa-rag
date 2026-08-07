package com.nexarag.document.mapper;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文档章节 Mapper 默认遍历方法测试。
 */
class DocumentSectionMapperTest {

    @Test
    void selectDescendantSectionIdsShouldReturnEmptyWhenRootSectionIdIsNull() {
        DocumentSectionMapper mapper = mock(DocumentSectionMapper.class, CALLS_REAL_METHODS);

        assertThat(mapper.selectDescendantSectionIds(1L, null)).isEmpty();
        verify(mapper, never()).selectActiveChildSectionIds(any(), anyList());
    }

    @Test
    void selectDescendantSectionIdsShouldExcludeRootWhenHierarchyContainsCycle() {
        DocumentSectionMapper mapper = mock(DocumentSectionMapper.class, CALLS_REAL_METHODS);
        when(mapper.selectActiveChildSectionIds(1L, List.of(10L))).thenReturn(List.of(20L, 30L));
        when(mapper.selectActiveChildSectionIds(1L, List.of(20L, 30L))).thenReturn(Arrays.asList(null, 10L));

        assertThat(mapper.selectDescendantSectionIds(1L, 10L)).containsExactly(20L, 30L);
        verify(mapper).selectActiveChildSectionIds(1L, List.of(20L, 30L));
    }

    @Test
    void mapperShouldExposeBatchChildSectionQuery() {
        assertThat(DocumentSectionMapper.class.getDeclaredMethods())
                .extracting(method -> method.getName())
                .contains("selectActiveChildSectionIds");
    }

    @Test
    void selectDescendantSectionIdsShouldKeepDocumentIdWhenQueryingEachLevel() {
        DocumentSectionMapper mapper = mock(DocumentSectionMapper.class, CALLS_REAL_METHODS);
        when(mapper.selectActiveChildSectionIds(1L, List.of(10L))).thenReturn(List.of(20L));
        when(mapper.selectActiveChildSectionIds(1L, List.of(20L))).thenReturn(List.of());

        assertThat(mapper.selectDescendantSectionIds(1L, 10L)).containsExactly(20L);
        verify(mapper).selectActiveChildSectionIds(1L, List.of(10L));
        verify(mapper).selectActiveChildSectionIds(1L, List.of(20L));
        verify(mapper, never()).selectActiveChildSectionIds(2L, List.of(10L));
    }
}
