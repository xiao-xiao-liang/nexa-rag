package com.nexarag.document.splitter;

import java.util.List;

/**
 * 待保存的文档章节草稿，用于在切分阶段传递章节层级和行号范围。
 *
 * @param sectionId       章节ID
 * @param parentSectionId 父章节ID
 * @param title           章节标题
 * @param headingPath     标题层级路径
 * @param headingLevel    标题层级
 * @param startLine       起始行号
 * @param endLine         结束行号
 */
public record DocumentSectionDraft(Long sectionId,
                                   Long parentSectionId,
                                   String title,
                                   List<String> headingPath,
                                   int headingLevel,
                                   int startLine,
                                   int endLine) {

    public DocumentSectionDraft {
        headingPath = headingPath == null ? List.of() : List.copyOf(headingPath);
    }
}
