package com.nexarag.document.toolkit.refiner;

import com.nexarag.document.model.bo.structure.HeadingEvidenceBO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 标题层级精修的默认实现，不发起外部调用。
 */
@Component
@ConditionalOnProperty(prefix = "nexa.parser.artifact.structure.llm-fallback", name = "enabled",
        havingValue = "false", matchIfMissing = true)
public class PassthroughHeadingHierarchyRefiner implements HeadingHierarchyRefiner {

    /** {@inheritDoc} */
    @Override
    public List<HeadingEvidenceBO> refine(Long documentId, List<HeadingEvidenceBO> headings) {
        return headings;
    }
}
