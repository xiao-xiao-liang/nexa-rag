package com.nexarag.document.model.bo.structure;

import java.util.List;

/** 一次切分可使用的文档结构解析结果。 */
public record DocumentStructureBO(List<ResolvedHeadingBO> headings, List<String> diagnostics) {
    public static DocumentStructureBO empty() {
        return new DocumentStructureBO(List.of(), List.of());
    }
}
