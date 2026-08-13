package com.nexarag.document.model.bo.structure;

import com.nexarag.document.enums.HeadingEvidenceSource;

/** 已定位到 Markdown 行的可信标题。 */
public record ResolvedHeadingBO(String title, int level, int lineNumber, Integer originalPageNumber,
                                HeadingEvidenceSource source, double confidence) {
}
