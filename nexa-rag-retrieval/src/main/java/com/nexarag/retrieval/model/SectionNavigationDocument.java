package com.nexarag.retrieval.model;

/**
 * 章节导航索引文档，仅保存标题层级信息，不能作为回答证据。
 *
 * @param sectionId       章节ID
 * @param documentId      文档ID
 * @param parentSectionId 父章节ID
 * @param title           章节标题
 * @param headingPath     章节标题路径
 * @param headingLevel    标题层级
 */
public record SectionNavigationDocument(Long sectionId,
                                        Long documentId,
                                        Long parentSectionId,
                                        String title,
                                        String headingPath,
                                        Integer headingLevel) {

    /**
     * 构造导航检索内容，限定为标题和层级路径。
     *
     * @return 用于导航索引的文本
     */
    public String indexContent() {
        return safeText(title) + "\n" + safeText(headingPath);
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }
}
