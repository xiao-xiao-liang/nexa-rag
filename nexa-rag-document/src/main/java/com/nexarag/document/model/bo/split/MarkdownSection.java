package com.nexarag.document.model.bo.split;

import java.util.List;

/**
 * Markdown 标题章节，包含标题树关系、章节范围和直属正文范围。
 *
 * @param sectionId       章节ID
 * @param parentSectionId 父章节ID
 * @param level           标题层级
 * @param title           当前标题
 * @param headingPath     标题路径
 * @param startLine       章节起始行号
 * @param endLine         章节结束行号
 * @param bodyStartLine   直属正文起始行号
 * @param bodyEndLine     直属正文结束行号
 * @param bodyText        直属正文内容
 */
public record MarkdownSection(Long sectionId,
                              Long parentSectionId,
                              int level,
                              String title,
                              List<String> headingPath,
                              int startLine,
                              int endLine,
                              int bodyStartLine,
                              int bodyEndLine,
                              String bodyText) {

    /**
     * 兼容既有元数据读取方式。
     *
     * @return 标题路径
     */
    public List<String> titlePath() {
        return headingPath;
    }

    /**
     * 兼容既有正文读取方式。
     *
     * @return 直属正文
     */
    public String text() {
        return bodyText;
    }
}
