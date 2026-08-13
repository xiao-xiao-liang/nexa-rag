package com.nexarag.document.splitter.structure;

import com.nexarag.document.enums.HeadingEvidenceSource;
import com.nexarag.document.toolkit.extractor.MinerUContentListHeadingEvidenceExtractor;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** 官方 MinerU Content List 标题证据提取器测试。 */
class MinerUContentListHeadingEvidenceExtractorTest {

    @Test
    void extractV2ShouldInferRelativeLevelsFromTitleBoundingBoxHeights() throws Exception {
        String contentListV2 = """
                [[
                  {"type":"title","content":{"title_content":[{"type":"text","content":"Java集合"}],"level":2},"bbox":[0,0,100,34]},
                  {"type":"title","content":{"title_content":[{"type":"text","content":"List"}],"level":2},"bbox":[0,0,100,27]},
                  {"type":"title","content":{"title_content":[{"type":"text","content":"1. ArrayList"}],"level":2},"bbox":[0,0,100,20]},
                  {"type":"title","content":{"title_content":[{"type":"text","content":"• 非章节项目"}],"level":2},"bbox":[0,0,100,19]}
                ]]
                """;

        assertThat(new MinerUContentListHeadingEvidenceExtractor().extractV2(
                new ByteArrayInputStream(contentListV2.getBytes(StandardCharsets.UTF_8))))
                .extracting(evidence -> evidence.title(), evidence -> evidence.declaredLevel(),
                        evidence -> evidence.source(), evidence -> evidence.pageNumber())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Java集合", 1, HeadingEvidenceSource.PDF_LAYOUT, 1),
                        org.assertj.core.groups.Tuple.tuple("List", 2, HeadingEvidenceSource.PDF_LAYOUT, 1),
                        org.assertj.core.groups.Tuple.tuple("1. ArrayList", 3, HeadingEvidenceSource.PDF_LAYOUT, 1));
    }

    @Test
    void extractLegacyShouldUseTextLevelBlocksAsTitleCandidates() throws Exception {
        String contentList = """
                [
                  {"type":"text","text":"Map","text_level":2,"bbox":[0,0,100,28],"page_idx":0},
                  {"type":"text","text":"HashMap原理","text_level":2,"bbox":[0,0,100,24],"page_idx":0},
                  {"type":"text","text":"正文","bbox":[0,0,100,20],"page_idx":0}
                ]
                """;

        assertThat(new MinerUContentListHeadingEvidenceExtractor().extractLegacy(
                new ByteArrayInputStream(contentList.getBytes(StandardCharsets.UTF_8))))
                .extracting(evidence -> evidence.title(), evidence -> evidence.declaredLevel(),
                        evidence -> evidence.source(), evidence -> evidence.pageNumber())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Map", 1, HeadingEvidenceSource.PDF_LAYOUT, 1),
                        org.assertj.core.groups.Tuple.tuple("HashMap原理", 2,
                                HeadingEvidenceSource.PDF_LAYOUT, 1));
    }

    @Test
    void extractV2ShouldKeepConsecutiveNumberedChildrenAtTheSameLevelWhenBboxHeightsVary() throws Exception {
        String contentListV2 = """
                [[
                  {"type":"title","content":{"title_content":[{"type":"text","content":"Java集合"}]},"bbox":[0,0,100,34]},
                  {"type":"title","content":{"title_content":[{"type":"text","content":"List"}]},"bbox":[0,0,100,27]},
                  {"type":"title","content":{"title_content":[{"type":"text","content":"5. 实现线程安全的List"}]},"bbox":[0,0,100,24]},
                  {"type":"title","content":{"title_content":[{"type":"text","content":"1. Vector"}]},"bbox":[0,0,100,17]},
                  {"type":"title","content":{"title_content":[{"type":"text","content":"2. Collections.synchronizedList"}]},"bbox":[0,0,100,19]},
                  {"type":"title","content":{"title_content":[{"type":"text","content":"辅助标题"}]},"bbox":[0,0,100,20]}
                ]]
                """;

        assertThat(new MinerUContentListHeadingEvidenceExtractor().extractV2(
                new ByteArrayInputStream(contentListV2.getBytes(StandardCharsets.UTF_8))))
                .extracting(evidence -> evidence.title(), evidence -> evidence.declaredLevel())
                .containsSubsequence(
                        org.assertj.core.groups.Tuple.tuple("1. Vector", 5),
                        org.assertj.core.groups.Tuple.tuple("2. Collections.synchronizedList", 5));
    }
}
