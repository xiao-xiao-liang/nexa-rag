package com.nexarag.document.model.bo.structure;

import com.nexarag.document.enums.HeadingEvidenceSource;

/** 用于恢复标题层级的原始证据。 */
public record HeadingEvidenceBO(String title, int declaredLevel, int sequence, HeadingEvidenceSource source,
                                double confidence, Integer pageNumber) {
}
